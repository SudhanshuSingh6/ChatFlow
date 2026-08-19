# ChatFlow — Architecture

ChatFlow is a real-time chat backend built as a **multi-module Maven reactor** that runs as a small
set of **microservices**, extracted from an original modular monolith via a strangler-fig migration
(history in `docs/microservices-migration.md`). Core chat, AI, media processing, the realtime
WebSocket edge, and the API gateway are independent deployables that communicate through a **Kafka
event backbone**, a **Redis delivery bus**, and a few **synchronous internal REST** calls.

```
                                   ┌─────────────────────────────┐
                client (web/app) ─►│  chatflow-gateway  :8088     │  API gateway (Spring Cloud Gateway, webmvc)
                                   │  edge JWT validation + routing│
                                   └───────┬───────────────┬──────┘
                                  /api/**  │               │ /ai/**
                                           ▼               ▼
                            ┌───────────────────┐   ┌──────────────────┐
        WebSocket  ────────►│  chatflow-core    │   │  chatflow-ai     │
        /ws (:8083)         │  :8080            │   │  :8081           │
   ┌────────────────┐  REST │  chat · identity  │◄─►│  embeddings·RAG  │
   │ chatflow-      │ /internal│ friends · notifs │sync│ summary·search  │
   │ realtime :8083 │◄───────│  media metadata   │   │  chat-completion │
   │ sessions+relay │ chat:relay (Redis) ◄───────┤   └──────┬───────────┘
   └────────────────┘        └───┬───────────┬───┘          │ embeds (Kafka)
                                 │ outbox     │ media.proc   ▼
                                 ▼ (Kafka)    ▼          pgvector (postgres-ai :5433)
                         chatflow.outbox.events   ┌──────────────────┐
                                 │                │ chatflow-media   │  thumbnail worker (FFmpeg)
                                 └───────────────►│  :8082 (stateless)│  reads/writes MinIO
                                                  └──────────────────┘
        Postgres :5432 (core)   Redis :6379   Kafka :9092   MinIO :9000   Jaeger :16686
        Prometheus :9090        Grafana :3000
```

---

## 1. Services (Maven modules)

| Module | Port | Stateful? | Owns / does |
|--------|------|-----------|-------------|
| **chatflow-contracts** | — | — | Dependency-free shared **event + DTO records** (the wire contracts): `MessageEmbeddingRequested`, `MediaProcessingRequested`, `MediaThumbnailReady`, `ConversationDeleted`, `EmbeddingSearchRequest/Hit`, `ConversationTranscript`. |
| **chatflow-core** | 8080 | Postgres `chatflow` | The hub: auth/JWT, users, conversations (1:1 + group unified), messages + delivery/read receipts + replay, friends, notifications, **media metadata** (`media_messages`), keyword + hybrid **search orchestration**, the transactional **outbox**. Embedded WebSocket handler in fallback mode. |
| **chatflow-ai** | 8081 | Postgres `chatflow_ai` + pgvector | All AI/LLM: message **embeddings** (OpenAI-compatible) into a denormalized pgvector store, **RAG** (`/ai/.../ask`), **summary** (`/ai/.../summary`), **chat completion** (Anthropic), and **vector search** for core's hybrid search. |
| **chatflow-media** | 8082 | stateless | CPU-heavy **thumbnailing** (Thumbnailator + FFmpeg). Consumes media events, reads the original from the shared object store, writes the thumbnail back. |
| **chatflow-gateway** | 8088 | stateless | **API gateway** (Spring Cloud Gateway webmvc). Single public entry **for HTTP**; validates the JWT at the edge and routes `/api/**`→core, `/ai/**`→ai. The servlet variant doesn't proxy WS upgrades — clients reach `/ws` directly on `:8083` (see below). |
| **chatflow-realtime** | 8083 | in-memory sessions | The **WebSocket edge**: terminates `/ws` (clients connect **directly**, not via the gateway), holds the session registry, delivers frames off the Redis relay, forwards inbound commands to core. Business logic stays in core. |

Infra (docker-compose): Postgres ×2 (pgvector image), Redis, Kafka (KRaft), MinIO, Jaeger,
Prometheus, Grafana, kafka-ui.

---

## 2. How everything connects

### Async — Kafka event backbone (the outbox)
Core writes domain events to an `outbox_events` table **in the same transaction** as the state
change (`OutboxWriter`), then `OutboxPoller` drains them through an `OutboxEventPublisher`:
- `app.outbox.transport=in-process` (default/fallback): dispatched in-JVM.
- `app.outbox.transport=kafka` (distributed mode): published to the shared topic
  **`chatflow.outbox.events`** (keyed by aggregate id).

Consumers (own group each, idempotent — see below):
- **core** `OutboxConsumer` → notifications (`message.created`, `friend.*`, `group.*`).
- **ai** `EmbeddingEventConsumer` → `message.embedding_requested` (embed) and `conversation.deleted` (evict).
- **media** `MediaProcessingConsumer` → `media.processing_requested` (thumbnail), then publishes
  `MediaThumbnailReady` to the dedicated topic **`chatflow.media.thumbnail-ready`**, which core consumes.

### Async — Redis delivery bus (`chat:relay`)
Outbound WebSocket frames. Core's `WebSocketGateway` publishes `{targetUserId, payload}` to the
Redis `chat:relay` channel. In **external** realtime mode, `chatflow-realtime` subscribes and writes
the frame JSON verbatim to the user's sockets (frames are opaque — no shared frame class).

### Sync — internal REST (`/internal/**`, shared `X-Internal-Token`)
Used where data must be correct-now, not eventual; each call is wrapped in **Resilience4j** circuit
breaker + timeout:
- **ai → core**: membership check (`/internal/conversations/{id}/participants/{uid}`) and unread
  transcript (`/internal/conversations/{id}/transcript/unread`).
- **core → ai**: vector search (`/internal/embeddings/search`) for hybrid search.
- **realtime → core**: `/internal/realtime/{connect,disconnect,inbound}`.

### Cross-cutting
- **Auth**: HS256 JWT, one shared secret. Gateway validates at the edge; each service also verifies
  (defense in depth). WS handshake takes the token as `?token=`.
- **Idempotency**: Kafka is at-least-once, so consumers dedupe on the stable outbox event id via a
  `processed_events` table + `IdempotencyGuard` (core: atomic claim; ai: process-then-mark).
- **Tracing**: Micrometer → OpenTelemetry → OTLP → **Jaeger**; context propagates over HTTP and as
  Kafka headers, so a trace spans gateway → core → Kafka → ai/media → relay → realtime.
- **Metrics**: Micrometer → `/actuator/prometheus` → **Prometheus** → **Grafana** (provisioned
  "ChatFlow Realtime" dashboard with `realtime.active.sessions`, `realtime.connected.users`,
  `realtime.frames.sent/received`, `realtime.relay.messages`).

---

## 3. Data ownership (database-per-service)

| Store | Owner | Holds |
|-------|-------|-------|
| Postgres `chatflow` (:5432) | core | users, conversations, conversation_participants, messages, friendships, notifications, media_messages, outbox_events, processed_events (Flyway V1–V5) |
| Postgres `chatflow_ai` (:5433) + pgvector | ai | message_embeddings (denormalized: snippet + sender + sequence + vector), processed_events (Flyway V1–V2) |
| MinIO bucket `chatflow` | shared | media originals (written by core) + thumbnails (written by media) |
| Redis | shared | `chat:relay` delivery bus; (presence is in-core in-memory) |

No service reads another's tables. Cross-service data moves via **event-carried state transfer**
(e.g. the embedding event carries the message content + sender) or a **sync internal call**.

---

## 4. Key flows

- **Send a message** (REST `/api/conversations/{id}/messages`, or WS `SEND_MESSAGE`): core locks the
  conversation, allocates the per-conversation sequence, persists the message, bumps unread, writes
  outbox events (notification + `message.embedding_requested`), and after commit publishes the
  `MESSAGE`/`MESSAGE_ACK` frames to `chat:relay`.
- **Realtime delivery** (external mode): `chat:relay` → realtime → socket. Offline users get
  `ReplayService` on reconnect (realtime reports connect → core replays).
- **RAG** (`POST /ai/conversations/{id}/ask`): ai checks membership (sync→core), embeds the question,
  vector-searches its own store, calls the LLM, returns a grounded answer + citations.
- **Summary** (`GET /ai/conversations/{id}/summary`): ai checks membership, fetches the unread
  transcript (sync→core, since it needs the full chronological backlog), summarizes via the LLM.
- **Hybrid search** (`GET /api/messages/search/hybrid`): core runs keyword search locally + calls ai
  for vector hits, merges with Reciprocal Rank Fusion.
- **Media upload** (`POST /api/messages/media`): core creates the parent `messages` row + `media_messages`
  detail + stores the original, emits `media.processing_requested`. The media worker thumbnails and
  emits `media.thumbnail_ready`; core saves the URL and pushes `MEDIA_THUMBNAIL_READY`.

---

## 5. Running it

Prereqs: Java 21, Docker. Build everything: `./mvnw package`.

### Full stack (recommended) — one command
```bash
docker compose --profile apps up --build
```
Brings up infra **and** all five services in distributed mode (core runs with
`APP_OUTBOX_TRANSPORT=kafka` + `APP_REALTIME_MODE=external`; MinIO bucket auto-created). Entry point
is the gateway. Plain `docker compose up` (no profile) starts infra only.

| URL | What |
|-----|------|
| http://localhost:8088 | API gateway (`/api/**`, `/ai/**`) — use this |
| ws://localhost:8083/ws?token=JWT | realtime WebSocket |
| http://localhost:3000 | Grafana (anonymous; "ChatFlow Realtime" dashboard) |
| http://localhost:16686 | Jaeger traces |
| http://localhost:9090 | Prometheus |
| http://localhost:8090 | kafka-ui |
| http://localhost:9001 | MinIO console (minioadmin/minioadmin) |

Secrets: copy `.env.example` → `.env` and set `JWT_SECRET`, `INTERNAL_TOKEN`, `AI_EMBEDDING_API_KEY`,
`ANTHROPIC_API_KEY` (AI features are no-ops without the keys).

### Run one service in dev (against infra)
```bash
docker compose up -d postgres postgres-ai redis kafka minio jaeger     # infra only
AI_EMBEDDING_API_KEY=… ANTHROPIC_API_KEY=… ./mvnw -pl chatflow-ai spring-boot:run
APP_OUTBOX_TRANSPORT=kafka APP_REALTIME_MODE=external ./mvnw -pl chatflow-core spring-boot:run
./mvnw -pl chatflow-realtime spring-boot:run
./mvnw -pl chatflow-media spring-boot:run
./mvnw -pl chatflow-gateway spring-boot:run     # needs no infra; boots standalone
```
Each exposes `/actuator/health`. The gateway is the only service that boots with no backing infra.

### Kubernetes (local)
`kubectl apply -k k8s/overlays/local` (kind/minikube). See `k8s/README.md` for building + loading
images, the Ingress, and the media HPA.

### Operating modes
- **Distributed** (compose/k8s defaults): `APP_OUTBOX_TRANSPORT=kafka` + `APP_REALTIME_MODE=external`
  — all services participate; AI/media run off Kafka; WS served by realtime.
- **Fallback / single-process** (Spring defaults: `in-process` + `embedded`): core serves `/ws`
  itself and dispatches outbox events in-JVM. AI/media features are degraded in this mode because
  their work arrives via Kafka — use it only to run core in isolation.

---

## 6. Tech stack
Java 21 · Spring Boot 4.0 · Spring Cloud 2025.1 (Gateway, CircuitBreaker/Resilience4j) ·
PostgreSQL + pgvector · Flyway · Redis · Apache Kafka (KRaft) · MinIO (S3) · Thumbnailator + FFmpeg ·
Anthropic + OpenAI-compatible embeddings · Micrometer + Prometheus + Grafana · OpenTelemetry + Jaeger ·
JWT (jjwt) · Maven multi-module · Docker Compose + Kubernetes (Kustomize).

> `docs/microservices-migration.md` records how this was reached (phase by phase). The module
> boundaries within core are still enforced by ArchUnit (`ModuleBoundaryTest`, 0 cycles).
> The older `docs/current-architecture.md` predates the migration and is superseded by this file.

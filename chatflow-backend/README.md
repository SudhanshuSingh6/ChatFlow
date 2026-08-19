# ChatFlow Backend

Java 21 / Spring Boot 4 multi-module Maven project. Provides the REST API, WebSocket layer, media pipeline, notifications, and AI features for the ChatFlow messaging platform.

---

## Module Map

```
chatflow-backend/
├── chatflow-contracts   # Shared Kafka event records and cross-service DTOs (no Spring)
├── chatflow-storage     # S3 / local storage abstraction (library, no Spring context)
├── chatflow-core        # Main service — REST API, WebSocket, outbox, notifications     :8080
├── chatflow-ai          # RAG / embeddings / conversation summary                       :8081
├── chatflow-media       # Kafka-driven thumbnail generation                             :8082
├── chatflow-realtime    # Dedicated WebSocket tier (optional external mode)             :8083
└── chatflow-gateway     # Spring Cloud Gateway — public entry point, JWT edge auth     :8088
```

`chatflow-contracts` and `chatflow-storage` are library-only modules (no `@SpringBootApplication`). Every runnable service depends on them via Maven; they never run standalone.

---

## Quick Start

```bash
# 1. Start all infrastructure (Postgres × 2, Redis, Kafka, MinIO, Jaeger, Grafana)
cd chatflow-backend
docker compose up -d

# 2. Build all modules and install to local Maven cache
./mvnw clean install -DskipTests

# 3. Run core locally (most common for dev)
./mvnw spring-boot:run -pl chatflow-core

# 4. Run all tests (requires Postgres container)
./mvnw test

# 5. Start everything as containers
docker compose --profile apps up --build
```

---

## Infrastructure Ports

| Service | URL | Credentials |
|---|---|---|
| PostgreSQL (core) | `localhost:5432` db `chatflow` | `chatflow / chatflow` |
| PostgreSQL (ai) | `localhost:5433` db `chatflow_ai` | `chatflow / chatflow` |
| Redis | `localhost:6379` | — |
| Kafka | `localhost:9092` | — |
| Kafka UI | `http://localhost:8090` | — |
| MinIO API | `http://localhost:9000` | `minioadmin / minioadmin` |
| MinIO Console | `http://localhost:9001` | `minioadmin / minioadmin` |
| Jaeger UI | `http://localhost:16686` | — |
| Prometheus | `http://localhost:9090` | — |
| Grafana | `http://localhost:3000` | anonymous admin |

---

## Service Responsibilities

### `chatflow-gateway` (:8088)

The only public-facing service. Built on Spring Cloud Gateway MVC.

- Routes `/api/**` → core (:8080) and `/ai/**` → ai-service (:8081).
- `EdgeAuthFilter` validates the JWT on every request except `/api/auth/**` and `/actuator/**`. A missing or invalid token gets a 401 before the request ever reaches a backend service.
- Does **not** parse claims or build a `SecurityContext` — it only validates the signature and expiry. Downstream services do their own full auth via `JwtAuthenticationFilter`.
- WebSocket (`/ws`) is **not** proxied through the gateway — clients connect directly to core (:8080) in embedded mode or to realtime (:8083) in external mode.

### `chatflow-core` (:8080)

The main service. All business logic lives here.

**Domains:**

| Package | Responsibility |
|---|---|
| `auth` | Registration, login, JWT minting (`JwtService`), `JwtAuthenticationFilter` |
| `user` | `UserController` — `GET /api/users/me`, `GET /api/users/search` |
| `conversation` | Unified DIRECT + GROUP conversation CRUD, message history, group membership, message search |
| `friend` | Friend requests, accept/decline, unfriend, friend list |
| `presence` | Online/offline state, conversation presence snapshots |
| `typing` | In-memory typing indicators per conversation |
| `notification` | Persistent in-app notifications, unread count, mark read/all |
| `media` | Upload, access control, URL signing, soft-delete cleanup |
| `realtime` | `ChatWebSocketHandler` (embedded mode) and `InternalRealtimeController` (external mode) |
| `infra.outbox` | Transactional outbox infrastructure (poller, dispatcher, publisher) |
| `infra.redis` | Cross-server WebSocket fanout via Redis Pub/Sub |
| `infra.websocket` | `WebSocketGateway`, session registry, frame types |

### `chatflow-ai` (:8081)

Owns the `chatflow_ai` database (Postgres with the `pgvector` extension). Never writes to core's database.

- **Embedding pipeline**: consumes `chatflow.outbox.events` with consumer group `chatflow-ai-embedding`, filters `MESSAGE_EMBEDDING_REQUESTED` events, calls an OpenAI-compatible embeddings API, and upserts into `message_embeddings` (1536-dim pgvector column).
- **Conversation summary** (`GET /ai/conversations/{id}/summary`): fetches the unread message backlog from core's `/internal` endpoint, builds a transcript, and asks Claude for a "catch me up" summary.
- **RAG ask** (`POST /ai/conversations/{id}/ask`): embeds the question, runs a pgvector `<=>` cosine similarity search over the conversation's embeddings, builds a grounded prompt with citations, and completes via `ChatCompletionService` (Anthropic Claude by default).
- `ConversationAccessClient` and `TranscriptClient` call core's `/internal` endpoints using `INTERNAL_TOKEN` — no user JWT is forwarded.

### `chatflow-media` (:8082)

Purely Kafka-driven; exposes no HTTP endpoints.

- Consumes `chatflow.outbox.events`, filters `MEDIA_PROCESSING_REQUESTED` events.
- `ThumbnailService` generates image thumbnails (resize to 320 px) and video thumbnails (ffmpeg first-frame extract).
- On completion publishes `MediaThumbnailReady` to `chatflow.media.thumbnail-ready`.
- Core's `MediaThumbnailReadyListener` (Kafka consumer) picks this up, persists the URL, and pushes a `MEDIA_THUMBNAIL_READY` WebSocket frame to all conversation participants.

### `chatflow-realtime` (:8083)

An optional dedicated WebSocket tier, activated by setting `APP_REALTIME_MODE=external` in core.

- Terminates WebSocket connections independently of core's HTTP thread pool.
- Handles `PING`/`PONG` and passes all other inbound frames to core via `CoreCommandClient` → `POST /internal/realtime/command`.
- Receives outbound frames from core via the same Redis `chat:relay` channel that embedded mode uses — so the fanout path is identical regardless of mode.
- `RealtimeSessionRegistry` + `RelaySubscriber` mirror core's session registry + `CrossServerRelay`.

### `chatflow-contracts`

Plain Java records (no Spring annotations). Kafka event payloads shared between services:

| Record | Topic direction |
|---|---|
| `MessageEmbeddingRequested` | core → ai (via outbox topic) |
| `MediaProcessingRequested` | core → media (via outbox topic) |
| `MediaThumbnailReady` | media → core (thumbnail-ready topic) |
| `ConversationDeleted` | core → ai (via outbox topic, triggers embedding eviction) |
| `ConversationTranscript` / `EmbeddingSearchRequest` / `EmbeddingSearchHit` | cross-service DTOs |

### `chatflow-storage`

Strategy pattern for media storage:

- `MediaStorageService` interface with `ReadableStorage`, `WritableStorage`, `UrlStorage` mixins.
- `LocalMediaStorageService` — writes files to disk, serves via `/media/**` (signed HMAC URLs).
- `S3MediaStorageService` — writes to MinIO/S3, serves pre-signed URLs (60-minute TTL by default).
- Activated by Spring profile: default = local, `SPRING_PROFILES_ACTIVE=s3` = S3.

---

## Database Schema

Core uses a single `chatflow` database. Schema is owned by Flyway; Hibernate runs in `validate` mode (it never auto-generates DDL).

### Key Tables

```
users                        — id, username, password, created_at
friendships                  — user_one_id, user_two_id, initiator_id, status
conversations                — id, type (DIRECT|GROUP), name, dm_key, last_message_*
conversation_participants    — conversation_id, user_id, role, last_read_seq, last_delivered_seq
messages                     — id, conversation_id, sender_id, type, content, sequence_number
media_messages               — id (= message.id), storage_key, thumbnail_url, mime_type, ...
outbox_events                — id, aggregate_type, event_type, payload (JSON), status
notifications                — id, recipient_id, actor_id, type, reference_type, reference_id
```

### Design Decisions

**Unified conversation model.** `DIRECT` and `GROUP` conversations are the same `Conversation` entity. The only difference is `type`. All message sending, receipts, presence, and group management go through the identical path — there is no separate `GroupService` or `GroupMessage` table.

**No per-message status flags.** Delivery and read state are two `bigint` watermark columns on `conversation_participants` (`last_delivered_seq`, `last_read_seq`). "Has user X read message N?" is answered by `participant.last_read_seq >= message.sequence_number`. This eliminates the classic fan-out write problem in group chats (one row updated per participant rather than one row per message × participant).

**`dm_key` uniqueness for direct chats.** A direct conversation is identified by a canonical `minId:maxId` key stored in `dm_key` with a unique constraint. Opening a DM is idempotent: re-calling `POST /api/conversations/direct` returns the existing conversation.

**`sequence_number` instead of timestamps for ordering.** A per-conversation sequence (`nextSequenceNumber()` uses `SELECT ... FOR UPDATE` on the conversation row) ensures strict ordering independent of clock skew. The infinite-scroll cursor is a sequence number, not a timestamp.

**Soft deletes.** Messages, group conversations, and notifications are soft-deleted by setting a `deleted_at` timestamp (or `deleted` boolean). A daily `DailyCleanupService` job physically purges rows past the retention window (30 days for messages and groups, configurable).

**AI database is separate.** `chatflow-ai` connects to `localhost:5433 / chatflow_ai` with the `pgvector` extension. The embedding table (`message_embeddings`) is a denormalized copy: it stores `content_snippet`, `sender_name`, and `sender_id` inline so RAG queries never join back to core's database.

### Migrations

| Version | Description |
|---|---|
| V1 | Full baseline schema (unified conversation model) |
| V2 | Soft-delete columns (`deleted_at`, `deleted`) |
| V3 | `message_embeddings` table (later dropped — moved to ai-service DB) |
| V4 | Drop `message_embeddings` from core |
| V5 | `processed_events` idempotency table for the outbox consumer |

The V1 migration is a clean rewrite. The dev database **must be fresh** (drop and recreate) when V1 changes.

---

## Key Flows

### Sending a Text Message

```
Client  ──SEND_MESSAGE──▶  ChatWebSocketHandler
                               │
                          RealtimeInboundService
                               │
                          ChatService.sendMessage()
                           ├─ INSERT message (with sequence_number)
                           ├─ UPDATE conversation.last_message_*
                           └─ INSERT outbox_events (NOTIFICATION_COMMAND + MESSAGE_EMBEDDING_REQUESTED)
                               │ (same transaction)
                               ▼
                          AfterCommit callback fires
                               ├─ WebSocketGateway.sendToUsers()   ← all participants
                               │   ├─ local delivery (WebSocketSessionRegistry)
                               │   └─ Redis publish (chat:relay)   ← other instances pick up
                               └─ OutboxPoller (1 s interval) → OutboxDispatcher
                                   ├─ NotificationOutboxHandler     ← fan-out notifications
                                   └─ KafkaOutboxPublisher          ← if transport=kafka
                                       └─ chatflow.outbox.events
                                           ├─ EmbeddingEventConsumer (ai-service)
                                           └─ MediaProcessingConsumer (media-service, filters by type)
```

The outbox guarantees at-least-once delivery: if the process crashes after the transaction commits but before the socket send, the poller retries. The WebSocket send is a best-effort push; the client always catches up via `GET /messages/after` on reconnect.

### Media Upload

```
Client  ──POST /api/messages/media (multipart)──▶  MediaController
                                                        │
                                                   MediaMessageService
                                                    ├─ Validate file (size, MIME type)
                                                    ├─ MediaStorageService.store()
                                                    │   ├─ local: write to ./uploads, return /media/{key}
                                                    │   └─ S3: upload to MinIO, return presigned URL
                                                    ├─ INSERT media_messages
                                                    ├─ INSERT messages (type=MEDIA)
                                                    └─ INSERT outbox_events (MEDIA_PROCESSING_REQUESTED)

        ◀── MediaMessageResponse ──
        (message visible immediately; thumbnail pending)

OutboxPoller → chatflow.outbox.events
    └─ MediaProcessingConsumer (chatflow-media)
        ├─ ThumbnailService.generate()  (resize image / ffmpeg first frame)
        └─ Publish MediaThumbnailReady → chatflow.media.thumbnail-ready

chatflow-core (MediaThumbnailReadyListener)
    ├─ UPDATE media_messages.thumbnail_url
    └─ WebSocketGateway.sendToUsers(MEDIA_THUMBNAIL_READY)
```

### WebSocket Fanout (Cross-Instance)

```
Instance A                          Redis (chat:relay)          Instance B
─────────────────────────────────   ──────────────────────────  ─────────────────────────────────
WebSocketGateway.sendToUser(uid, m)
  │
  ├─ sessionRegistry.sendToUser()   ←── local delivery if connected on A
  │
  └─ CrossServerRelay.publish()  ──▶  PUBLISH chat:relay  ──▶  CrossServerRelay.onMessage()
                                      {sourceInstanceId,             │
                                       targetUserId,                  ├─ skip if sourceInstanceId == self
                                       payload}                       └─ sessionRegistry.sendToUser() if connected on B
```

Every instance both publishes to and subscribes from `chat:relay`. The `sourceInstanceId` field prevents each instance from delivering its own messages twice.

### Notification Delivery

Notifications are created by `NotificationOutboxHandler` when the outbox event is processed. A single outbox entry can fan out to multiple recipients (e.g. all group members on a new message). The handler:

1. Builds `Notification` rows (one per recipient) and bulk-inserts them.
2. Calls `WebSocketGateway.sendToUsers(NOTIFICATION, ...)` for connected recipients.
3. Connected recipients get the badge count updated live. Disconnected recipients see it on next load via `GET /api/notifications/unread-count`.

---

## Authentication & Authorization

### JWT

All services share the same HMAC-SHA256 secret (`JWT_SECRET`). Tokens are minted by `chatflow-core` on login/register, validated edge-side by `chatflow-gateway`, and re-validated inside each service.

- Gateway: validates signature + expiry, rejects early. Does not build a Spring `SecurityContext`.
- Core / AI / Realtime: `JwtAuthenticationFilter` parses claims into a `UsernamePasswordAuthenticationToken`. `principal.getName()` in controllers is the user's UUID string.
- WebSocket: `JwtHandshakeInterceptor` extracts the token from the `Authorization` header during the HTTP upgrade handshake and builds the same principal.

### Internal Service-to-Service Auth

Service-to-service calls (ai → core, realtime → core) use a shared `INTERNAL_TOKEN` header. `SecretsGuard` in core validates it on `/internal/**` routes. Internal endpoints are blocked from public access — the gateway does not route `/internal/**`.

### Media URL Signing

Local media URLs (`/media/{key}`) are time-limited HMAC-SHA256 signed URLs. `MediaTokenFilter` validates the token before serving the file. A separate `MEDIA_SIGNING_SECRET` is used (different trust domain from JWT). On S3/MinIO, pre-signed URLs from the SDK serve the same purpose.

---

## Configuration Toggles

| Environment variable | Values | Effect |
|---|---|---|
| `APP_REALTIME_MODE` | `embedded` (default) / `external` | `embedded`: core serves `/ws` directly. `external`: `chatflow-realtime` serves WebSockets; core only handles `/internal/realtime/*` commands. |
| `APP_OUTBOX_TRANSPORT` | `in-process` (default) / `kafka` | `in-process`: outbox events dispatched in-JVM. `kafka`: published to `chatflow.outbox.events`; ai-service and media-service consume independently. |
| `SPRING_PROFILES_ACTIVE=s3` | — | Switches `MediaStorageService` bean from local disk to MinIO/S3. |
| `JWT_SECRET` | string (≥32 chars) | Shared across all services. Override in every environment. |
| `INTERNAL_TOKEN` | string | Shared service-to-service secret. Must match across core, ai, realtime. |
| `ANTHROPIC_API_KEY` | string | Required for ai-service chat completions (RAG + summary). |
| `AI_EMBEDDING_API_KEY` | string | OpenAI-compatible embedding API key. |

---

## Observability

All services export:

- **Prometheus metrics** at `/actuator/prometheus` — scraped by the docker-compose Prometheus instance.
- **OTLP traces** to Jaeger (`http://localhost:4318` by default). Sampling is 100% in dev; lower it in production via `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`.
- **Trace propagation across Kafka**: both producers and consumers have `observation-enabled: true` so Kafka hops appear in the same trace as the HTTP request that triggered them.
- **Correlation IDs**: `CorrelationIdFilter` in core reads or generates `X-Correlation-Id` and populates it in the MDC for log correlation.
- **Custom metrics** in core: `chatflow.messages.delivery.latency` histogram with p50/p95/p99 percentile buckets, and WebSocket connection gauges.

Grafana is pre-provisioned with the Prometheus datasource. Import the dashboard JSON from `ops/grafana/` or build your own against the `chatflow.*` metric namespace.

---

## Kubernetes

Manifests live in `k8s/`:

```
k8s/
├── base/
│   ├── core.yaml, ai.yaml, media.yaml, realtime.yaml, gateway.yaml
│   ├── hpa.yaml        — HorizontalPodAutoscaler for core and gateway
│   ├── secret.yaml     — placeholder for JWT_SECRET, INTERNAL_TOKEN, etc.
│   └── kustomization.yaml
└── overlays/local/
    ├── infra.yaml      — Postgres, Redis, Kafka, MinIO as in-cluster pods
    ├── bucket-init-job.yaml  — one-shot MinIO bucket creation
    └── kustomization.yaml
```

Apply locally with:

```bash
kubectl apply -k k8s/overlays/local
```

Each service reads its config from environment variables declared in the Deployment spec. Secrets (`JWT_SECRET`, `INTERNAL_TOKEN`, API keys) are expected in the `chatflow-secrets` Secret object (see `k8s/base/secret.yaml` for the expected keys).

---

## Package Conventions

- **Entity classes** live in `*.entity.*` — JPA `@Entity`, no business methods beyond simple predicates and lifecycle hooks (`@PrePersist`).
- **Service classes** in `*.service.*` — `@Transactional`, own the business logic. Never accessed directly from another service's package; always go through the same-module service boundary.
- **Controller classes** in `*.controller.*` — thin HTTP adapters. Extract the caller's UUID from `principal.getName()`, delegate entirely to the service.
- **Repository classes** in `*.repository.*` — Spring Data JPA interfaces. Complex queries use JPQL `@Query`; raw SQL is avoided except for the sequence-number increment.
- **`infra.*` packages** — cross-cutting plumbing (outbox, Redis relay, WebSocket registry, idempotency). Nothing in `infra.*` imports from feature packages; feature packages depend on `infra.*`.

---

## Resilience

`ResilienceConfig` (in core, ai-service, and realtime) registers [Resilience4j](https://resilience4j.readme.io/) beans:

- **Circuit breaker** wraps external HTTP calls (core → ai for embedding search; ai → core for membership checks; realtime → core for commands). Opens after 50% failure rate in a 10-call window.
- **Retry** (3 attempts, exponential backoff) sits outside the circuit breaker for transient failures.

Kafka consumer failures are logged and left as pending outbox events for the next poll cycle (at-least-once retry by design). Embedding ingest uses an `IdempotencyGuard` (`processed_events` table) so redelivered events don't duplicate embeddings.

---

## API Reference

All public endpoints go through the gateway at `http://localhost:8088`. Every endpoint except `POST /api/auth/register` and `POST /api/auth/login` requires `Authorization: Bearer <token>`.

Errors follow [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807): `Content-Type: application/problem+json`, fields `type`, `title`, `status`, `detail`.

---

### Authentication — `/api/auth`

#### `POST /api/auth/register`

Creates an account and returns a token. No auth header required.

**Request**
```json
{ "username": "alice", "password": "secret123" }
```

**Response `201`**
```json
{ "token": "<jwt>", "userId": "<uuid>", "username": "alice" }
```

---

#### `POST /api/auth/login`

**Request**
```json
{ "username": "alice", "password": "secret123" }
```

**Response `200`**
```json
{ "token": "<jwt>", "userId": "<uuid>", "username": "alice" }
```

---

### Users — `/api/users`

#### `GET /api/users/me`

Returns the caller's own profile.

**Response `200`**
```json
{ "id": "<uuid>", "username": "alice" }
```

---

#### `GET /api/users/search?q={query}&limit={n}`

People-picker search. Excludes the caller from results. Default limit 10.

**Response `200`**
```json
[
  { "id": "<uuid>", "username": "bob" }
]
```

---

### Conversations — `/api/conversations`

Conversations are unified: both DIRECT (1:1) and GROUP chats use the same resource.

#### `POST /api/conversations/direct`

Opens or returns the existing 1:1 conversation with `userId`. Idempotent.

**Request**
```json
{ "userId": "<uuid>" }
```

**Response `200`** — `ConversationResponse` (see shape below)

---

#### `POST /api/conversations/group`

Creates a new group conversation. The caller is automatically added as `OWNER`.

**Request**
```json
{ "name": "Team Alpha", "memberIds": ["<uuid>", "<uuid>"] }
```

**Response `201`** — `ConversationResponse`

---

#### `GET /api/conversations`

Returns all conversations the caller participates in, sorted by `lastMessageAt` descending. `participants` is `null` in list views.

**Response `200`**
```json
[
  {
    "id": "<uuid>",
    "type": "DIRECT",
    "name": null,
    "title": "bob",
    "peerId": "<uuid>",
    "createdBy": null,
    "participants": null,
    "lastMessagePreview": "Hey!",
    "lastMessageAt": "2026-07-06T10:00:00Z",
    "lastMessageSeq": 42,
    "unreadCount": 3,
    "callerRole": "MEMBER",
    "memberCount": 2
  }
]
```

**`callerRole`** values: `OWNER` | `ADMIN` | `MEMBER`

---

#### `GET /api/conversations/{conversationId}`

Full detail view. `participants` is populated.

**Response `200`** — `ConversationResponse` with `participants`:
```json
{
  "id": "<uuid>",
  "type": "GROUP",
  "name": "Team Alpha",
  "title": "Team Alpha",
  "participants": [
    {
      "userId": "<uuid>",
      "username": "alice",
      "role": "OWNER",
      "joinedAt": "2026-07-01T09:00:00Z",
      "lastReadSeq": 40,
      "lastDeliveredSeq": 42
    }
  ],
  "callerRole": "OWNER",
  "memberCount": 3,
  ...
}
```

---

#### `GET /api/conversations/{conversationId}/messages?before={seq}&limit={n}`

Cursor-paged message history (newest page first). Omit `before` on first load; pass `nextCursor` for older pages.

| Param | Default | Max |
|---|---|---|
| `before` | `Long.MAX_VALUE` | — |
| `limit` | 20 | 50 |

**Response `200`**
```json
{
  "messages": [
    {
      "id": "<uuid>",
      "conversationId": "<uuid>",
      "senderId": "<uuid>",
      "clientMessageId": "msg-001",
      "type": "TEXT",
      "content": "Hello!",
      "sequenceNumber": 42,
      "createdAt": "2026-07-06T10:00:00Z",
      "editedAt": null,
      "deleted": false
    }
  ],
  "nextCursor": 10
}
```

`nextCursor` is the `sequenceNumber` to pass as `before` on the next call. `null` means no earlier messages.

---

#### `GET /api/conversations/{conversationId}/messages/after?after={seq}&limit={n}`

Returns messages with `sequenceNumber > after`. Used for post-reconnect catch-up. Same limit constraints as above.

**Response `200`** — same shape as `/messages` but `nextCursor` is always `null`.

---

### Group Management — `/api/conversations/{id}/...`

All group management endpoints require the caller to be a participant. Role enforcement is applied per operation (see below).

#### `DELETE /api/conversations/{conversationId}`

Soft-deletes the group. `OWNER` only. **Response `204`**

---

#### `POST /api/conversations/{conversationId}/participants`

Adds a member. `OWNER` or `ADMIN`.

**Request**
```json
{ "userId": "<uuid>" }
```

**Response `201`** — `ParticipantResponse`

---

#### `DELETE /api/conversations/{conversationId}/participants/{userId}`

Removes a member. `OWNER` can remove anyone; `ADMIN` can only remove `MEMBER`-role users. **Response `204`**

---

#### `PUT /api/conversations/{conversationId}/participants/{userId}/role`

Changes a member's role. `OWNER` only. Cannot demote the owner.

**Request**
```json
{ "role": "ADMIN" }
```

**Response `200`** — `ParticipantResponse`

---

#### `POST /api/conversations/{conversationId}/transfer-ownership`

Transfers `OWNER` role to another participant. Caller loses `OWNER` and becomes `ADMIN`. `OWNER` only.

**Request**
```json
{ "newOwnerId": "<uuid>" }
```

**Response `200`** — updated `ConversationResponse`

---

### Message Search — `/api/messages`

#### `GET /api/messages/search?query={q}&cursor={cursor}&limit={n}`

Full-text search over messages the caller has access to. Cursor-paginated.

| Param | Required | Notes |
|---|---|---|
| `query` | yes | Plain-text search term |
| `cursor` | no | Opaque cursor from previous response |
| `limit` | no | Default 20 |

**Response `200`**
```json
{
  "results": [
    {
      "messageId": "<uuid>",
      "conversationId": "<uuid>",
      "senderId": "<uuid>",
      "senderName": "bob",
      "content": "...matched text...",
      "sequenceNumber": 17,
      "createdAt": "2026-07-06T09:30:00Z"
    }
  ],
  "nextCursor": "eyJzZXEiOjE3fQ=="
}
```

---

#### `GET /api/messages/search/hybrid?query={q}&limit={n}`

Hybrid semantic + keyword search. Combines pgvector cosine similarity (via ai-service) with full-text ranking. Returns top-k results with a merged `rankScore`, no pagination.

**Response `200`**
```json
[
  {
    "messageId": "<uuid>",
    "conversationId": "<uuid>",
    "content": "...",
    "sequenceNumber": 17,
    "rankScore": 0.93
  }
]
```

> Requires `chatflow-ai` to be running. Falls back gracefully to keyword-only if ai-service is unavailable.

---

### Friends — `/api/friends`

#### `POST /api/friends/requests`

Sends a friend request.

**Request**
```json
{ "userId": "<uuid>" }
```

**Response `201`** — `FriendshipResponse`

```json
{
  "id": "<uuid>",
  "otherUserId": "<uuid>",
  "otherUsername": "bob",
  "initiatorId": "<uuid>",
  "status": "PENDING",
  "createdAt": "2026-07-06T10:00:00Z",
  "updatedAt": "2026-07-06T10:00:00Z"
}
```

**`status`** values: `PENDING` | `ACCEPTED` | `DECLINED`

---

#### `GET /api/friends/requests/received`

Friend requests waiting for the caller to accept or decline.

**Response `200`** — `FriendshipResponse[]`

---

#### `GET /api/friends/requests/sent`

Friend requests the caller sent that are still pending.

**Response `200`** — `FriendshipResponse[]`

---

#### `POST /api/friends/requests/{friendshipId}/accept`

**Response `200`** — `FriendshipResponse` with `status: ACCEPTED`

---

#### `POST /api/friends/requests/{friendshipId}/decline`

**Response `200`** — `FriendshipResponse` with `status: DECLINED`

---

#### `GET /api/friends`

All accepted friendships for the caller.

**Response `200`** — `FriendshipResponse[]`

---

#### `DELETE /api/friends/{userId}`

Removes an accepted friendship. **Response `204`**

---

### Notifications — `/api/notifications`

#### `GET /api/notifications?cursor={instant}&limit={n}`

Notification feed, newest first. Cursor is an ISO-8601 instant (`createdAt` of the last item from the previous page).

| Param | Default | Max |
|---|---|---|
| `limit` | 20 | 50 |

**Response `200`**
```json
[
  {
    "id": "<uuid>",
    "actorId": "<uuid>",
    "type": "FRIEND_REQUEST",
    "referenceType": "FRIENDSHIP",
    "referenceId": "<uuid>",
    "preview": "bob sent you a friend request",
    "eventCount": 1,
    "read": false,
    "createdAt": "2026-07-06T10:00:00Z"
  }
]
```

**`type`** values: `FRIEND_REQUEST` | `FRIEND_REQUEST_ACCEPTED` | `GROUP_MEMBER_ADDED` | `GROUP_MEMBER_REMOVED` | `GROUP_ROLE_CHANGED` | `GROUP_OWNERSHIP_TRANSFERRED` | `NEW_MESSAGE`

**`referenceType`** values: `FRIENDSHIP` | `CONVERSATION` | `MESSAGE`

---

#### `GET /api/notifications/unread-count`

**Response `200`**
```json
{ "count": 5 }
```

---

#### `POST /api/notifications/{id}/read`

Marks a single notification read. **Response `204`**

---

#### `POST /api/notifications/read-all`

Marks all caller notifications read. **Response `204`**

---

#### `DELETE /api/notifications/{id}`

Dismisses (deletes) a notification. **Response `204`**

---

### Presence — `/api`

#### `GET /api/users/{userId}/presence`

Returns online status of a user. Only callable by users who share at least one conversation with `userId`.

**Response `200`**
```json
{ "userId": "<uuid>", "online": true, "onlineSince": "2026-07-06T09:00:00Z" }
```

`onlineSince` is `null` when offline.

---

#### `GET /api/conversations/{conversationId}/presence`

Returns online status of the first two participants. Designed for DIRECT chats; for groups only the two earliest-joined participants are reported.

**Response `200`**
```json
{
  "participantOne": { "userId": "<uuid>", "online": true, "onlineSince": "2026-07-06T09:00:00Z" },
  "participantTwo": { "userId": "<uuid>", "online": false, "onlineSince": null }
}
```

---

### Media — `/api/messages/media`

#### `POST /api/messages/media` (multipart/form-data)

Uploads a media file and creates a message of type `MEDIA`. Returns immediately (status `202`) before thumbnail generation completes.

**Form fields**

| Field | Type | Required | Notes |
|---|---|---|---|
| `file` | binary | yes | The file to upload |
| `type` | string | yes | `IMAGE` \| `VIDEO` \| `AUDIO` \| `FILE` |
| `conversationId` | UUID | yes | Must be a conversation the caller participates in |
| `caption` | string | no | Optional text caption |

**File size limits** (configurable via `app.media.max-file-size-bytes`):

| Type | Default limit |
|---|---|
| `IMAGE` | 10 MB |
| `VIDEO` | 100 MB |
| `AUDIO` | 20 MB |
| `FILE` | 50 MB |

**Response `202`**
```json
{
  "id": "<uuid>",
  "messageId": "<uuid>",
  "conversationId": "<uuid>",
  "senderId": "<uuid>",
  "messageType": "IMAGE",
  "status": "PROCESSING",
  "mediaUrl": null,
  "thumbnailUrl": null,
  "mimeType": "image/jpeg",
  "fileSize": 204800,
  "originalFileName": "photo.jpg",
  "caption": null,
  "createdAt": "2026-07-06T10:00:00Z"
}
```

`status` progresses: `PROCESSING` → `READY` (once thumbnail is generated). The `MEDIA_THUMBNAIL_READY` WebSocket frame carries the final URLs.

---

#### `GET /api/messages/media/{id}`

Returns the `MediaMessageResponse` for a media message. Caller must be a participant in the owning conversation.

**Response `200`** — same shape as upload response, with `status: READY` and `thumbnailUrl` populated.

---

#### `GET /api/messages/media/{id}/url`

Returns a time-limited signed URL for the original file. Valid for 60 minutes by default.

**Response `200`**
```json
{ "url": "https://...", "expiresAt": "2026-07-06T11:00:00Z" }
```

---

#### `DELETE /api/messages/media/{id}`

Soft-deletes the media message. Caller must be the sender. **Response `204`**

---

### AI — `/ai/conversations` (via gateway → ai-service :8081)

Requires the same `Authorization: Bearer` header. The gateway routes `/ai/**` to the ai-service.

#### `GET /ai/conversations/{conversationId}/summary`

"Catch me up" — summarizes the messages the caller has not yet read (unread since their last `MARK_READ` watermark).

**Response `200`**
```json
{
  "summary": "Bob shared the Q3 roadmap. Alice asked about the release date. Team agreed on July 15.",
  "messageCount": 12,
  "fromSequence": 41,
  "toSequence": 52
}
```

Returns `messageCount: 0` and a "You're all caught up" message when there is nothing unread.

---

#### `POST /ai/conversations/{conversationId}/ask`

RAG "ask your chat history" — finds the most semantically relevant messages in the conversation, then answers the question grounded in them with citations.

**Request**
```json
{ "question": "What was the agreed launch date?" }
```

**Response `200`**
```json
{
  "answer": "The team agreed on July 15 [<uuid>][<uuid>].",
  "citations": [
    {
      "messageId": "<uuid>",
      "sequenceNumber": 47,
      "similarity": 0.91,
      "preview": "Alice: July 15 works for everyone then?"
    }
  ]
}
```

`citations` reference real message IDs in the conversation so the client can deep-link to the source.

> Both AI endpoints require the Anthropic API key (`ANTHROPIC_API_KEY`) and an OpenAI-compatible embeddings key (`AI_EMBEDDING_API_KEY`) to be configured in ai-service.

---

### WebSocket Protocol

Connect to `ws://localhost:8080/ws` (embedded mode) or `ws://localhost:8083/ws` (external mode). Pass the JWT as `Authorization: Bearer <token>` during the HTTP upgrade handshake.

All frames are JSON with the envelope:
```json
{ "type": "<TYPE>", "requestId": "<optional>", "payload": { ... } }
```

`requestId` is a client-supplied string; the server echoes it on the corresponding response frame (e.g. `SEND_MESSAGE` → `MESSAGE_ACK`).

#### Client → Server (Inbound)

| Type | Payload fields | Description |
|---|---|---|
| `SEND_MESSAGE` | `conversationId`, `clientMessageId`, `content` | Send a text message. `clientMessageId` is your idempotency key. |
| `CONVERSATION_OPEN` | `conversationId` | Notify server this conversation is visible; advances `lastDeliveredSeq`. |
| `MARK_READ` | `conversationId`, `upToSeq` | Mark all messages up to `upToSeq` as read. |
| `MESSAGE_DELIVERED` | `conversationId`, `sequenceNumber` | Explicit delivery acknowledgement (usually sent automatically by CONVERSATION_OPEN). |
| `TYPING` | `conversationId`, `typing` (bool) | Broadcast typing indicator to other participants. |
| `PING` | — | Keepalive. Server replies with `PONG`. |

#### Server → Client (Outbound)

| Type | Payload | Triggered by |
|---|---|---|
| `MESSAGE` | `MessageResponse` | Another participant sends a message. |
| `MESSAGE_ACK` | `MessageResponse` | Server confirms your own `SEND_MESSAGE` (carries the assigned `sequenceNumber`). |
| `STATUS_UPDATE` | `conversationId`, `lastDeliveredSeq` | A participant opened the conversation (delivery watermark advanced). |
| `SEEN_UPDATE` | `conversationId`, `userId`, `lastReadSeq` | A participant marked messages read. |
| `PRESENCE` | `userId`, `status` (`ONLINE`\|`OFFLINE`), `onlineSince` | User connected or disconnected. |
| `TYPING` | `conversationId`, `userId`, `typing` | Another participant is typing. |
| `MEDIA_MESSAGE` | `MediaMessageResponse` | Another participant uploaded a media file. |
| `MEDIA_THUMBNAIL_READY` | `messageId`, `conversationId`, `mediaId`, `thumbnailUrl` | Thumbnail generation complete; update the media bubble. |
| `GROUP_CREATED` | `ConversationResponse` | You were added to a new group. |
| `GROUP_MEMBER_ADDED` | `conversationId`, `userId`, `username` | A member was added to a group you're in. |
| `GROUP_MEMBER_REMOVED` | `conversationId`, `userId` | A member was removed. If `userId` is you, you've been kicked. |
| `GROUP_ROLE_CHANGED` | `conversationId`, `userId`, `newRole` | A member's role was changed. |
| `GROUP_OWNERSHIP_TRANSFERRED` | `conversationId`, `newOwnerId` | Group ownership transferred. |
| `GROUP_DELETED` | `conversationId` | The group was deleted by the owner. |
| `FRIEND_REQUEST` | `FriendshipResponse` | Someone sent you a friend request. |
| `FRIEND_REQUEST_ACCEPTED` | `FriendshipResponse` | Your friend request was accepted. |
| `FRIEND_REQUEST_DECLINED` | `FriendshipResponse` | Your friend request was declined. |
| `FRIEND_REMOVED` | `FriendshipResponse` | Someone unfriended you. |
| `NOTIFICATION` | `NotificationResponse` | A new in-app notification was created. |
| `NOTIFICATION_READ` | `notificationId` | A notification was marked read (from another session). |
| `ERROR` | `code`, `message`, `requestId` | Error response to a client frame. |
| `PONG` | — | Reply to `PING`. |

---

### Internal Endpoints (Service-to-Service)

These are not for client use. All require `X-Internal-Token: <token>` matching `app.internal.token`. The gateway does **not** route any `/internal/**` path.

| Method | Path (on core :8080) | Description |
|---|---|---|
| `POST` | `/internal/realtime/connect` | chatflow-realtime → core: user connected, trigger presence + replay |
| `POST` | `/internal/realtime/disconnect` | chatflow-realtime → core: user disconnected |
| `POST` | `/internal/realtime/inbound` | chatflow-realtime → core: forward an inbound WS command |
| `GET` | `/internal/conversations/{id}/participants/{userId}` | ai-service → core: membership check |
| `GET` | `/internal/conversations/{id}/transcript/unread?userId={uid}` | ai-service → core: fetch unread backlog for summary |

| Method | Path (on ai-service :8081) | Description |
|---|---|---|
| `POST` | `/internal/embeddings/search` | core → ai-service: vector similarity search for hybrid message search |

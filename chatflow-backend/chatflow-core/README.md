# chatflow-core

The **heart of ChatFlow** — and the original monolith the microservices migration was
strangled out of. Everything that wasn't extracted into a dedicated service still lives
here: authentication, the unified conversation/messaging model, groups, friends,
notifications, presence/typing, media message records, and the **transactional outbox**
that is the event backbone for the whole platform.

It owns the **primary PostgreSQL database** and is the source of truth for users,
conversations, messages, and memberships. Other services (ai, media, realtime) hang off
the events it emits and call back into its `/internal/**` API.

Listens on **`:8080`**.

---

## What's still in core

| Domain          | Package              | Responsibility                                                        |
|-----------------|----------------------|-----------------------------------------------------------------------|
| **Auth**        | `auth`               | Register/login, JWT minting + validation, Spring Security wiring.     |
| **Conversation**| `conversation`       | Unified 1:1 + group model, message send/page/replay, soft delete, search. |
| **Friend**      | `friend`             | Friend requests, accept/decline, friendships.                         |
| **Notification**| `notification`       | Notification fan-out (driven by outbox events), unread counts.        |
| **Presence**    | `presence`           | Online/offline presence (in-memory store + lifecycle events).         |
| **Typing**      | `typing`             | Per-conversation typing indicators.                                   |
| **Media**       | `media`              | Media *message* records, S3/local storage, signed URLs, validation. Thumbnailing is delegated to chatflow-media via events. |
| **Realtime**    | `realtime`           | WebSocket handler + the inbound dispatch shared with chatflow-realtime. |
| **Infra**       | `infra`              | Outbox, idempotency guard, Redis relay, WebSocket session registry, after-commit hooks. |

Cross-cutting config lives in `config/` (security, websocket, scheduling, metrics,
resilience, correlation-id filter, secrets fail-fast, exception handler).

---

## Where it sits

```
            chatflow-gateway :8088
                    │ /api/**
                    ▼
        ┌───────────────────────────┐        Postgres ─ source of truth
        │   chatflow-core :8080     │────────┤
        │   auth · chat · groups    │        Redis ─ chat:relay + presence
        │   friends · notifications │
        │   presence · media records│
        │   transactional outbox    │
        └─────┬───────────────┬─────┘
              │ emits events  │ /internal/** callbacks
              ▼ (outbox→Kafka)│
   ai · media · realtime ◀────┘
```

---

## Event backbone — the transactional outbox

Core writes domain events to an `outbox_events` table **in the same transaction** as the
business change, so an event is never lost or emitted for a rolled-back write. A poller
then dispatches them. Transport is switchable via `app.outbox.transport`:

- **`in-process`** (default) — events are dispatched in-JVM; legacy monolith behaviour.
- **`kafka`** — each event is published to `chatflow.outbox.events` and consumed back via
  an in-app listener. This is what the extracted services subscribe to.

Consumers are **idempotent**: a `processed_events` table + `IdempotencyGuard` dedup on the
event id (Kafka is at-least-once).

**Event types emitted** (`OutboxEventType`):

```
message.created                 friend.requested
message.embedding_requested     friend.request_accepted
media.processing_requested      group.member_added / member_removed
conversation.deleted            group.role_changed / ownership_transferred
```

Notable downstream wiring:
- `message.embedding_requested` → **chatflow-ai** populates its embedding store.
- `media.processing_requested` → **chatflow-media** thumbnails; result returns on
  `chatflow.media.thumbnail-ready`, consumed by `MediaThumbnailReadyListener` (kafka mode only).
- `conversation.deleted` → **chatflow-ai** evicts that conversation's embeddings.

---

## API surface

### Public (`/api/**`, JWT required; gateway validates at the edge, core re-verifies)

| Area          | Endpoints |
|---------------|-----------|
| **Auth**      | `POST /api/auth/register`, `POST /api/auth/login` |
| **Conversations** | `POST /api/conversations/direct`, `POST /api/conversations/group`, `GET /api/conversations/{id}`, `GET /api/conversations/{id}/messages`, `GET /api/conversations/{id}/messages/after`, `DELETE /api/conversations/{id}` |
| **Membership** | `POST /api/conversations/{id}/participants`, `DELETE /api/conversations/{id}/participants/{userId}`, `PUT /api/conversations/{id}/participants/{userId}/role`, `POST /api/conversations/{id}/transfer-ownership` |
| **Search**    | `GET /api/messages/search` (keyword), `GET /api/messages/search/hybrid` (keyword + vector via ai) |
| **Media**     | `POST /api/messages/media` (multipart), `GET /api/messages/media/{id}`, `GET /api/messages/media/{id}/url`, `DELETE /api/messages/media/{id}` |
| **Friends**   | `POST /api/friends/requests`, `GET /api/friends/requests/received`, `GET /api/friends/requests/sent`, `POST /api/friends/requests/{id}/accept`, `POST /api/friends/requests/{id}/decline`, `DELETE /api/friends/{userId}` |
| **Notifications** | `GET /api/notifications/unread-count`, `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`, `DELETE /api/notifications/{id}` |
| **Presence**  | `GET /api/users/{userId}/presence`, `GET /api/conversations/{id}/presence` |

### Internal (`/internal/**`, guarded by shared `X-Internal-Token` — must match ai/realtime)

| Endpoint | Caller |
|----------|--------|
| `GET /internal/conversations/{id}/participants/{userId}` | ai — membership check for RAG |
| `GET /internal/conversations/{id}/transcript/unread`     | ai — transcript for summaries |
| `POST /internal/realtime/connect` · `/disconnect` · `/inbound` | chatflow-realtime — connection lifecycle + inbound frames |

### WebSocket
`/ws` — served by core **only** when `app.realtime.mode=embedded` (default). In
`external` mode, chatflow-realtime terminates sockets and core just publishes frames to
the Redis `chat:relay` channel and serves the `/internal/realtime/*` callbacks.

---

## Database

Schema is **owned by Flyway** (`src/main/resources/db/migration`); Hibernate runs
`ddl-auto: validate` only.

| Migration | What it does |
|-----------|--------------|
| `V1__init` | Baseline — unified conversation model (full schema; **must run against a fresh DB**). |
| `V2__soft_delete_columns` | Soft-delete columns for group conversations + notifications. |
| `V3__ai_embeddings` | Per-message embeddings (since removed). |
| `V4__drop_message_embeddings` | Drops the embedding table — ai owns embeddings now. |
| `V5__processed_events` | Consumer-side idempotency table. |

> ⚠️ The schema is a clean rewrite to the unified model. **Drop the dev DB once** before
> the first boot. See [`../docs/schema-unification-plan.md`](../docs/schema-unification-plan.md).

---

## Configuration

Key settings from `application.yaml` (all env-overridable):

| Concern | Key | Default |
|---------|-----|---------|
| HTTP port | (server.port) | `8080` |
| Postgres | `spring.datasource.*` (`DB_USERNAME`/`DB_PASSWORD`) | `chatflow`/`chatflow` |
| Redis | `SPRING_DATA_REDIS_HOST/PORT` | `localhost:6379` |
| Kafka | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| JWT | `JWT_SECRET` / `app.jwt.expiration-ms` | dev placeholder / 24h |
| Internal token | `INTERNAL_TOKEN` | `dev-internal-token` (**must match ai/realtime**) |
| Outbox transport | `APP_OUTBOX_TRANSPORT` | `in-process` (`kafka` for the split) |
| Realtime mode | `APP_REALTIME_MODE` | `embedded` (`external` → chatflow-realtime) |
| Object store | `app.s3.*` (`S3_ENDPOINT`, `S3_BUCKET`, keys) | MinIO @ `localhost:9000` |
| ai upstream | `AI_BASE_URL` | `http://localhost:8081` |
| Tracing | `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` |

`SecretsGuard` **fails fast under the `prod` profile** if `JWT_SECRET` / `INTERNAL_TOKEN`
are still dev defaults.

---

## Cross-cutting concerns

- **Resilience4j** (Spring Cloud CircuitBreaker) + RestClient timeouts + fallbacks on the
  sync call to ai (`EmbeddingSearchClient` → degrades to keyword-only search).
- **Observability** — OpenTelemetry tracing exported over OTLP (Jaeger), Micrometer →
  Prometheus metrics (incl. a `chatflow.messages.delivery.latency` histogram with
  p50/p95/p99), structured JSON logs (logstash-logback) with a correlation-id filter.
- **Scheduled cleanup** — `DailyCleanupService` purges soft-deleted rows past their
  retention window; `MediaCleanupService`/`MediaStoragePurger` retry orphaned object-store deletes.

---

## Running

**Prereqs:** Postgres, Redis (and Kafka + MinIO if running in `kafka`/S3 mode). The
easiest path is Docker Compose from the reactor root.

```bash
# Just the infra, then run core from the IDE / mvn:
docker compose up -d        # postgres, redis, kafka, minio, jaeger ...
./mvnw -pl chatflow-core -am spring-boot:run

# Or the whole platform containerised:
docker compose --profile apps up --build
```

**Build the jar:**

```bash
./mvnw -pl chatflow-core -am -DskipTests package
```

**Docker** — build context is the reactor root (parent pom + contracts module), see `Dockerfile`.

---

## Tests

```bash
./mvnw -pl chatflow-core -am -Dsurefire.failIfNoSpecifiedTests=false test
```

Includes **ArchUnit** tests enforcing the package boundaries / no-cycle architecture.

---

## Stack

- Java 21, Spring Boot 4.0 (web, data-jpa, security, websocket, validation, actuator)
- PostgreSQL + Flyway, Redis, Spring Kafka
- Spring Cloud 2025.1.1 (circuit breaker — Resilience4j)
- jjwt 0.12.6, AWS SDK v2 (S3), Thumbnailator
- Micrometer + Prometheus, OpenTelemetry (OTLP), logstash-logback

Relates to the living migration plan in [`../docs/microservices-migration.md`](../docs/microservices-migration.md).

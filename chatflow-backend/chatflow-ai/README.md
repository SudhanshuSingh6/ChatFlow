# chatflow-ai

The **AI service** — the first and lowest-risk extraction in the migration, and the one that
delivers the headline goal: isolating the AI workload from the chat hot path. It owns
everything LLM/vector-related:

- **Embeddings store** — its own pgvector database of per-message embeddings.
- **Vector search** — nearest-neighbour lookups core calls for hybrid message search.
- **RAG** — "ask a question about this conversation" answered from the embedding store.
- **Summary** — summarise a conversation's unread transcript.
- **Chat completion** — the LLM provider call (Anthropic) backing RAG/summary answers.

Listens on **`:8081`**.

---

## Database-per-service

ai owns its **own PostgreSQL instance** (separate from core, default port `5433`) with the
**pgvector** extension — use the `pgvector/pgvector` image. There is **no foreign key back to
core**: the `message_embeddings` table is **denormalized**, carrying the message snippet +
sender / sequence / conversation inline, so a vector search needs no cross-database JOIN.

Rows arrive via **event-carried state transfer** — core emits `MessageEmbeddingRequested`,
ai consumes it and embeds. Schema is owned by Flyway (no JPA; the `vector` type is handled
with `JdbcTemplate` + native SQL).

| Migration | What it does |
|-----------|--------------|
| `V1__embeddings` | `vector` extension + denormalized `message_embeddings` table; HNSW cosine index + conversation index. |
| `V2__processed_events` | Consumer-side idempotency table. |

---

## Where it sits

```
                         chatflow-gateway :8088
                               │ /ai/**
                               ▼
   message.embedding_requested ┌──────────────────────────┐    GET /internal/conversations/…/participants/…
   (Kafka: chatflow.outbox)──▶ │   chatflow-ai :8081      │──▶ GET /internal/conversations/…/transcript/unread
   conversation.deleted ─────▶ │   embeddings · RAG       │    (membership + transcript, X-Internal-Token)
                               │   summary · chat         │ ────────────────────────────────▶ chatflow-core :8080
                               └────────┬─────────┬───────┘
                                        │         │
                          pgvector DB ──┘         └── LLM / embedding providers
                          (own Postgres :5433)        (Anthropic, OpenAI-compatible embeddings)
                                        ▲
   core hybrid search ─ POST /internal/embeddings/search ─┘
```

---

## How embeddings get in

`EmbeddingEventConsumer` (`@KafkaListener`) subscribes to the shared outbox topic
`chatflow.outbox.events` under its **own consumer group** (`chatflow-ai-embedding`), so it
sees every event independently of core, then filters:

- **`MessageEmbeddingRequested`** → `EmbeddingIngestService` calls the embedding provider and
  upserts the row. **Process-then-mark** idempotency: the upsert is idempotent on
  `message_id`, so a crash between embedding and marking just re-embeds harmlessly — and no DB
  transaction is held across the slow embed call. `IdempotencyGuard` dedups on the event id.
- **`ConversationDeleted`** → evicts that conversation's embeddings (delete-by-conversation,
  inherently idempotent, no dedup needed).

---

## API surface

### Public (`/ai/**`, JWT required — ai **re-verifies** the caller's JWT itself until the gateway terminates auth)

| Endpoint | Purpose |
|----------|---------|
| `POST /ai/conversations/{id}/ask` | **RAG** — answer a question from the conversation's embeddings. Re-checks membership against core first. |
| `GET /ai/conversations/{id}/summary` | **Summary** — fetches the unread transcript from core, summarises it. |

### Internal (`/internal/**`, shared `X-Internal-Token`)

| Endpoint | Caller |
|----------|--------|
| `POST /internal/embeddings/search` | **core** — vector hits for hybrid message search (`EmbeddingSearchClient`). |

### Sync calls *out* to core (with `X-Internal-Token`, circuit-breaker + timeout + fallback)

| Client | core endpoint | Fallback |
|--------|---------------|----------|
| `ConversationAccessClient` | `GET /internal/conversations/{id}/participants/{userId}` | **deny** access |
| `TranscriptClient` | `GET /internal/conversations/{id}/transcript/unread` | **5xx** |

---

## Providers

| Concern | Provider | Default | Key |
|---------|----------|---------|-----|
| **Embeddings** | OpenAI-compatible API | `text-embedding-3-small`, 1536-dim | `AI_EMBEDDING_API_KEY` |
| **Chat / RAG answers** | Anthropic (`anthropic-java` 2.34.0) | `claude-opus-4-8`, max 4096 tok | `ANTHROPIC_API_KEY` |

> The embedding `dimensions` (1536) **must match** the `message_embeddings.embedding`
> `vector(1536)` column. Changing the model/dimensions needs a migration.

---

## Configuration

All env-overridable (`application.yaml`):

| Concern | Key / Env var | Default |
|---------|---------------|---------|
| HTTP port | `SERVER_PORT` | `8081` |
| Own Postgres | `AI_DB_URL` / `AI_DB_USERNAME` / `AI_DB_PASSWORD` | `jdbc:postgresql://localhost:5433/chatflow_ai` |
| Kafka | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| Inbound topic | `APP_OUTBOX_TOPIC` | `chatflow.outbox.events` |
| Consumer group | `APP_OUTBOX_CONSUMER_GROUP` | `chatflow-ai-embedding` |
| core upstream | `CORE_BASE_URL` | `http://localhost:8080` |
| Internal token | `INTERNAL_TOKEN` | `dev-internal-token` (**must match core**) |
| JWT secret | `JWT_SECRET` | dev placeholder (**must match core**) |
| Embedding provider | `AI_EMBEDDING_BASE_URL` / `AI_EMBEDDING_MODEL` / `AI_EMBEDDING_API_KEY` | OpenAI / `text-embedding-3-small` |
| Chat provider | `AI_CHAT_PROVIDER` / `AI_CHAT_MODEL` / `ANTHROPIC_API_KEY` | `anthropic` / `claude-opus-4-8` |
| Tracing | `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` |

Actuator exposes `health`, `info`, `metrics`, `prometheus`. The trace started in core is
continued across the Kafka hop (`listener.observation-enabled`).

---

## Running

> Needs a **pgvector** Postgres (own instance, default `:5433`), Kafka, a running core
> (`:8080`) for membership/transcript calls, and provider API keys for real embeddings/chat.

**Locally (from the reactor root):**

```bash
export AI_EMBEDDING_API_KEY=sk-…   # OpenAI-compatible
export ANTHROPIC_API_KEY=sk-ant-…
./mvnw -pl chatflow-ai -am spring-boot:run
```

Core must run in **`kafka` outbox mode** for embedding events to flow.

**Build the jar:**

```bash
./mvnw -pl chatflow-ai -am -DskipTests package
```

**Docker** (build context = reactor root, see `Dockerfile`):

```bash
docker compose --profile apps up --build ai
```

---

## Tests

```bash
./mvnw -pl chatflow-ai test
```

Uses `spring-kafka-test` for the consumer path.

---

## Stack

- Java 21, Spring Boot 4.0 (web, jdbc, security, actuator)
- PostgreSQL + **pgvector** + Flyway (`JdbcTemplate` + native SQL — no JPA)
- Spring Kafka, Spring Cloud 2025.1.1 (circuit breaker — Resilience4j)
- `anthropic-java` (chat), OpenAI-compatible embeddings, jjwt 0.12.6
- Micrometer + Prometheus, OpenTelemetry (OTLP)

Relates to the living migration plan in [`../docs/microservices-migration.md`](../docs/microservices-migration.md)
(Phases 1 & 1.5).

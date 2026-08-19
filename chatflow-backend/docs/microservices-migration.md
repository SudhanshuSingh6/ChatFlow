# ChatFlow — Monolith → Microservices Migration Strategy

**Status:** proposal / strategy. No code changed yet.
**Goal driving this split:** isolate the heavy/independent workloads (**AI/RAG** and
**media processing**) from the core chat path, and build a credible, showcase-grade
event-driven microservices architecture.
**Decomposition style chosen:** *coarse* — **4 services**, not one-per-feature. Fewer
moving parts, less operational sprawl, still demonstrates the patterns that matter.

> Read `ARCHITECTURE.md` first. The current modular monolith already enforces a one-way
> dependency rule with **0 cycles** (ArchUnit `ModuleBoundaryTest`) and a transactional
> **outbox**. That clean state is exactly what makes this extraction realistic — we are
> promoting existing module boundaries to network boundaries, not untangling spaghetti.

---

## 0. Honest framing — what you gain, what it costs

**Gain:** `media` (FFmpeg/Thumbnailator, CPU-heavy) and `ai` (embeddings + LLM calls,
slow + costly + rate-limited) can scale, fail, and deploy independently of the
latency-sensitive chat path. A spike in video uploads or a slow LLM no longer competes
for threads with message delivery.

**Cost (state plainly):**
- The send-message flow is one local `@Transactional` today (`ChatService.sendMessage`:
  persist message + bump unread + write outbox, then `AfterCommit` pushes). Across
  services there is **no distributed transaction** — correctness now rests on the outbox
  + idempotency + eventual consistency.
- In-process Spring `@EventListener` async (media → thumbnail pipeline) becomes
  inter-service messaging with retries, ordering, and poison-message handling.
- New operational surface: a broker, an API gateway, per-service DBs, container
  orchestration, distributed tracing. This is the real price of admission.

**Mitigant you already have:** the transactional outbox (`infra/outbox`). It gives
at-least-once, crash-safe event delivery *without* a distributed transaction. We keep it
and point it at a real broker instead of the in-process `OutboxPoller`.

---

## 1. Target topology — 4 services

```
                         ┌─────────────────────────┐
            WSS / HTTPS   │     API Gateway          │  (Spring Cloud Gateway)
   clients ──────────────►│  JWT verify at the edge  │
                         └────────┬───────┬─────────┘
                                  │       │
                 WebSocket        │       │  REST
                ┌─────────────────▼──┐    │
                │  realtime-gateway   │    │
                │  WS termination,    │    │
                │  session registry,  │    │
                │  presence, typing   │    │
                └──────┬──────▲───────┘    │
        commands(async)│      │ events     │ REST (history, create convo,
                       │      │ (deliver)  │       friends, notifications)
                       ▼      │            ▼
   ┌──────────────────────────────────────────────────┐
   │                   core-chat-service                │  owns the truth
   │  auth · user · conversation(msg/delivery/replay)   │  Postgres:
   │  group · friend · notification                     │  users, conversations,
   │  + transactional OUTBOX (event producer)           │  messages, friendships,
   └───────┬───────────────────────────────┬───────────┘  notifications, outbox
           │ events                          │ events
           │ (message.created,               │ (media requested / membership)
           │  message.embedding_requested)   │
           ▼                                  ▼
   ┌────────────────────┐            ┌────────────────────────┐
   │    ai-service      │            │     media-service        │
   │ embeddings, vector │            │ upload, FFmpeg/thumbnail │
   │ search, RAG, chat  │            │ S3/MinIO, access URLs,   │
   │ completion         │            │ cleanup                  │
   │ Postgres+pgvector  │            │ Postgres + object store  │
   └────────────────────┘            └────────────────────────┘

        ▲  all services publish/consume via  ┌──────────────────┐
        └────────────────────────────────────┤  Event broker    │  (Kafka recommended)
                                              └──────────────────┘
```

### Why these four boundaries

| Service | Owns (current packages) | Why it's a service |
|---------|-------------------------|--------------------|
| **core-chat-service** | `auth`, `user`, `conversation`, `friend`, `notification`, `infra/outbox`, `infra/tx` | The transactional heart. Keeps the message/unread/receipt invariants in one local DB transaction. The "monolith remainder." |
| **realtime-gateway** | `realtime`, `infra/websocket`, `infra/redis`, `presence`, `typing` | Stateful long-lived connections scale on a different axis than request/response. Presence + typing are *ephemeral, connection-bound* state, so they live with the connections. |
| **ai-service** | `ai/chat`, `ai/embedding`, `conversation/search` (embeddings, vector search, RAG, summary) | LLM + embedding calls are slow, costly, rate-limited, and bursty. **Primary isolation target.** |
| **media-service** | `media/*` | FFmpeg/Thumbnailator are CPU/memory-heavy and already async + event-driven. **Primary isolation target.** |

> Note `presence`/`typing` go to the gateway, not core-chat — they are derived from live
> sockets, so co-locating them with the session registry avoids a chatty round trip on
> every keystroke. Their cross-instance fan-out already runs over Redis pub/sub today,
> which the gateway keeps.

---

## 2. Data ownership (database-per-service)

The golden rule: **one writer per table; no shared DB.** Today it's a single Postgres.

| Service | Datastore | Tables / data |
|---------|-----------|---------------|
| core-chat | Postgres (`chatflow_core`) | `users`, `conversations`, `messages`, `group_*`, `friendships`, `notifications`, `outbox_events` |
| ai | Postgres + **pgvector** (`chatflow_ai`) | `message_embeddings` (+ snippet text, see §4) |
| media | Postgres (`chatflow_media`) + object store | `media_messages`; bytes in S3/MinIO |
| realtime-gateway | none durable | ephemeral: session registry (in-mem), presence/typing (in-mem + Redis) |

**Prerequisite this forces:** you are on `ddl-auto: update` with **no migrations**. Before
splitting a schema you must adopt **Flyway** (or Liquibase) per service. Treat this as
Phase 0 — it's non-negotiable once schemas diverge. See §8.

**Cross-service reads** (e.g. media-service rendering needs sender username): do **not**
join across DBs. Either (a) carry the needed fields in the event payload, or (b) keep a
small **read replica/projection** populated from events, or (c) make a synchronous REST
call to the owner and cache it. Prefer (a) for hot paths.

---

## 3. The event backbone — promote the outbox, don't replace it

You already have the hard part. Today:

```
service @Transactional → OutboxWriter.write(...) (same tx)
        → OutboxPoller (drains PENDING) → OutboxDispatcher → OutboxEventHandler (in-process)
```

After the split, **the producer side is unchanged** — services still write to their own
`outbox_events` in the same local transaction. Only the drain target changes:

```
OutboxPoller → publish to BROKER (Kafka topic per aggregate/event family)
            → other services consume → their own OutboxEventHandler equivalent
```

`OutboxEventType` constants (`message.created`, `message.embedding_requested`,
`friend.requested`, `group.member_added`, …) become your **topic/routing keys** — they
were already designed as string routing keys, not an enum, "so the column can absorb new
event kinds." That foresight pays off here.

### Broker choice

| Option | Verdict |
|--------|---------|
| **Apache Kafka** | **Recommended.** Durable log + replay + consumer groups + partition ordering per conversation. Best fit for an event-sourced chat backbone and the most resume-valuable. |
| RabbitMQ | Simpler to run; fine if you want lower ops overhead. Lacks cheap replay. |
| Redis Streams | You already run Redis; lowest new infra. Weakest durability/tooling story. |

Recommendation: **Kafka**, partitioned by `conversationId` so per-conversation event
order is preserved (critical for `sequenceNumber` correctness on the consumer side).

### Delivery semantics

- **At-least-once** end to end (outbox guarantees produce; consumers must be idempotent).
- Consumers dedupe on natural keys you already have: `clientMessageId` (messages),
  `aggregateId` + `eventType`, embedding row uniqueness, etc.
- Add a `processed_events` table (or rely on upsert semantics) in each consumer.

---

## 4. The four hard flows (and how each survives the boundary)

### 4a. Send a 1:1 message → realtime delivery
**Today:** `ChatService.sendMessage` persists + writes outbox `message.created`; an
`AfterCommit` directly pushes `MESSAGE`/`MESSAGE_ACK` via `WebSocketGateway` (local +
Redis relay).

**After:** core-chat keeps the local transaction (message + unread + outbox). It no longer
pushes directly. Instead:
```
core-chat commit → outbox message.created → Kafka(topic chat.messages, key=conversationId)
   → realtime-gateway consumes → looks up connected sessions for receiver → pushes MESSAGE
   → sender's ACK becomes an event/command too (or gateway emits ACK on receive)
```
The existing `CrossServerRelay` (Redis pub/sub for "user on another instance") is
**subsumed by Kafka consumer groups** — every gateway instance consumes, the one holding
the user's socket delivers. Offline receiver → `ReplayService` logic moves with
core-chat's read API: on reconnect the gateway calls `GET /conversations/replay` (or
consumes from the user's last-acked offset).

### 4b. Embeddings / RAG (the AI isolation win)
**Today:** `message.embedding_requested` outbox event → `MessageEmbeddingOutboxHandler` →
`MessageEmbeddingWorker` (in `conversation/search`) calls `EmbeddingService`.

**After:** the `conversation/search` package **moves into ai-service**. core-chat still
emits `message.embedding_requested` (it owns the message); ai-service consumes it,
embeds, stores the vector in its own pgvector DB. **Key design choice:** store a
denormalized **content snippet** alongside each embedding so RAG retrieval doesn't make a
chatty per-hit call back to core-chat. `ConversationRagService` + `ConversationSummaryService`
move to ai-service and expose REST (`POST /ai/ask`, `GET /ai/conversations/{id}/summary`)
behind the gateway. ai-service owns both the vector store and the LLM client
(`AnthropicChatCompletionService`) — fully self-contained.

### 4c. Media upload + thumbnailing (the media isolation win)
**Today:** `MediaMessageService.upload` (sync) → `MediaProcessingEvent` (in-process) →
`ThumbnailService` (`@Async`) → `ThumbnailGeneratedEvent` → push `MEDIA_THUMBNAIL_READY`.

**After:** media-service owns the whole pipeline already — it just stops using Spring
application events and uses its **internal** queue/worker (or its own outbox). When a
thumbnail is ready it emits `media.thumbnail_ready` → realtime-gateway pushes
`MEDIA_THUMBNAIL_READY` to recipients. **Authorization wrinkle:** `MediaAccessGuard` needs
to know conversation/group membership, which core-chat owns. Resolve via a synchronous
`GET /internal/conversations/{id}/members` on core-chat (cached), or carry participant IDs
on the `media.requested` event. Prefer the cached sync call for access checks (they must be
correct, not eventually-correct).

### 4d. Notifications & friend/group events
`notification` stays in core-chat (it's tightly coupled to users + the same transaction
that creates the notifiable event). The *delivery* of a live notification to a socket is
the same pattern as 4a: emit event → gateway pushes. Friend/group outbox events
(`friend.requested`, `group.member_added`, …) likewise flow core-chat → gateway for live
fan-out.

---

## 5. Cross-cutting concerns across the boundary

| Concern | Today | Microservices |
|---------|-------|---------------|
| **AuthN** | `JwtAuthenticationFilter` per request; `JwtHandshakeInterceptor` for WS | Verify JWT **at the API gateway edge**; pass a trusted identity header (or re-verify in each service with a shared secret / JWKS). Keep `JwtService` as a shared lib. |
| **Correlation IDs** | `CorrelationIdFilter` → MDC | Propagate `X-Correlation-Id` over HTTP **and** as a Kafka header. Upgrade to **OpenTelemetry** trace context for true distributed tracing. |
| **Metrics** | Micrometer + Prometheus, common tag `app=chatflow` | Same, per service; scrape each. Add RED metrics per endpoint + broker lag. |
| **Tracing** | none (single process) | **Add now** — OpenTelemetry SDK → Tempo/Jaeger. The biggest debuggability gap once calls span services. |
| **Logging** | Logstash JSON encoder | Keep; ship to a central sink (Loki/ELK) keyed by correlation + trace id. |
| **Config/secrets** | `application.yaml` + env | Per-service config (k8s ConfigMaps/Secrets). The dev-default JWT/MinIO secrets must become real secrets. |
| **Resilience** | in-process calls can't fail on the network | Add timeouts + **circuit breakers** (Resilience4j) on every sync inter-service call; the broker absorbs async backpressure. |

---

## 6. Shared code

Extract a small `chatflow-contracts` library (or a few):
- **event DTOs** (`NotificationCommand`, message/embedding event payloads) — versioned.
- **JWT verification** (`JwtService`, claims) — shared.
- **common web** (correlation filter, `ProblemDetail` exception mapping) — optional.

Keep contracts **additive and versioned** (e.g. schema registry for Kafka, or just
backward-compatible JSON). Resist the urge to share entities — share *events*, not tables.

---

## 7. Migration plan — strangler fig, incremental

Order chosen to (a) deliver the isolation goal first and (b) learn the patterns on the
lowest-risk service before touching the hot path. Each phase ships independently; the
monolith keeps running throughout.

- [~] **Phase 0 — foundations (no split yet)** — *in progress*
  - [x] Adopt **Flyway** migrations; freeze `ddl-auto` to `validate` — already in place
        (`V1__init`, `V2__soft_delete_columns`, `V3__ai_embeddings`).
  - [x] Stand up the **broker** (Kafka, KRaft single-node) + `kafka-ui` in
        `docker-compose.yml` (alongside the existing Postgres/Redis/MinIO).
  - [x] Make the outbox publish to Kafka (still consumed in-process) — done via an
        `OutboxEventPublisher` seam behind `app.outbox.transport` (`in-process` default |
        `kafka`). `KafkaOutboxPublisher` produces (sync, keyed by `aggregateId`),
        `OutboxConsumer` (`@KafkaListener`) drives the same `OutboxDispatcher`. ArchUnit
        still 0 cycles. — done 2026-06-03
  - [x] Add **OpenTelemetry** tracing to the monolith — Micrometer Observation →
        `micrometer-tracing-bridge-otel`, spans exported over OTLP to a Jaeger all-in-one
        container (UI :16686). Trace context propagates over HTTP and as Kafka headers
        (`spring.kafka.{template,listener}.observation-enabled`). `TracingWiringTest` guards
        the wiring. — done 2026-06-03
        _(Boot 4 gotcha: the autoconfig is `@ConditionalOnClass(OtelTracer)` and the
        `spring-boot-micrometer-tracing-opentelemetry` module does NOT pull the bridge in
        transitively — it must be declared explicitly, same lesson as `spring-boot-flyway`.)_
  - [ ] Add an **API gateway** (Spring Cloud Gateway) in front of the monolith — no routing
        changes yet, just terminate + verify JWT at the edge. _(Deferred to the start of
        Phase 1 — a gateway earns its keep once there's a second service to route to.)_

- [~] **Phase 1 — extract ai-service** *(lowest risk, biggest isolation win)* — *in progress*
  Decision: **multi-module Maven monorepo** (one git repo, one reactor pom, shared BOM).
  - [x] Reactor restructure: parent `chatflow-backend` (packaging `pom`) aggregating
        `chatflow-contracts`, `chatflow-core`, `chatflow-ai`. The whole monolith moved to
        `chatflow-core/` (history preserved via `git mv`); all of core's tests still pass
        (ArchUnit 0 cycles, tracing). — done 2026-06-03
  - [x] `chatflow-contracts` module: dependency-free shared event records. First contract:
        `MessageEmbeddingRequested` (event-carried state transfer payload, §4b). — done 2026-06-03
  - [x] `chatflow-ai` bootable skeleton (web + actuator, port 8081, `contextLoads` test).
        No behavior moved yet — core still owns/runs all AI code. — done 2026-06-03
  - [x] **Embeddings write-path slice** — done 2026-06-04:
        - core's `ChatService` now emits the enriched `MessageEmbeddingRequested` (content +
          conversation/sender/sequence carried) as the embedding event payload.
        - `chatflow-ai` owns its **own pgvector DB** (`postgres-ai` :5433, Flyway
          `V1__embeddings.sql`) with a **denormalized** `message_embeddings` (snippet + sender
          + sequence inline, no FK/JOIN to core).
        - `EmbeddingEventConsumer` (own consumer group on the shared outbox topic) →
          `EmbeddingIngestService` embeds via the OpenAI-compatible provider (copied into ai) →
          upserts vector+snippet. Idempotent on `message_id`.
        - Tracing wired in ai (continues the trace across the Kafka hop).
        - Tests: ingest, consumer routing, vector literal, `contextLoads` (all infra-free).
        - **Transition state:** core keeps its in-process embedding path too (parallel run),
          so RAG in core is unaffected. Double-embedding in kafka mode is intentional until
          cutover. `senderName` carried as `null` until the RAG move populates it.
  - [x] **RAG read-path move** — done 2026-06-04:
        - chat provider (`ai/chat`) moved into ai; `ConversationRagService` + DTOs moved to
          `chatflow-ai`, serving `POST /ai/conversations/{id}/ask` entirely from the
          denormalized embedding store (snippet/sender/sequence inline) — no message data
          leaves core.
        - access check is a **sync call to core** (`GET /internal/conversations/{id}/participants/{uid}`,
          guarded by a shared `X-Internal-Token`; `/internal/**` permitted in core security).
        - ai re-verifies the caller JWT itself (shared `app.jwt.secret`) — own `JwtService` +
          filter + stateless `SecurityConfig` — until the gateway terminates auth.
        - `senderName` now populated on the embedding event (core resolves it at emit time),
          so citations show authors without ai reading core's `users`.
        - Tests: RAG service (access/empty/grounded), `contextLoads` with full wiring.
  - [x] **Summary move** — done 2026-06-04. Decision: **move to ai + sync transcript fetch**
        (keeps all LLM logic in one service). `ConversationSummaryService` + `SummaryController`
        (`GET /ai/conversations/{id}/summary`) live in ai; it checks membership (reusing
        `ConversationAccessClient`) then fetches the unread backlog from core's new
        `GET /internal/conversations/{id}/transcript/unread` (`ConversationTranscriptService`,
        watermark + paging + name resolution stay in core, which owns that data). The
        `ConversationTranscript` wire type lives in `chatflow-contracts`. Tests: non-participant
        → 403, caught-up, and a real summarization.
  - [x] **API gateway** — done 2026-06-04: new `chatflow-gateway` module (Spring Cloud
        Gateway **webmvc**, Spring Cloud `2025.1.1` BOM, aligned with Boot 4.0). Single public
        entry on **:8088** routing `/api/**`→core:8080 and `/ai/**`→ai:8081; `EdgeAuthFilter`
        validates the JWT at the edge (shared secret), permitting `/api/auth/**` + actuator and
        401-ing everything else. The token is still forwarded so downstream re-verification
        keeps working (non-breaking). Tests: validator, edge filter, `contextLoads`.
        _(Follow-ups: inject a trusted `X-User-Id` header + drop downstream JWT re-verification
        to fully terminate auth at the edge; `/ws` isn't proxied by the servlet gateway yet —
        it connects directly to core.)_
  - [x] **Cutover (LLM features)** — done 2026-06-04: deleted core's `ai/chat/*`,
        `ConversationRagService`, `ConversationSummaryService`, the Ask/Summary DTOs, their
        `ConversationController` endpoints, and the `anthropic-java` dependency. RAG, summary,
        and chat completion now live **only** in ai-service. Core + ai build green, 0 cycles.
  - [ ] **Embedding ownership (the unplanned coupling)** — *not done*. Core's
        `ai/embedding/*` + `conversation/search/*` were **kept**: core's **hybrid/semantic
        message search** (`MessageSearchService`, `/api/messages/search/hybrid`) and
        `DailyCleanupService` depend on the embedding client + vector store. So embeddings are
        still produced by core too (the "double embedding" is now justified — core's store
        powers search, ai's store powers RAG). Fully ending it requires **moving semantic
        message search to ai-service** (then core stops embedding entirely). New follow-up —
        see below.

  > **Scope finding:** "extract ai-service" is effectively complete — ai owns embeddings (its
  > store), RAG, summary, and chat. The leftover is that *semantic message search* is a second
  > vector consumer living in core; moving it to ai is its own task (call it Phase 1.5),
  > tracked here:

- [x] **Phase 1.5 — semantic search moved to ai; core stops embedding** — done 2026-06-04:
  - core keeps orchestrating hybrid search (keyword + RRF merge + hydration) but the **vector
    hits now come from ai** via `EmbeddingSearchClient` → ai's `POST /internal/embeddings/search`
    (`EmbeddingSearchService`, guarded by `X-Internal-Token`). core passes the caller's
    conversation scope (it owns membership); ai embeds + searches its store. Fails soft to
    keyword-only if ai is down.
  - **Deleted from core**: `ai/embedding/*`, `conversation/search/*` (worker, outbox handler,
    repository, backfill, `EmbeddingRequested`, `VectorSearchHit`) and the worker test. core's
    `message_embeddings` table dropped (`V4__drop_message_embeddings.sql`); `DailyCleanupService`
    no longer touches embeddings (no FK). The shared wire types `EmbeddingSearchRequest/Hit`
    live in contracts.
  - **Double-embedding ended**: only ai embeds now. core still *emits* `MessageEmbeddingRequested`
    (ai consumes via Kafka) — so embeddings require `APP_OUTBOX_TRANSPORT=kafka` + ai running;
    in in-process mode the event has no consumer (logged, harmless) and hybrid search degrades
    to keyword-only.
  - ArchUnit green (0 cycles); core's `ai` package is gone, the `aiDependsOnNoFeature` rule kept
    as an `allowEmptyShould` guard.
  - _Optional remaining nicety:_ ai-side cleanup of embeddings for deleted messages (currently
    harmless orphans — core hydration drops any hit whose message is gone).

  > Build note: the repo is now a reactor. Build everything with `./mvnw package`. To test
  > one module run `./mvnw -pl chatflow-core -am -Dsurefire.failIfNoSpecifiedTests=false test`
  > (`-am` also builds its `chatflow-contracts` dependency).

- [~] **Phase 2 — extract media-service** — *in progress*
  **Scope finding:** a media upload is transactionally a *message creation* in core (locks the
  conversation, allocates the per-conversation `nextSequenceNumber`, inserts a `messages` row,
  updates last-message/cursors, notifies). That can't be lifted to another DB. So the boundary
  chosen is **"extract processing only"**: core keeps upload + message creation + `media_messages`
  metadata; the **CPU-heavy thumbnailing (FFmpeg/Thumbnailator)** moves to a **stateless** worker.
  - [x] Scaffold + processing move — done 2026-06-04: new `chatflow-media` module (stateless,
        :8082, no DB). Moved storage client (with a new `read()`) + `ThumbnailService` into it.
        Contracts: `MediaProcessingRequested`, `MediaThumbnailReady`. `MediaProcessingConsumer`
        consumes the shared outbox topic (filters `media.processing_requested`), reads the
        original from the **shared object store**, thumbnails it, writes it back, and publishes
        `media.thumbnail_ready`. Tracing wired. Tests: MediaKeys, consumer routing, `contextLoads`.
        Parallel run — core's in-process pipeline untouched this turn.
  - [x] Fixed a latent Kafka bug (found here): Spring Boot's autoconfigured `KafkaTemplate<?,?>`
        doesn't satisfy a `KafkaTemplate<String,String>` injection → added explicit typed
        template beans in `chatflow-media` and `chatflow-core` (the outbox publisher would have
        failed to start in `kafka` mode).
  - [x] Core rewiring — done 2026-06-04: `MediaMessageService.upload` now emits
        `MediaProcessingRequested` via the outbox (drained to Kafka) instead of the in-process
        `MediaProcessingEvent` (no bytes read into memory anymore). New
        `MediaThumbnailReadyListener` (`@KafkaListener` on `chatflow.media.thumbnail-ready`,
        gated to kafka mode) sets `thumbnail_url` + pushes `MEDIA_THUMBNAIL_READY` after commit.
        Deleted core's `ThumbnailService`, `ThumbnailEventListener`, `MediaProcessingEvent`,
        `ThumbnailGeneratedEvent` + `ThumbnailServiceTest`. Core + media point at the same object
        store. **Phase 2 functionally complete** — FFmpeg/thumbnailing fully isolated.
        _(Left as harmless dead config in core: the unused `mediaProcessingExecutor` pool +
        `app.media.thumbnail.*`/`processing.*` keys — only chatflow-media uses thumbnailing now.)_
  - [ ] (Optional) move object-storage of originals + presigned access-URL issuance to media too,
        if we want media to fully own the bucket (bigger; access-URL needs the sync membership check).

- [x] **Phase 3 — extract realtime gateway (`chatflow-realtime`, :8083)** — done 2026-06-04.
      Refined from the original sketch (see plan `.claude/plans/agile-wandering-fairy.md`):
  - **Delivery bus = Redis pub/sub** (reused `chat:relay`, not Kafka): core's `WebSocketGateway`
    publish path is unchanged; `chatflow-realtime`'s `RelaySubscriber` consumes `chat:relay` and
    writes the frame JSON verbatim to sockets (frames are opaque — no shared `OutboundMessage`).
  - **Inbound** forwarded over REST to core `/internal/realtime/{connect,disconnect,inbound}`
    (`CoreCommandClient`, X-Internal-Token, timeouts + circuit breaker that ignores validation 4xx).
    Core's WS dispatch was extracted into the shared `RealtimeInboundService` used by both the
    embedded handler and `InternalRealtimeController`. `PING` answered at the edge.
  - **Presence/typing stay in core**; the gateway reports connect/disconnect lifecycle (first/last
    session) which drives `PresenceService` + `ReplayService` + typing-clear.
  - **Flag + fallback** `app.realtime.mode = embedded (default) | external`; in external mode core's
    `WebSocketConfig`/`ChatWebSocketHandler` and the `chat:relay` listener are `@ConditionalOnProperty`-gated off.
  - **Metrics**: `realtime.active.sessions`, `realtime.connected.users`, `realtime.frames.sent/received`,
    `realtime.relay.messages` + a **Prometheus + Grafana** stack (compose infra, provisioned
    "ChatFlow Realtime" dashboard). k8s: `realtime` Deployment/Service (Prometheus/Grafana on k8s
    parked → kube-prometheus-stack).
  - Tests: realtime handler routing, relay subscriber, contextLoads; core arch stays 0 cycles. All green.

- [ ] **Phase 4 — core-chat is what remains**
  - Split the DB schema (`chatflow_core`), remove now-dead code, finalize contracts.
  - core-chat = auth, user, conversation, group, friend, notification + outbox.

> Marking convention (matches `ARCHITECTURE.md`): flip to `[x]` and append
> `— done YYYY-MM-DD` when the phase is deployed and verified end-to-end.

---

## 8. Prerequisites / blockers to resolve before Phase 1

1. **Migrations** — `ddl-auto: update` cannot coexist with independently-evolving
   per-service schemas. Flyway first. *(Hard blocker.)*
2. **Idempotent consumers** — confirm every consumer dedupes (`clientMessageId`,
   `aggregateId+eventType`). At-least-once *will* redeliver.
3. **Event versioning** — decide JSON-compatible evolution vs. a schema registry.
4. **Local dev story** — a one-command `docker-compose` (Postgres×N, Kafka, Redis, MinIO,
   gateway, services) or you'll lose the ability to run the system locally.
5. **Secrets** — replace dev-default JWT/MinIO creds with real secret management.

---

## 9. Patterns demonstrated (showcase value)

For the learning/resume goal, this architecture lets you point to concrete,
non-toy implementations of:

- **Transactional outbox** (already built) + broker publishing → exactly-effectively-once.
- **Event-driven choreography** between services (no central orchestrator).
- **Database-per-service** with denormalized read models / event-carried state transfer.
- **API gateway** with edge auth + **Spring Cloud Gateway**.
- **Idempotent consumers** keyed on business identifiers.
- **Circuit breakers / timeouts** (Resilience4j) on sync hops.
- **Distributed tracing** (OpenTelemetry) + structured logging + RED metrics.
- **Stateful realtime gateway** decoupled from business logic via events.
- **Strangler-fig migration** from a clean modular monolith — the textbook path.

---

## 10. Recommendation in one paragraph

Do **Phase 0 + Phase 1 (ai-service)** first and stop to evaluate. That single extraction
delivers the headline goal (AI isolation), exercises the entire backbone — Flyway, Kafka,
gateway, tracing, idempotent consumers — on the **lowest-risk** module, and is fully
reversible. If the operational overhead feels worth it after Phase 1, continue to media
and the realtime gateway. If it doesn't, you've isolated the one workload that most
benefits and kept everything else as a clean, well-bounded monolith — which, given your
current 0-cycle architecture, is a perfectly respectable end state.

---

## 11. Post-Phase-2 hardening (done 2026-06-04)

Optional quality/ops batch (plan: `.claude/plans/agile-wandering-fairy.md`):

1. **Media cleanup** — deleted core's dead `AsyncConfig` (`mediaProcessingExecutor`) +
   `app.media.thumbnail/processing` config (thumbnailing lives in chatflow-media).
2. **Idempotent consumers** — `processed_events` table + `IdempotencyGuard` in core (V5) and ai
   (V2); `OutboxConsumer` claims atomically (firstTime + dispatch in one tx), `EmbeddingEventConsumer`
   uses process-then-mark (upsert already idempotent). Dedup key = `OutboxEventMessage.id`.
3. **Resilience4j** — Spring Cloud CircuitBreaker (programmatic, Boot-4 aligned) + `RestClient`
   timeouts + fallbacks on `EmbeddingSearchClient` (→ keyword-only), `ConversationAccessClient`
   (→ deny), `TranscriptClient` (→ 5xx). `ResilienceConfig` raises the default 1s time limiter.
4. **Full Docker Compose** — multi-stage Dockerfiles (build from reactor root) + the 4 app services
   under an `apps` profile + MinIO bucket-init; `docker compose --profile apps up --build`.
5. **Real secrets** — `.env.example` + `.env` gitignored; `SecretsGuard` fails fast under the
   `prod` profile if JWT/internal-token are dev defaults.
6. **Kubernetes** — Kustomize `k8s/base` (Deployments/Services/Secret/Ingress/HPA) + `overlays/local`
   (namespace + single-replica infra + bucket-init Job); see `k8s/README.md`.
7. **AI orphan cleanup** — core emits `ConversationDeleted` on group cascade; ai evicts that
   conversation's embeddings (`MessageEmbeddingRepository.deleteByConversationId`).

Also fixed a latent bug found during (4): Spring Boot's autoconfigured `KafkaTemplate<?,?>` doesn't
satisfy a `KafkaTemplate<String,String>` injection → explicit typed template beans in core + media.

Still parked: per-family Kafka topics, schema registry, gateway X-User-Id termination, media owning
the object store, WebSocket-through-gateway (with Phase 3). Phase 3 (realtime-gateway) is the next
major extraction.

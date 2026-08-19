# ChatFlow — Codebase Explained

A deep walk-through of the ChatFlow backend: what each part does, how the pieces
fit, the patterns that recur, and a prioritized list of what to improve.

> Scope: `chatflow-backend/` — a single Spring Boot application. Frontend/clients
> are out of scope.

---

## 1. What it is

A real-time chat backend supporting:

- **1:1 messaging** with delivery/seen receipts
- **Group messaging** with roles, read/delivery cursors
- **Media messages** (image/video/audio/file) with thumbnails and signed URLs
- **Friendships** (request → accept/decline), which gate group membership
- **Presence** (online/offline) and **typing** indicators
- **Message search** (scoped + global)

Designed to run as **multiple instances** behind a load balancer, with **Redis**
relaying real-time frames between them.

### Tech stack

| Layer | Choice |
|------|--------|
| Language / platform | Java 21, Spring Boot |
| Web / API | Spring MVC (REST) + raw WebSocket (`/ws`) |
| Persistence | PostgreSQL via Spring Data JPA (`ddl-auto=update`) |
| Realtime scaling | Redis pub/sub (cross-instance relay) + presence |
| Auth | JWT (jjwt) on HTTP filter and WS handshake |
| Media storage | Local disk **or** S3/MinIO (AWS SDK v2), profile-selected |
| Media processing | Thumbnailator (images), FFmpeg (video) |
| Observability | Micrometer + Prometheus, Actuator, JSON logs (logstash-logback) |

---

## 2. High-level architecture

```
   Clients (REST + WebSocket)
            │
   ┌────────▼─────────┐         ┌──────────────────┐
   │ ChatFlow inst. A │◄───────►│      Redis       │◄───────► ChatFlow inst. B …
   │  WS sessions     │ pub/sub │  relay + presence │
   └────────┬─────────┘         └──────────────────┘
            │ JPA
       ┌────▼─────┐
       │ Postgres │   (also shared by all instances)
       └──────────┘
```

A user's WebSocket lives on **one** instance. To push to a user, a service calls
`WebSocketGateway`, which (a) delivers to any local session and (b) publishes to
Redis so the instance holding that user's session can deliver too. This is the
core horizontal-scaling mechanism.

**Three invariants run through all messaging:**

1. **Monotonic `sequenceNumber`** per conversation/group → clients detect gaps and backfill.
2. **Idempotency** via a client-supplied id → retries never duplicate.
3. **Persist first, deliver after commit, reconcile on reconnect** → the DB is the
   source of truth; the realtime push is a best-effort fast path.

---

## 3. Project structure

Organised by **feature module** under `com.chatflow`, each with its own
`entity / repository / dto / service / controller`:

| Package | Responsibility |
|---------|----------------|
| `auth` | Register/login, JWT issue & validation, Spring Security config |
| `user` | User accounts |
| `friend` | Friend requests & lifecycle; gates group membership |
| `message` | 1:1 conversations, messages, delivery/seen, **search** |
| `group` | Groups, membership/roles, group messages, delivery/read receipts |
| `media` | Upload, validation, storage, thumbnails, signed URLs, cleanup |
| `presence` | Online/offline state |
| `typing` | Typing indicators |
| `infra` | Cross-cutting transport: WebSocket + Redis relay |
| `config` | Security, WebSocket, scheduling, async, metrics, exception handling |

---

## 4. Cross-cutting infrastructure

### Transport (`infra/websocket`)

- **`ChatWebSocketHandler`** (raw `TextWebSocketHandler` on `/ws`) — the entry point
  for all realtime traffic. On connect: registers the session, flips presence
  (first session only), replays missed 1:1 + group messages. Dispatches inbound
  frames (`SEND_MESSAGE`, `MESSAGE_ACK`, `CONVERSATION_OPEN`, `CONVERSATION_SEEN`,
  `GROUP_SEND_MESSAGE`, `GROUP_READ_RECEIPT`, `GROUP_MESSAGE_DELIVERED`, `TYPING`,
  `PING`) to feature services. Maps `IllegalArgumentException`/`SecurityException`
  to client errors, everything else to a generic server error.
- **`WebSocketSessionRegistry`** — `userId → local session(s)`; returns
  first/last-session booleans so presence flips correctly with multiple devices.
  Registers the only live metric (`chatflow.websocket.connections` gauge).
- **`WebSocketGateway`** — single send API: local delivery + Redis publish.
- **`OutboundMessage` / `InboundMessage`** — the wire envelopes; `OutboundMessage.Type`
  enumerates every server→client frame.

### Cross-instance relay (`infra/redis`)

- **`CrossServerRelay`** (`MessageListener`) — publishes `CrossServerMessage`
  envelopes to a shared Redis channel and, on receipt, delivers to the local
  registry. **Best-effort**: publish failures are logged, not retried.
- **`RedisConfig`** — `StringRedisTemplate` + listener container.

### Commit-safe side effects (`infra/tx`)

- **`AfterCommit.run(Runnable)`** — runs a side effect only after the transaction
  commits (or inline if none is active). The correct pattern for realtime pushes:
  persist first, deliver after commit.

### Config (`config`)

- **`SecurityConfig`** — stateless; permits `/api/auth/**` and `/ws`, authenticates
  everything else.
- **`JwtAuthenticationFilter` / `JwtHandshakeInterceptor`** — JWT validation for
  HTTP and the WS handshake respectively.
- **`CorrelationIdFilter`** — `X-Correlation-Id` → MDC → JSON logs (REST only).
- **`MetricsConfig`** — metric-name constants + global `app=chatflow` tag.
- **`AsyncConfig`** — `mediaProcessingExecutor` for thumbnailing.
- **`SchedulingConfig`** — `@EnableScheduling` + a typing-timer executor.
- **`RestExceptionHandler`** — RFC-7807 `ProblemDetail`: `IllegalArgumentException`
  → 400, `SecurityException` → 403, validation → 400, etc.

---

## 5. Modules in depth

### auth

JWT-based. `AuthService` registers/logs in; `JwtService` issues/validates tokens;
`JwtAuthenticationFilter` authenticates requests; `JwtHandshakeInterceptor` gates
the WebSocket handshake. Authenticated user id is read everywhere as
`UUID.fromString(principal.getName())`. Single ~24h token; **no refresh token or
revocation.**

### friend

- **Model:** one **symmetric** `Friendship` row per pair. `create()` canonically
  orders the two UUIDs (`userOneId < userTwoId`), so A→B and B→A map to the same
  unique `(user_one_id, user_two_id)` row regardless of direction. `initiatorId`
  records who asked; `status` is `PENDING → ACCEPTED | DECLINED`.
- **Repository** queries handle symmetry; `findPendingReceived` (PENDING, not
  initiator) vs `findPendingSent` (PENDING, initiator) split incoming/outgoing.
- **Service:** send (by username; rejects self/dupes; resends a DECLINED),
  accept/decline (only the recipient — not the initiator), unfriend (hard delete).
  **Now pushes** `FRIEND_REQUEST` / `FRIEND_REQUEST_ACCEPTED` /
  `FRIEND_REQUEST_DECLINED` / `FRIEND_REMOVED` after commit.
- **Cross-module:** `areFriends(...)` gates adding members to groups.
- Unfriend hard-deletes (no UNFRIENDED state). Concurrent duplicate sends are
  caught via the unique constraint and surfaced as 400.

### message (1:1)

- **Model:** `Conversation` (two participants + denormalized `lastMessage`,
  `lastMessageAt`, per-participant unread counts) and `Message`
  (`conversationId`, `senderId`, `receiverId`, `content`, `status`, `sequenceNumber`).
- **Status:** `SENT → DELIVERED → SEEN`.
- **`ChatService.sendMessage`** — dedup by `clientMessageId`, lock conversation
  (`findByIdForUpdate`), verify participant, assign `sequenceNumber`, save, bump
  unread, **push after commit** (ACK to sender, `MESSAGE` to receiver).
- **`DeliveryService`** — `ack` (one msg → DELIVERED), `conversationOpen` (bulk
  DELIVERED + clear unread + optional seen), `markSeen` (bulk DELIVERED→SEEN,
  notify senders). All status moves are **monotonic** (guarded so they never go
  backwards).
- **`ConversationService`** — history (`getMessages`, `before` cursor) and gap-fill
  (`getMessagesAfter`).
- **`ReplayService.replayForUser`** — on reconnect, re-push all still-`SENT`
  messages.

### group

- **Model:** `Group` + N `GroupMember` + `GroupMessage`. **No fan-out table** —
  one message row; "who read/received" is derived from per-member cursors
  (`lastReadSequenceNumber`, `lastDeliveredSequenceNumber`). O(1) storage/message.
- **Roles:** `OWNER > ADMIN > MEMBER`, enforced in `GroupService`. Add member =
  owner/admin **and** must be a friend. Removal/role/transfer/delete have layered
  rules; owner must transfer before leaving.
- **Concurrency:** pessimistic locks (`findByIdForUpdate`, `findByGroupIdForUpdate`)
  because role/ownership invariants span multiple rows.
- **`GroupChatService.sendMessage`** — dedup, sequence, save, **after-commit**
  fan-out (`GROUP_MESSAGE` to all members except sender).
- **`GroupDeliveryService`** — `markDelivered` (advance delivery cursor),
  `markRead` (advance read cursor + broadcast `GROUP_READ_RECEIPT` to others).
  Monotonic; skips redundant receipts.
- **`replayForUser`** — re-push messages beyond each membership's delivery cursor
  (capped 200).
- **Lifecycle events now pushed** after commit: `GROUP_CREATED`,
  `GROUP_MEMBER_ADDED`, `GROUP_MEMBER_REMOVED`, `GROUP_ROLE_CHANGED`,
  `GROUP_OWNERSHIP_TRANSFERRED`, `GROUP_DELETED`.

### media

A separate persistence + pipeline that rides the same delivery rails. See
`chatflow-backend/docs/MEDIA_MESSAGES_EXECUTION.md`.

1. **Upload** (`POST /api/messages/media`) — multipart; metadata to Postgres.
2. **Validation** — size/MIME/extension; MIME from **magic bytes**, not the client
   header; UUID storage keys (never the original filename).
3. **Storage** — `MediaStorageService` abstraction; `local` (disk) or `s3`
   (S3/MinIO) by profile.
4. **Delivery** — `MEDIA_MESSAGE` to participants via `WebSocketGateway`.
5. **Thumbnails** — async (`MediaProcessingEvent → ThumbnailService → ThumbnailGeneratedEvent`),
   Thumbnailator/FFmpeg, off the request thread; emits `MEDIA_THUMBNAIL_READY`.
6. **Signed URLs** (`GET …/{id}/url`) — presigned, time-limited, participant-gated.
7. **Cleanup** (`DELETE …/{id}`) — transactional-outbox: mark `PENDING_DELETION`
   in-tx, purge storage after commit, scheduled retry. No orphaned files.

### presence

- `PresenceStore` interface; `InMemoryPresenceStore` (`@Profile("!prod")`,
  `ConcurrentHashMap<UUID,Instant>`).
- `PresenceService` flips online/offline and **broadcasts `PRESENCE`** to all of
  the user's conversation partners.
- Driven by the WebSocket handler's connect/disconnect (first/last session).

### typing

- `TypingStateManager` tracks transient typing state and broadcasts `TYPING`,
  with a timer executor to auto-expire stale typing state.

### search (in `message`)

- **Scoped** (`/api/conversations/{id}/messages/search`,
  `/api/groups/{id}/messages/search`) and **global** (`/api/search/messages`).
- **Authorization is the SQL predicate** (direct: caller is sender/receiver; group:
  caller is a member) — no post-filtering.
- Case-insensitive `LIKE` (wildcards escaped, min length 2); newest-first; keyset
  cursor on `(createdAt, id)` so direct + group results page together. See
  `chatflow-backend/docs/MESSAGE_SEARCH.md`.

---

## 6. Data model summary

```
users ──< friendships >── users        (symmetric pair; gates group add)

conversations (p1, p2, unread, lastMessage)
   └─< messages (seq, status SENT/DELIVERED/SEEN)

groups ──< group_members (role, read/delivery cursors)
   └─< group_messages (seq)

media_messages (type, status, storageKey, mediaUrl, thumbnailUrl, conv|group)
```

JPA `ddl-auto=update` manages schema; entities declare their own indexes and
unique constraints (canonical friendship pair, `(group_id,user_id)`,
`(group_id,client_message_id)`, etc.).

---

## 7. Recurring patterns

- **Persist-then-deliver-after-commit** (`AfterCommit`) — realtime pushes never fire
  for data that rolls back.
- **Idempotency keys** — `clientMessageId` dedups message retries.
- **Sequence numbers + replay** — durability backstop for lossy realtime delivery.
- **Cursor bookkeeping over fan-out rows** — group read/delivery via per-member
  cursors, not per-recipient rows.
- **Strategy by profile** — `MediaStorageService` swaps local ↔ S3 with no caller
  changes.
- **Transactional outbox (lite)** — media deletion marks intent in-tx, purges
  storage after commit, retries on failure.
- **Authorization in the query** — search and friend/group access enforced by SQL
  predicates, not post-filtering.

---

## 8. Verification status

- Unit tests exist for the **media** pipeline, **search**, **friend**, **group**
  lifecycle events, and the `AfterCommit` helper (all passing).
- The single `@SpringBootTest contextLoads` **cannot run** in the current
  environment because Postgres rejects the default credentials, and there is no
  test database / Testcontainers setup. As a result, **JPA queries (including
  search JPQL and the membership subquery) are not verified against a real DB.**

---

## 9. Things to improve / add / polish

Ordered roughly by leverage. Items marked ✅ were addressed during recent work.

### Already fixed
- ✅ **1:1 send pushed before commit** → moved to `AfterCommit` (no phantom messages on rollback).
- ✅ **Group lifecycle & friend events were pull-only** → now pushed in real time.
- ✅ **Friend `sendRequest` duplicate-race** → returns 400 instead of a 500 (caught constraint via `saveAndFlush`).
- ✅ **`RestExceptionHandler` Spring-API mismatch** (`getAllValidationResults` → `getParameterValidationResults`).
- ✅ **`application.yaml` duplicate keys** (two `spring:` blocks, repeated `app.media`) consolidated.

### Tier 1 — foundation (highest leverage)
- [ ] **Database migrations (Flyway/Liquibase).** Replace `ddl-auto=update`; it can't
  do safe renames/backfills/rollbacks and risks drift. Also the prerequisite for
  full-text search indexes.
- [ ] **`docker-compose` for local dev** (Postgres + Redis + MinIO). Fixes the
  broken `contextLoads`, enables `s3`-profile testing, one-command onboarding.
- [ ] **CI pipeline** (GitHub Actions running `mvn test`). Nothing currently guards regressions.
- [ ] **Integration tests with Testcontainers.** Coverage is unit-only; every
  repository `@Query` is unverified against real Postgres.

### Tier 2 — correctness / robustness
- [ ] **Cluster-wide presence.** `InMemoryPresenceStore` is per-instance and
  `@Profile("!prod")` — there appears to be **no prod (Redis-backed) `PresenceStore`**,
  so presence is wrong across instances (and the `prod` profile may have no bean).
  Move presence into shared Redis.
- [ ] **Delete dead `PresenceEventListener`.** It listens for STOMP
  `SessionConnectedEvent`/`SessionDisconnectEvent`, which never fire after the
  raw-WebSocket migration — dead code that duplicates the handler's presence path.
- [ ] **Redis relay is best-effort.** `CrossServerRelay.publish` logs on failure;
  cross-instance delivery can silently drop. Make it observable / consider durability.
- [ ] **Reconnect reconciliation for group/friend events.** These have no replay
  log; an offline user misses them. Clients must re-fetch on reconnect — or add a
  server-side sync-on-connect.
- [ ] **`sanitiseOriginalFilename` latent bug** (`MediaMessageService`):
  `substring(0, Math.min(original.length(), 255))` uses the *original* length
  against the (possibly shorter) sanitized filename → `StringIndexOutOfBounds` when
  the original contains path separators. Use the sanitized string's length.

### Tier 3 — observability (mostly inert today)
- [ ] **Wire the declared metrics.** Only `chatflow.websocket.connections` is
  emitted; `MESSAGES_SENT`, `DELIVERY_LATENCY`, `MESSAGES_REPLAYED`,
  `RELAY_*`, `GROUP_MESSAGES_SENT` are defined but never recorded — and the
  delivery-latency **histogram config in `application.yaml` is therefore inert**.
- [ ] **WebSocket heartbeat/idle config is dead.** `app.websocket.idle-timeout-seconds`
  and `ping-interval-seconds` are read by nothing; there's no server-side idle
  eviction or server-initiated ping. Wire an idle timeout, or remove the config.
- [ ] **Correlation IDs don't cover WebSocket** work (servlet-filter only).

### Tier 4 — auth & security hardening
- [ ] **Refresh tokens + logout/revocation** (single long-lived JWT today; Redis is available for a denylist).
- [ ] **Rate limiting** on `/api/auth/**` (brute-force) and possibly message send.
- [ ] **Media: local-profile signed URLs aren't actually signed** (returns the plain URL; P8 JWT signing is a follow-up). Consider virus scanning for uploads.

### Tier 5 — scale & product
- [ ] **Group fan-out is O(members)/message** (per-member loop + Redis publish);
  revisit a broadcast/topic relay for large groups.
- [ ] **Replay caps** (group 200; 1:1 replays all still-`SENT`) — add pagination so
  long-offline users don't silently miss messages.
- [ ] **Offline push notifications** (FCM/APNs) — pushes only reach connected sockets today.
- [ ] **Message edit / delete / reactions** — common chat features, absent.
- [ ] **Full-text search relevance** (Postgres `tsvector` + GIN + `ts_rank`,
  `ts_headline` snippets) — needs the migration tooling from Tier 1.

### Tier 6 — DX / consistency
- [ ] **Consolidate the three `afterCommit` implementations** (`GroupChatService`,
  `GroupDeliveryService`, and the shared `infra/tx/AfterCommit`) onto the shared one.
- [ ] **OpenAPI/Swagger** (`springdoc`) — no API docs today.
- [ ] **WebSocket protocol doc** — document the `InboundMessage`/`OutboundMessage` frame shapes.
- [ ] **Friend `DECLINED` re-send semantics** — a declined request can be re-sent by
  either party (initiator flips). Make this behavior deliberate/documented.

---

*Companion docs:* `chatflow-backend/docs/MEDIA_MESSAGES_EXECUTION.md`,
`chatflow-backend/docs/MESSAGE_SEARCH.md`, and the architecture overview in `README.md`.

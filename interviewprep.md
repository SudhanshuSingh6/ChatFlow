# ChatFlow — Interview Prep

A real-time chat backend (Spring Boot 4 / Java 21 / PostgreSQL / Redis) with 1:1 + group
messaging, presence, typing, read/delivery receipts, media, notifications, and a
transactional outbox. This doc has two parts: **(1) what we did today** (the schema
unification) and **(2) the full feature list** you can talk through in an interview.

---

# Section 1 — What We Did Today

**Headline:** collapsed two near-duplicate chat stacks (1:1 and group) into a single
unified conversation model, added a transactional outbox + in-app notifications, re-linked
media to messages, and got the app booting on a clean Flyway-managed schema.

## 1.1 The problem
The backend had two parallel implementations of the same idea:
- 1:1 chat under `com.chatflow.message.*` (`Conversation` with `participantOne/Two`, `Message`, `ChatService`, `DeliveryService`, `ReplayService`).
- Group chat under `com.chatflow.group.*` (`Group`, `GroupMember`, `GroupMessage`, `GroupChatService`, `GroupDeliveryService`, `GroupService`).

That meant duplicate entities, duplicate services, two replay paths, two sets of WebSocket
message types, and two search code paths — every feature had to be built and fixed twice.

## 1.2 The unified model
Everything now lives under `com.chatflow.conversation.*`:
- **`Conversation`** with a `type` (`DIRECT` | `GROUP`). A group is just a conversation with more participants.
- **`ConversationParticipant`** — membership for both kinds, carrying `last_read_seq` / `last_delivered_seq` watermarks and a `role` (`OWNER`/`ADMIN`/`MEMBER`).
- **`Message`** — unified for both, with `type` (`TEXT`/`MEDIA`/`SYSTEM`), per-conversation `sequence_number`, soft-delete, and `client_message_id` for idempotency.
- DM uniqueness enforced by a canonical `dm_key` = `min(a,b):max(a,b)` with a unique constraint.

## 1.3 Receipts as watermarks (the key normalization)
Old model stored per-message `status` (SENT/DELIVERED/SEEN) + `receiver_id` — only models 1:1.
New model derives everything from per-participant sequence watermarks:
- "Has X read message N?" → `participant(X).last_read_seq >= N`.
- 1:1 ticks derive from the *other* participant's watermarks (delivered/seen).
- Group receipts use the **same** logic — one code path for both.

## 1.4 Transactional outbox
New `com.chatflow.infra.outbox` package:
- Each state-changing `@Transactional` method writes an `outbox_events` row **in the same transaction** as its data change (via `OutboxWriter`).
- A `@Scheduled` `OutboxPoller` drains PENDING rows; `OutboxProcessor` handles each in its own `REQUIRES_NEW` tx using `SELECT … FOR UPDATE SKIP LOCKED` (safe under concurrent pollers / multiple instances).
- `OutboxDispatcher` routes events to side effects (notifications today).
- **Hybrid delivery:** keep the fast direct WebSocket push for live latency *and* write the outbox row for durable, at-least-once, crash-safe delivery. Clients de-dupe by `sequence_number` / `client_message_id`.

## 1.5 In-app notifications
New `com.chatflow.notification` package — persistent feed + live WS push, driven by the
outbox (not a parallel listener, so there's one durable path):
- `Notification` entity, repo, `NotificationService`, `NotificationController`.
- **Coalescing** for message bursts: many messages in one conversation collapse into one unread row with an `event_count`.
- Endpoints: feed (keyset paginated), unread-count, mark-read, mark-all-read, delete.
- Triggers: friend request / accepted, group member added/removed, role changed, ownership transferred, new message.

## 1.6 Media re-linked to messages
A media message is now a `messages` row of `type=MEDIA` **plus** a `media_messages` detail row
linked by `message_id`. Dropped the old `conversationId`/`groupId` XOR on media — access is
resolved through the parent message's conversation participants.

## 1.7 Protocol + API collapse
- **WebSocket:** collapsed `MESSAGE`/`GROUP_MESSAGE`, the `*_ACK`s and receipts into one set keyed by `conversationId`. Inbound types: `SEND_MESSAGE`, `MESSAGE_DELIVERED`, `CONVERSATION_OPEN`, `MARK_READ`, `TYPING`, `PING`.
- **REST:** unified under `/api/conversations`; group management is now sub-resources (`/direct`, `/group`, `/{id}/participants`, `/{id}/participants/{userId}/role`, `/{id}/transfer-ownership`). Old `/api/groups` removed.

## 1.8 Schema & migration (Flyway)
- `V1__init.sql` rewritten as the full unified 8-table schema; `spring.jpa.hibernate.ddl-auto=validate`.
- Dev DB wiped once (clean-slate, no backfill); `baseline-on-migrate` turned off.
- **Boot 4 gotcha fixed:** Spring Boot 4 moved `FlywayAutoConfiguration` out of `spring-boot-autoconfigure` into a dedicated `spring-boot-flyway` module. We had `flyway-core` but not that module, so migrations silently never ran and Hibernate validated against an empty DB. Adding `spring-boot-flyway` fixed it.

## 1.9 Tests & build
- Rewrote/cleaned tests against the unified model; deleted obsolete `message.*`/`group.*` tests.
- `./mvnw clean test` → **33 tests, 0 failures**. App boots clean: Flyway applies V1, Hibernate validates.
- Also removed a duplicate `spring-boot-starter-web` declaration that was emitting a POM warning.

## 1.10 Outcome
- Net deletion of an entire parallel stack; one model, one set of services, one protocol.
- New capabilities added cleanly on top: outbox durability + notifications.
- Build green, app running.

---

# Section 2 — Full Feature List

## 2.1 Authentication & Security
- JWT-based auth (`AuthController`, `JwtService`, `JwtAuthenticationFilter`), register/login.
- Spring Security filter chain; `Principal.getName()` → user `UUID` in every controller.
- JWT handshake interceptor for the WebSocket upgrade (`JwtHandshakeInterceptor`).
- Authorization checks at the service layer (participant/membership/role rules).

## 2.2 Friendships
- Request lifecycle: send / accept / decline / unfriend, with re-send handling.
- Canonical user-pair ordering + unique constraint; concurrent-insert race handled via `DataIntegrityViolationException`.
- Live WS events to the counterpart + durable notification via outbox.
- "Are friends?" check gates group membership additions.

## 2.3 Conversations (unified DIRECT + GROUP)
- Get-or-create 1:1 conversation by `dm_key` (idempotent, race-safe).
- Create group; add/remove participants; update roles; transfer ownership; delete group.
- Role rules: OWNER/ADMIN/MEMBER, with owner-can't-leave-before-transfer, admins can't remove admins, etc.
- System messages (`type=SYSTEM`) supported in the model for events.

## 2.4 Messaging
- Send text messages into any conversation (one path for DM + group).
- Per-conversation monotonic `sequence_number` allocation under a pessimistic lock.
- Idempotency via `client_message_id` (a resend returns the original).
- Denormalized `last_message_preview` / `last_message_at` / `last_message_seq` for fast conversation lists.
- Soft delete + edit support on messages.
- History paging (before/after a sequence cursor), max page size capped.

## 2.5 Delivery & Read Receipts (watermark-based)
- `MESSAGE_DELIVERED` advances `last_delivered_seq`; `MARK_READ` advances `last_read_seq` (and delivered, since read implies delivered).
- `CONVERSATION_OPEN` marks everything delivered on open.
- Monotonic cursors (never move backwards); status updates pushed to other participants.
- 1:1 ticks and group receipts share the exact same mechanism.

## 2.6 Offline Replay
- On (re)connect, re-push every message across all the user's conversations beyond their delivered watermark (excluding their own), ordered by conversation then sequence.

## 2.7 Presence
- Online/offline tracking (`InMemoryPresenceStore`), `onlineSince` timestamps.
- Broadcast presence to all "contacts" (anyone sharing a conversation — DM or group).
- REST: per-user presence (authorized if you share a conversation) and per-conversation presence.

## 2.8 Typing Indicators
- Debounced typing state with a 4s auto-expiry timer per (conversation, user).
- Broadcast to all other participants; cleared on disconnect.

## 2.9 Media Messages
- Multipart upload; a media message = a `messages` row (`type=MEDIA`) + `media_messages` detail row.
- MIME type detected from magic bytes (not trusted from client header); per-type size limits.
- Storage abstraction with **local** and **S3/MinIO** implementations; presigned/time-limited access URLs.
- Async thumbnail pipeline (Thumbnailator for images, FFmpeg for video) off the request thread; `MEDIA_THUMBNAIL_READY` pushed when done.
- Deletion with post-commit storage purge + scheduled retry for orphaned objects (`PENDING_DELETION` → `DELETED`).
- Access control via the parent message's conversation participants.

## 2.10 Notifications (in-app)
- Persistent feed (source of truth) + live WS push if connected.
- Coalescing of message bursts per conversation (`event_count`).
- Keyset-paginated feed, unread badge count, mark-read / mark-all-read / delete.
- Driven by the transactional outbox (durable, at-least-once).

## 2.11 Transactional Outbox
- `outbox_events` written in the same tx as the domain change.
- Scheduled poller + `FOR UPDATE SKIP LOCKED` + per-event `REQUIRES_NEW` processing.
- Single dispatcher/consumer replacing scattered fan-out; at-least-once guarantee.

## 2.12 Real-time Transport
- Raw WebSocket (migrated off STOMP/SockJS) with a unified inbound/outbound message envelope (`{type, requestId, payload}`).
- Per-session registry; JSON (Jackson 3) framing; payload validation via Bean Validation.
- **Cross-server relay** over Redis pub/sub (`CrossServerRelay`) so users on different instances still get pushes.

## 2.13 Message Search
- Case-insensitive substring search across all the caller's conversations (single query over the unified `messages` table).
- Keyset cursor on `(createdAt, id)`; LIKE-wildcard escaping; result carries conversation context for deep-linking.

## 2.14 Cross-cutting / Infra
- **Flyway** schema migrations; Hibernate `ddl-auto=validate` (entities must match the migration).
- **Correlation IDs** (`CorrelationIdFilter`) + structured logging (logstash encoder).
- **Metrics**: Micrometer + Prometheus, actuator endpoints, delivery-latency histogram (p50/p95/p99).
- **Async pools** (`AsyncConfig`) for media processing; scheduling enabled for outbox poller, typing timers, media cleanup.
- **`AfterCommit`** helper: WS fan-out runs only after the DB tx commits (no phantom pushes on rollback).
- Centralized REST exception handling (`RestExceptionHandler`).
- Docker / docker-compose for local Postgres + Redis (+ MinIO for S3).

---

## Likely interview talking points
- **Why unify?** Eliminating duplication: one model, one receipts mechanism, one protocol — features built once, fixed once.
- **Watermarks vs per-message status:** scales to N participants; same code for 1:1 ticks and group receipts.
- **Hybrid delivery (direct push + outbox):** snappy live latency *and* durable/crash-safe notifications + cross-instance fan-out, without a distributed transaction.
- **Idempotency & ordering:** `client_message_id` for dedupe, per-conversation `sequence_number` for ordering; clients de-dupe so at-least-once is safe.
- **Concurrency:** pessimistic locks for sequence allocation, `dm_key` unique constraint + caught violations for DM creation races, `SKIP LOCKED` for the outbox poller.
- **Spring Boot 4 modularization:** auto-config split into per-tech modules (`spring-boot-flyway`) — a subtle upgrade trap worth mentioning.

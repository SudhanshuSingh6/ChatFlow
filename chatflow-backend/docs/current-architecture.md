# ChatFlow — Current Architecture

**What this document is:** a detailed description of what is *already built* in the
ChatFlow backend as of this writing. It reflects the actual code, not a plan.

---

## 1. Tech stack

| Concern | Choice |
|---------|--------|
| Language / runtime | Java 21 |
| Framework | Spring Boot **4.0.4** (Web MVC, Data JPA, Security, WebSocket, Validation, Actuator) |
| Build | Maven (`spring-boot-starter-parent`) |
| Database | PostgreSQL (schema auto-managed by Hibernate `ddl-auto: update`) |
| Cache / pub-sub | Redis (Spring Data Redis) — used for cross-server WebSocket relay |
| Auth | JWT via `io.jsonwebtoken:jjwt` 0.12.6; BCrypt(12) password hashing |
| Realtime | **Raw** WebSocket (`spring-boot-starter-websocket`, no STOMP/SockJS) |
| Object storage | AWS SDK v2 S3 (`software.amazon.awssdk:s3`), MinIO-compatible; local-disk fallback |
| Media processing | Thumbnailator (images) + FFmpeg (video frames) |
| Observability | Micrometer + Prometheus registry; Logstash Logback JSON encoder |
| JSON | Jackson 3 (`tools.jackson.databind`) — the Boot 4 default |

> Note the migration history: a recent commit replaced STOMP/SockJS with raw
> WebSocket (`cc15a42`), so all realtime code is hand-rolled over `TextWebSocketHandler`.

---

## 2. Module map

Package root: `com.chatflow`. The codebase is organized by **feature module**, each
with its own `controller / service / repository / entity / dto` sub-packages.

```
auth/        Registration, login, JWT issuing/validation, Spring Security filter
user/        User entity + repository (username, bcrypt password)
friend/      Friend request lifecycle (PENDING → ACCEPTED/DECLINED), friend list
message/     1:1 conversations, messages, delivery/seen receipts, replay, search
group/       Group chat: groups, members/roles, group messages, delivery/read receipts
media/       Media upload, storage (S3/local), thumbnailing, access URLs, cleanup
presence/    Online/offline tracking + presence broadcast
typing/      Typing indicators with auto-expiry
infra/
  websocket/ Raw WS handler, session registry, gateway, in/out message envelopes
  redis/     Cross-server relay (pub/sub fan-out across instances)
  tx/        AfterCommit helper (post-commit side effects)
config/      Security, WebSocket, JWT handshake, metrics, async, scheduling, errors
```

---

## 3. Authentication & security

### REST auth (`SecurityConfig`)
- Stateless (`SessionCreationPolicy.STATELESS`), CSRF disabled.
- Public: `POST /api/auth/**`, the `/ws` handshake. **Everything else requires auth.**
- A `JwtAuthenticationFilter` runs before `UsernamePasswordAuthenticationFilter`,
  extracts the bearer token, validates it (`JwtService`), and populates the
  `SecurityContext`. Controllers read the caller via `Principal.getName()` → `UUID`.
- Passwords hashed with `BCryptPasswordEncoder(strength = 12)`.

### WebSocket auth (`JwtHandshakeInterceptor`)
- Token is passed as a **query param** `?token=...` on the `/ws` handshake.
- The interceptor validates it and stashes the `userId` UUID into the session
  attributes (`USER_ID_ATTR`). Invalid/missing token → handshake rejected.
- Every inbound frame re-reads `userId` from the session — there is no per-frame token.

### Tokens (`JwtService`)
- HS256 signed with `app.jwt.secret`; `app.jwt.expiration-ms = 86400000` (24h).
- Subject is the user UUID.

---

## 4. Realtime WebSocket layer (`infra/websocket`)

This is the heart of the system. Endpoint: **`/ws`** (configured in `WebSocketConfig`,
allowed origins `*`).

### Message envelopes
- **`InboundMessage`** `{ type, requestId, payload }` where `payload` is a raw
  `JsonNode`, parsed+validated per-type. Inbound `Type`s:
  `SEND_MESSAGE, MESSAGE_ACK, CONVERSATION_OPEN, CONVERSATION_SEEN,
  GROUP_SEND_MESSAGE, GROUP_READ_RECEIPT, GROUP_MESSAGE_DELIVERED, TYPING, PING`.
- **`OutboundMessage`** `{ type, requestId, payload }`. Outbound `Type`s:
  `MESSAGE, MESSAGE_ACK, STATUS_UPDATE, SEEN_UPDATE`,
  `GROUP_MESSAGE, GROUP_MESSAGE_ACK, GROUP_READ_RECEIPT, GROUP_DELIVERY_ACK`,
  `PRESENCE, TYPING, ERROR, PONG`,
  `MEDIA_MESSAGE, MEDIA_THUMBNAIL_READY`,
  `GROUP_CREATED, GROUP_MEMBER_ADDED, GROUP_MEMBER_REMOVED, GROUP_ROLE_CHANGED,
   GROUP_OWNERSHIP_TRANSFERRED, GROUP_DELETED`,
  `FRIEND_REQUEST, FRIEND_REQUEST_ACCEPTED, FRIEND_REQUEST_DECLINED, FRIEND_REMOVED`.

### `ChatWebSocketHandler` (extends `TextWebSocketHandler`)
- **On connect:** register the session; if it's the user's first session, mark them
  online (`PresenceService`). Then **replay missed messages** — `ReplayService`
  (1:1) and `GroupChatService.replayForUser` (group) re-push everything still in
  `SENT` status.
- **On message:** parse `InboundMessage`, validate the typed payload with the Bean
  Validation `Validator`, then `dispatch(...)` via a `switch` to the right service.
  `IllegalArgumentException`/`SecurityException` become an `ERROR` frame back to the
  client; unexpected exceptions become a generic "Internal server error".
- **On close / transport error:** remove the session; if it was the user's last,
  mark offline and clear typing state.
- Multi-device aware: a user can have **multiple concurrent sessions**.

### `WebSocketSessionRegistry`
- `Map<UUID, Set<WebSocketSession>>` — concurrent, multi-session per user.
- Tracks `lastActivityAt` per session id (for idle/stale detection and pings).
- Serializes frames and prunes dead sessions on send. Exposes a Micrometer gauge
  (`chatflow.websocket.connections`) for live connection count.
- Helpers: `getStaleSessionsOlderThan`, `sendPingToAll` (server-initiated heartbeat).

### `WebSocketGateway` — the fan-out façade
```
sendToUser(userId, msg):
    if locally connected → sessionRegistry.sendToUser(...)
    always → crossServerRelay.publish(...)   // reach the user on other instances
```
This is the single entry point every service uses to push to a user. It makes the
system **horizontally scalable**: a message for a user connected to a different
instance is delivered via Redis pub/sub.

### Cross-server relay (`infra/redis`)
- `CrossServerRelay` publishes a `CrossServerMessage {sourceInstanceId, targetUserId,
  payload}` to Redis channel **`chat:relay`**.
- Each instance subscribes (`RedisConfig` → `RedisMessageListenerContainer`). On
  receive, it **ignores its own messages** (by `instanceId`) and delivers to the
  target user only if that user is locally connected.
- `instanceId` defaults to a random UUID per process (`app.instance-id`).

---

## 5. 1:1 messaging (`message/`)

### Entities
- **`Conversation`** — canonical participant ordering (`participantOneId <
  participantTwoId`) enforces a unique pair index. Holds `lastMessage`,
  `lastMessageAt`, and **per-participant unread counters** (`unreadCountP1/P2`).
- **`Message`** — `clientMessageId` (unique, for idempotency), `conversationId`,
  `senderId`, `receiverId`, `content` (≤4000), `status` (`SENT/DELIVERED/SEEN`),
  and a per-conversation monotonic `sequenceNumber` (unique index on
  `(conversationId, sequenceNumber)`).

### Send flow (`ChatService.sendMessage`, `@Transactional`)
1. **Idempotency:** if `clientMessageId` already exists, re-ack to sender and return
   (ownership checked).
2. Lock the conversation (`findByIdForUpdate`), verify sender is a participant and
   the declared receiver is the real counterparty.
3. Allocate `nextSequenceNumber`, persist the message as `SENT`, update conversation
   `lastMessage`/`lastMessageAt`, bump the receiver's unread counter.
4. **After commit** (`AfterCommit.run`): push `MESSAGE_ACK` to sender and `MESSAGE`
   to receiver — never before commit, so a rollback can't leak a phantom message.

### Delivery & read receipts (`DeliveryService`)
- `MESSAGE_ACK` (client confirms receipt) → `SENT → DELIVERED`, pushes
  `STATUS_UPDATE` to the sender.
- `CONVERSATION_OPEN` → bulk `SENT → DELIVERED` for all messages the opener is the
  receiver of (locks conversation), pushes per-message status updates.
- `CONVERSATION_SEEN` → marks messages `SEEN`, pushes `SEEN_UPDATE`.
- Status transitions use guarded conditional updates (`updateStatus(... from, to)`)
  so a later state never regresses; `updated == 0` is treated as "already advanced".

### Replay (`ReplayService`)
On (re)connect, all messages still in `SENT` for the user are re-pushed in sequence
order — the offline inbox. Delivery happens through the same `MESSAGE` frame.

### REST (`ConversationController`, `/api/conversations`)
- `POST /` get-or-create a conversation (201 if new, 200 if existing).
- `GET /` list the caller's conversations.
- `GET /{id}/messages?before=&limit=` keyset history (newest-first).
- `GET /{id}/messages/after?after=` forward paging.

### Search (`MessageSearchController` + `MessageSearchService`)
- `GET /api/search/messages` — global across the caller's messages.
- `GET /api/conversations/{id}/messages/search` and
  `GET /api/groups/{id}/messages/search` — scoped.
- Cursor-based paging via `SearchCursor` / `SearchPageResponse`.

---

## 6. Group chat (`group/`)

### Entities
- **`Group`** — `name`, `createdBy`, `createdAt`.
- **`GroupMember`** — `groupId`, `userId`, `role` (`OWNER/ADMIN/MEMBER`),
  `lastReadSequenceNumber`, `lastDeliveredSequenceNumber`, `joinedAt`.
  Read/delivery watermarks are tracked per member as sequence numbers.
- **`GroupMessage`** — `clientMessageId`, `groupId`, `senderId`, `content`,
  per-group `sequenceNumber`, `createdAt`.

### Services
- **`GroupService`** — group lifecycle and membership, all with role checks and
  after-commit WebSocket fan-out to the affected members:
  - create, list, get, delete
  - add member(s), remove member, change member role, transfer ownership
  - Emits `GROUP_CREATED / GROUP_MEMBER_ADDED / GROUP_MEMBER_REMOVED /
    GROUP_ROLE_CHANGED / GROUP_OWNERSHIP_TRANSFERRED / GROUP_DELETED`.
- **`GroupChatService`** — send group message (idempotent via `clientMessageId`,
  sequence-numbered), fan-out `GROUP_MESSAGE` to all members, and
  `replayForUser` (group offline inbox based on the member's read/delivered
  watermark).
- **`GroupDeliveryService`** — `GROUP_MESSAGE_DELIVERED` and `GROUP_READ_RECEIPT`
  advance the member's delivered/read watermarks and broadcast receipts.

### REST (`GroupController`, `/api/groups`)
`POST /`, `GET /`, `GET /{id}`, `DELETE /{id}`, `POST /{id}/members`,
`DELETE /{id}/members/{userId}`, `PUT /{id}/members/{userId}/role`,
`POST /{id}/transfer-ownership`, `GET /{id}/messages`.

---

## 7. Friends (`friend/`)

- **`Friendship`** entity uses canonical pair ordering (`userOneId < userTwoId`) with
  a unique constraint `uk_friendship_pair`, plus `initiatorId` and `status`
  (`PENDING/ACCEPTED/DECLINED`). Concurrency-safe: a duplicate insert is caught via
  `DataIntegrityViolationException`.
- **`FriendService`** — send request, list received/sent/accepted, accept, decline,
  remove. Live notifications go to the *other* party after commit
  (`FRIEND_REQUEST`, `FRIEND_REQUEST_ACCEPTED`, etc.); the actor gets the REST
  response.
- **REST (`/api/friends`)** — `POST /requests`, `GET /requests/received`,
  `GET /requests/sent`, `POST /requests/{id}/accept`, `POST /requests/{id}/decline`,
  `GET /`, `DELETE /{userId}`.

---

## 8. Media (`media/`)

A multi-phase subsystem (the code references "Phase 6/7/8") for attachments.

### Entities & types
- **`MediaMessage`** — belongs to either a conversation or a group, with
  `messageType` (`IMAGE/VIDEO/AUDIO/FILE`), storage key, optional `thumbnailUrl`,
  and a `MediaStatus` lifecycle: `UPLOADING → PROCESSING → READY`, plus
  `PROCESSING_FAILED`, and the deletion tombstone states `PENDING_DELETION → DELETED`.

### Upload flow (`MediaMessageService.upload`, `@Transactional`)
1. Validate the file (`MediaValidator` — type/size limits per `application.yaml`,
   e.g. image 10 MB, video 100 MB).
2. Store bytes via `MediaStorageService` (S3 or local).
3. Persist the `MediaMessage` row.
4. Publish a `MediaProcessingEvent` carrying the raw bytes (so async work doesn't
   depend on the request-scoped multipart stream).

### Async processing pipeline
- **`ThumbnailService`** (`@Async("mediaProcessingExecutor")` + `@EventListener`) —
  generates a thumbnail (Thumbnailator for images, FFmpeg frame-grab for video;
  audio/file have none), stores it, and publishes `ThumbnailGeneratedEvent`. Never
  throws into the caller.
- **`ThumbnailEventListener`** (`@Async` + `@EventListener` + `@Transactional`) —
  saves the thumbnail URL and pushes `MEDIA_THUMBNAIL_READY` to the conversation
  participants or all group members.
- Pool is bounded (`AsyncConfig.mediaProcessingExecutor`, core 2 / max 4 / queue 50,
  `CallerRunsPolicy`) so an upload burst slows down instead of exhausting memory.

### Storage abstraction (`media/storage`)
- `MediaStorageService` interface with `S3MediaStorageService` (AWS SDK v2,
  MinIO-compatible, presigned URLs) and `LocalMediaStorageService` (disk) impls.
  `MediaKeys` builds storage keys; `S3Properties` holds bucket/endpoint config.

### Access & cleanup
- `MediaAccessService` / `MediaAccessGuard` authorize who may read a media item and
  issue time-limited access URLs (`signed-url-ttl-minutes: 60`).
- `MediaCleanupService` + `MediaStoragePurger` — a scheduled retry job purges storage
  objects for rows marked `PENDING_DELETION` after a grace period
  (`cleanup.interval-ms`, `retry-after-seconds`), then tombstones them `DELETED`.

### REST (`MediaController`, `/api/messages/media`)
- `POST /` multipart upload, `GET /{id}` metadata, `GET /{id}/url` access URL,
  `DELETE /{id}`. Static local files served via `MediaResourceConfig` at `/media`.

---

## 9. Presence & typing

### Presence (`presence/`)
- `PresenceStore` interface; the active impl is **`InMemoryPresenceStore`** — a
  `ConcurrentHashMap<UUID, Instant>` of online users + "online since" timestamps.
- `PresenceService.userConnected/Disconnected` updates the store and **broadcasts a
  `PRESENCE` event to every conversation partner** of that user.
- REST (`PresenceController`): `GET /api/users/{id}/presence`,
  `GET /api/conversations/{id}/presence`.
- Heartbeat configured at `app.presence.heartbeat-interval-ms: 30000`.

> **Scaling caveat:** presence is currently per-instance in memory. It is *not*
> shared across instances via Redis yet (unlike message fan-out), so presence is
> accurate only for users on the same node.

### Typing (`typing/`)
- `TypingStateManager` tracks who is typing in which conversation and broadcasts
  `TYPING` events; entries **auto-expire** via the `typingTimerExecutor`
  (`SchedulingConfig`) so a dropped "stopped typing" doesn't leave a stuck indicator.
  Typing state is cleared on disconnect.

---

## 10. Cross-cutting infrastructure

### `AfterCommit` (`infra/tx`)
Runs a side effect only after the current transaction commits (registers a
`TransactionSynchronization`); if no transaction is active (e.g. unit tests), runs
immediately. This is the standard pattern for WebSocket fan-out from
`@Transactional` services — **persist first, deliver after commit**.

### Async (`AsyncConfig`)
`@EnableAsync`; defines the bounded `mediaProcessingExecutor` pool described above.

### Scheduling (`SchedulingConfig`)
Provides the `typingTimerExecutor` `ScheduledExecutorService` (and supports the media
cleanup job).

### Correlation IDs (`CorrelationIdFilter`)
Reads/generates `X-Correlation-Id`, puts it in the SLF4J **MDC** so every log line
carries it; always cleared in a `finally` (safe for thread-pool reuse). Pairs with
the Logstash JSON log encoder.

### Error handling (`RestExceptionHandler`, `@RestControllerAdvice`)
Maps exceptions to RFC-7807 `ProblemDetail` responses:
`StorageException`, `MediaValidationException`, `MaxUploadSizeExceededException`,
`IllegalArgumentException` (400), `SecurityException` (403), auth exceptions (401),
and the various Bean Validation failures (`MethodArgumentNotValidException`,
`HandlerMethodValidationException`, `ConstraintViolationException`,
`HttpMessageNotReadableException`).

### Metrics (`MetricsConfig` + Actuator)
Named meters: `chatflow.websocket.connections` (gauge),
`chatflow.websocket.frames.inbound`, `chatflow.messages.sent`,
`chatflow.messages.replayed`, `chatflow.messages.delivery.latency` (timer with
p50/p95/p99 histogram), `chatflow.relay.publishes/deliveries`,
`chatflow.group.messages.sent`. Exposed via
`/actuator/{health,info,metrics,prometheus}`; common tag `app=chatflow`.

---

## 11. Configuration summary (`application.yaml`)

| Area | Key settings |
|------|--------------|
| Datasource | PostgreSQL `localhost:5432/chatflow`, creds via `DB_USERNAME/DB_PASSWORD` |
| JPA | `ddl-auto: update`, `show-sql: false` |
| Redis | host/port via `SPRING_DATA_REDIS_HOST/PORT` |
| Multipart | max file 100 MB, max request 105 MB |
| JWT | `app.jwt.secret` (env), 24h expiry |
| WebSocket | idle timeout 90s, ping interval 30s |
| Presence | heartbeat 30s |
| Media | per-type size limits, thumbnail max 320px, ffmpeg path/timeout, signed-URL TTL 60m, cleanup intervals |
| S3/MinIO | endpoint/bucket/region/keys (defaults point at local MinIO), presigned-URL expiry 60m |
| Actuator | health/info/metrics/prometheus exposed |

---

## 12. End-to-end flow examples

**Send a 1:1 message (online receiver):**
`WS SEND_MESSAGE` → `ChatWebSocketHandler.dispatch` → `ChatService.sendMessage`
(persist, seq#, unread++) → after commit → `WebSocketGateway` →
local push **and** Redis relay → receiver gets `MESSAGE`; sender gets `MESSAGE_ACK`.

**Send a 1:1 message (offline receiver):**
Same persist path; the after-commit push is a no-op locally. When the receiver
reconnects, `ReplayService` re-pushes all `SENT` messages in order.

**Upload an image:**
`POST /api/messages/media` → validate + store + persist (`PROCESSING`) →
`MediaProcessingEvent` → `ThumbnailService` (async) makes a thumbnail →
`ThumbnailGeneratedEvent` → `ThumbnailEventListener` saves URL and pushes
`MEDIA_THUMBNAIL_READY` to the chat's recipients.

---

## 13. Testing

JUnit/Spring test coverage exists for the higher-risk logic:
`FriendServiceTest`, `GroupServiceTest`, `AfterCommitTest`, `MessageSearchServiceTest`,
`SearchCursorTest`, and the media suite (`ThumbnailServiceTest`,
`MediaAccessGuardTest`, `MediaAccessServiceTest`, `MediaCleanupServiceTest`,
`MediaStoragePurgerTest`, `S3ConfigTest`, `S3MediaStorageServiceTest`).

---

## 14. Known gaps / not yet built

- **No notification system** — "notify" today means a live WebSocket push only;
  there is no persistent notification feed and no push/email/SMS. (See the separate
  `notification-feature.md` plan.)
- **Presence is per-instance** (in-memory), not Redis-backed — unlike message
  fan-out, it does not span instances.
- **Schema via `ddl-auto: update`** — no Flyway/Liquibase migrations.
- Default secrets in `application.yaml` (JWT, MinIO) are dev placeholders and must be
  overridden in production.

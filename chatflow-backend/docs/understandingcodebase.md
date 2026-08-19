# Understanding the ChatFlow Backend — A Guided Walkthrough

A learning path through the codebase. Read top to bottom: each section builds on the last,
and within a section the files are listed in the order that makes them easiest to understand.
For each file you get **what it is** and **what to notice**, so you can open the file and read
it with a purpose.

> **Mental model in one paragraph.** ChatFlow is a Spring Boot 4 chat backend. Clients
> authenticate with a JWT, then talk over two channels: a **REST API** (`/api/...`) for
> request/response actions and history, and a **WebSocket** (`/ws`) for real-time send/receive,
> typing, presence, and live notifications. Everything — DMs and group chats — is one unified
> model (`Conversation` + `Message` + `ConversationParticipant`). Side effects that must be
> reliable (notifications, embeddings) go through a **transactional outbox** drained by a
> poller. Postgres (via Flyway + JPA) is the source of truth; Redis fans messages across
> server instances.

---

## 0. Orientation — read these first

| File | What it is | What to notice |
|---|---|---|
| `ChatflowBackendApplication.java` | The entrypoint (`main`). | Tiny — all wiring is via Spring annotations/component scan. |
| `src/main/resources/application.yaml` | All config: JWT, websocket, media, S3, AI, cleanup. | Skim it once — it's the index of every tunable knob and every feature that exists. |
| `pom.xml` | Dependencies. | Spring Web/JPA/Security/WebSocket/Redis, Flyway, JJWT, AWS S3, Anthropic SDK. The dep list tells you the surface area. |
| `db/migration/V1__init.sql` | The **entire schema** as one Flyway migration. | This is the best map of the domain — every table and index. `V2` adds soft-delete columns, `V3` adds AI embeddings. Schema is owned by Flyway; JPA only *validates* against it. |

**How a project like this fits together (layers):** `Controller` (HTTP) → `Service` (business logic + `@Transactional`) → `Repository` (DB) → `Entity` (table row). DTOs (records) carry data in/out of controllers so entities never leak to the wire. Keep this 4-layer shape in mind for every feature below.

---

## 1. Cross-cutting infrastructure (`config/`, `infra/`)

Understand these once; every feature uses them.

### Security & request plumbing (`config/`)
| File | What it is | What to notice |
|---|---|---|
| `SecurityConfig.java` | Spring Security setup: which routes are public, JWT filter chain, stateless sessions. | `/api/auth/**` is open; everything else needs a valid JWT. |
| `auth/security/JwtAuthenticationFilter.java` | Runs per request, reads the `Authorization: Bearer` token, sets the authenticated user. | After this filter, `Principal.getName()` is the user's UUID — that's how every controller knows "who is calling." |
| `auth/security/JwtService.java` | Signs/verifies JWTs. | Token = identity. |
| `CorrelationIdFilter.java` | Stamps each request with a correlation id for logging. | Why logs can be traced across a request. |
| `RestExceptionHandler.java` | `@RestControllerAdvice` mapping exceptions → HTTP status. | **Important convention:** `IllegalArgumentException` → 400, `SecurityException` → 403, auth errors → 401. Services throw these plain exceptions; this class turns them into responses. |
| `WebSocketConfig.java` + `JwtHandshakeInterceptor.java` | Registers the `/ws` endpoint and authenticates the WebSocket handshake with the JWT. | WebSocket auth happens once, at connect. |
| `AsyncConfig.java`, `SchedulingConfig.java`, `MetricsConfig.java` | Enable `@Async`, `@Scheduled`, and Micrometer metrics. | `@EnableScheduling` here is what makes the outbox poller, media cleanup, and daily cleanup jobs run. |

### Real-time transport (`infra/websocket/`)
Read in this order:
1. `InboundMessage.java` — the frames a client **sends** (`SEND_MESSAGE`, `MESSAGE_DELIVERED`, `MARK_READ`, `TYPING`). One enum for DM + group.
2. `OutboundMessage.java` — the frames the server **pushes** (`MESSAGE`, `MESSAGE_ACK`, `TYPING`, `PRESENCE`, `NOTIFICATION`, `GROUP_*`, …). This enum is the catalog of everything that can happen live.
3. `WebSocketSessionRegistry.java` — maps `userId → open sessions` (a user can be on multiple devices).
4. `ChatWebSocketHandler.java` — receives inbound frames, routes them to services. The real-time entrypoint (mirror of a controller, but for sockets).
5. `WebSocketGateway.java` — the **send side**: `sendToUser` / `sendToUsers`. Services call this to push live updates. Also where cross-server relay hooks in.

### Reliable side effects (`infra/outbox/`, `infra/tx/`, `infra/redis/`)
This is the most important pattern in the codebase — study it carefully.
| File | What it is | What to notice |
|---|---|---|
| `infra/tx/AfterCommit.java` | Helper to run code *after* the DB transaction commits. | Used so a WebSocket push never fires for a message that then rolls back. |
| `outbox/OutboxEvent.java` | A row in `outbox_events`: `aggregateType`, `aggregateId`, `eventType`, JSON `payload`, `status`. | The durable "I owe a side effect" record. |
| `outbox/OutboxWriter.java` | Writes an outbox row **inside the caller's transaction**. | Atomicity: the business change and the event commit together or not at all. |
| `outbox/OutboxPoller.java` | `@Scheduled` (every ~1s) — finds `PENDING` events, hands each to the processor. | At-least-once delivery; failures stay `PENDING` and retry. |
| `outbox/OutboxProcessor.java` | Processes **one event in its own `REQUIRES_NEW` tx**, locked with `SKIP LOCKED`. | One poisoned event can't roll back the batch; multiple servers won't double-process. |
| `outbox/OutboxDispatcher.java` | The single consumer — routes each `eventType` to its effect (notifications, embeddings). | The `switch` here is the list of everything the outbox can trigger. |
| `redis/CrossServerRelay.java` + `RedisConfig.java` | Pub/sub so a message sent on server A reaches a user connected to server B. | Why the app scales horizontally. |

> **Takeaway:** "write a row in my transaction, let a poller deliver it reliably later" is how this app decouples *doing the thing* from *announcing the thing*. Notifications and embeddings both ride this rail.

---

## 2. Auth — the simplest end-to-end feature (`auth/`, `user/`)

Start your feature reading here because the flow is short and you'll reuse the pattern.

Reading order: `user/entity/User.java` → `user/repository/UserRepository.java` → `auth/dto/*` (`RegisterRequest`, `LoginRequest`, `AuthResponse`) → `auth/service/AuthService.java` → `auth/controller/AuthController.java` → `auth/security/CustomUserDetailsService.java`.

- **Flow:** `POST /api/auth/register` and `POST /api/auth/login` → `AuthService` verifies/creates the user, hashes the password, and returns a signed JWT in `AuthResponse`.
- **What to notice:** how the controller delegates to the service, how the service throws `IllegalArgumentException` for bad input (→ 400), and how the JWT it returns is what every later request carries.

---

## 3. The core domain — conversations & messaging (`conversation/`)

This is the heart of the app. Everything (DM and group) is one model. Read in this order.

### Entities (the tables)
| File | What it is | What to notice |
|---|---|---|
| `entity/Conversation.java` | A chat. `type = DIRECT | GROUP`. | `dmKey` enforces one DM per pair; `deletedAt` is soft-delete (groups only). Denormalized `lastMessage*` fields make the conversation list cheap to render. |
| `entity/ConversationParticipant.java` | Membership row: who's in a conversation, their `role`, and their **read/delivery watermarks** (`lastReadSeq`, `lastDeliveredSeq`). | **Key idea:** there is no per-message "read" flag. Read/delivery state is a *watermark per participant*. This one design serves both DM ticks and group receipts with the same code. |
| `entity/Message.java` | A message. `sequenceNumber` orders it within a conversation. | `deletedAt` tombstone + `softDelete()`. No `receiverId`/`status` — see watermarks above. |
| `entity/MessageType.java`, `ConversationType.java`, `ParticipantRole.java` | Enums. | Quick read — the vocabulary. |

### Repositories (the queries)
- `repository/ConversationRepository.java` — `findAllForUser` (the conversation list, filters `deletedAt IS NULL`), `findByIdForUpdate` (pessimistic lock), `findGroupIdsToPurge`.
- `repository/MessageRepository.java` — sequence allocation (`nextSequenceNumber`), history paging (`findPageBefore`/`findPageAfter`), unread counts, full-text-ish `LIKE` search, and `purgeDeletedBefore`. **Notice the JPQL** — this is where most of the data access logic lives.
- `repository/ConversationParticipantRepository.java` — membership checks, watermark advances (`advanceReadCursor`, `advanceDeliveryCursor` — monotonic), fan-out locking.

### Services (the logic) — read these closely
| File | What it does | What to notice |
|---|---|---|
| `service/ChatService.java` | **Sending a message.** Allocates a sequence number, saves the message, advances the sender's own read watermark, writes the notification + embedding outbox events (same tx), and pushes live via `AfterCommit`. | This single method is the spine of the app — read it line by line. Note idempotency via `clientMessageId`. |
| `service/DeliveryService.java` | Handles delivery/read acks → advances watermarks, pushes receipts. | Watermarks in action. |
| `service/ReplayService.java` | When a user reconnects, replays messages they missed (beyond their delivered watermark). | Offline inbox. |
| `service/ConversationService.java` | Create DM/group, add/remove participants, change roles, transfer ownership, delete (soft) group. | Group lifecycle + authorization (`requireMembership`, `requireOwner`). |
| `service/MessageSearchService.java` | Keyword (`/search`) + hybrid semantic (`/search/hybrid`) search. | The hybrid path merges keyword + vector with Reciprocal Rank Fusion — ties into the AI section. |
| `service/SearchCursor.java` | Encodes/decodes the pagination cursor. | Why search is stable across pages. |

### Controllers + DTOs (the API surface)
- `controller/ConversationController.java` — `/api/conversations/**` (create, list, history, summary, ask, group management).
- `controller/MessageSearchController.java` — `/api/messages/search` and `/search/hybrid`.
- `dto/*` — one record per request/response shape. Skim names; open one (e.g. `MessageResponse`, `ConversationResponse`) to see the entity→DTO mapping.

> **Do this:** trace one message end to end — `ChatWebSocketHandler` (SEND_MESSAGE) → `ChatService.send` → outbox write → `OutboxDispatcher` → `NotificationService`, and the `AfterCommit` push back out via `WebSocketGateway`. If you understand this loop, you understand the app.

---

## 4. Friends (`friend/`)

A self-contained feature, good for reinforcing the layered pattern.
Reading order: `entity/Friendship.java` + `FriendshipStatus.java` → `repository/FriendshipRepository.java` → `service/FriendService.java` → `controller/FriendController.java` → `dto/*`.
- **Flow:** send request → accept/decline → list friends; `/api/friends/**`.
- **What to notice:** friendship has a lifecycle (PENDING → ACCEPTED), and sending a request writes a notification via the outbox. Group membership later checks friendship.

---

## 5. Notifications (`notification/`)

Now that you know the outbox, this clicks into place.
Reading order: `entity/Notification.java` (+ `NotificationType`, `ReferenceType`) → `event/NotificationCommand.java` → `repository/NotificationRepository.java` → `service/NotificationService.java` → `controller/NotificationController.java` → `dto/*`.
- **Flow:** business events write outbox rows → `OutboxDispatcher` calls `NotificationService.createAndPush` → persists a row (coalescing repeated events) and pushes a live `NOTIFICATION` frame. REST `/api/notifications` reads the feed/unread-count and marks read/dismissed.
- **What to notice:** **coalescing** (50 messages → one notification with `eventCount`), the read/unread watermark on the badge, and soft-delete on dismiss (`deletedAt`, purged later).

---

## 6. Presence & typing (`presence/`, `typing/`)

Lightweight, mostly in-memory, real-time only.
- `presence/` — who's online. `PresenceStore` (interface) + `InMemoryPresenceStore`, `PresenceService`, `PresenceEventListener` (reacts to WS connect/disconnect), `PresenceController` (`/api/users/{id}/presence`, `/api/conversations/{id}/presence`).
- `typing/` — "X is typing." `TypingStateManager` debounces typing state; pushed over WS only (no DB).
- **What to notice:** not everything needs a database. Ephemeral state lives in memory and is broadcast live.

---

## 7. Media (`media/`)

The most elaborate feature — a two-phase upload with async processing and storage cleanup. Read last among features.
| Sub-area | Files | What to notice |
|---|---|---|
| Entity | `entity/MediaMessage.java`, `MediaStatus.java` | A media message is a `messages` row of `type=MEDIA` **plus** a `media_messages` detail row. Status machine: `READY` → `PENDING_DELETION` → purged. |
| Upload/serve | `controller/MediaController.java` (`/api/messages/media`), `service/MediaMessageService.java`, `service/MediaAccessService.java`, `MediaAccessGuard.java` | Upload returns a message; access is authorization-checked; signed/served URLs. |
| Storage | `storage/MediaStorageService.java` (interface), `LocalMediaStorageService`, `S3MediaStorageService`, `S3Config`, `S3Properties` | Pluggable backend — local disk in dev, S3/MinIO in prod (profile-gated). |
| Processing | `processing/ThumbnailService.java`, `ThumbnailEventListener.java`, events | Thumbnails generated **async** after upload (Spring events), then a `MEDIA_THUMBNAIL_READY` WS push. |
| Cleanup | `service/MediaCleanupService.java`, `MediaStoragePurger.java` | **Study this** — it's the template the AI/daily-cleanup jobs copied: logical delete in-tx, storage purge after commit, `@Scheduled` retry for crash safety. |
| Validation | `validation/MediaValidator.java`, `MediaValidationConfig.java` | Size/type limits per media kind. |

---

## 8. The AI features (`ai/`)

Newest layer. Read `docs/aifeatures.md` alongside for the design narrative. Two packages.

### Embeddings (`ai/embedding/`) — semantic search backbone
Reading order:
1. `EmbeddingService.java` (interface) + `EmbeddingResult.java` — provider-agnostic "text → vector."
2. `OpenAiCompatibleEmbeddingService.java` + `EmbeddingProperties.java` — the impl (any OpenAI-compatible endpoint via `base-url`).
3. `EmbeddingRequested.java` — the outbox payload. **Notice:** `ChatService` writes a `MESSAGE_EMBEDDING_REQUESTED` outbox event in the *same* tx as the message; no event spawns another event.
4. `MessageEmbeddingWorker.java` — the `OutboxDispatcher` branch that embeds a message and stores it.
5. `MessageEmbeddingRepository.java` — `JdbcTemplate` + native SQL (pgvector has no JPA mapping). Holds upsert, the cosine-similarity search (`<=>`), backfill query, and the FK-safe delete-before-purge methods.
6. `VectorSearchHit.java` — `(messageId, similarity)`.
7. `EmbeddingBackfillService.java` — `@Scheduled` job that enqueues embedding events for pre-existing messages; idempotent and self-terminating.

### Chat completion (`ai/chat/`) — summarizer & RAG
- `ChatCompletionService.java` (interface) — `complete(systemInstruction, cacheableContext, question)`. Provider-agnostic.
- `AnthropicChatCompletionService.java` + `ChatProperties.java` — Anthropic SDK impl with **prompt caching** (the transcript is the cached prefix); lazy client so the app boots without an API key.

### Where AI plugs into the domain
- `conversation/service/MessageSearchService.java` — hybrid search (keyword + vector, RRF).
- `conversation/service/ConversationSummaryService.java` — `/summary`: "catch me up" using the caller's `lastReadSeq` watermark.
- `conversation/service/ConversationRagService.java` — `/ask`: embed question → top-k in this conversation → grounded answer **with citations**.

---

## 9. The scheduled jobs (the "background" of the app)

Worth collecting in your head — these are the `@Scheduled` loops:
| Job | File | Cadence | Purpose |
|---|---|---|---|
| Outbox poller | `infra/outbox/OutboxPoller` | ~1s | Deliver notifications/embeddings reliably. |
| Media cleanup | `media/service/MediaCleanupService` | ~5min | Retry storage purges. |
| Embedding backfill | `ai/embedding/EmbeddingBackfillService` | ~1min | Embed historical messages. |
| Daily retention cleanup | `conversation/service/DailyCleanupService` | daily | Hard-delete soft-deleted messages/groups/notifications (+ their embeddings) past retention. |

---

## 10. Full REST endpoint reference (where to set a breakpoint)

| Method & path | Controller | Does |
|---|---|---|
| `POST /api/auth/register`, `/login` | AuthController | Account + JWT |
| `GET/POST /api/friends/**` | FriendController | Friend requests & list |
| `POST /api/conversations/direct`, `/group` | ConversationController | Create chat |
| `GET /api/conversations` | " | List my chats |
| `GET /api/conversations/{id}` | " | One chat |
| `GET /api/conversations/{id}/messages[/after]` | " | History paging |
| `POST /api/conversations/{id}/summary` | " | AI "catch me up" |
| `POST /api/conversations/{id}/ask` | " | AI RAG over history |
| `DELETE /api/conversations/{id}` | " | Soft-delete group |
| `POST/DELETE/PUT /api/conversations/{id}/participants[...]` | " | Group membership/roles |
| `POST /api/conversations/{id}/transfer-ownership` | " | Hand over a group |
| `GET /api/messages/search`, `/search/hybrid` | MessageSearchController | Keyword / hybrid search |
| `POST/GET/DELETE /api/messages/media/**` | MediaController | Upload / fetch / delete media |
| `GET /api/notifications`, `/unread-count`; `POST /{id}/read`, `/read-all`; `DELETE /{id}` | NotificationController | Notification feed |
| `GET /api/users/{id}/presence`, `/api/conversations/{id}/presence` | PresenceController | Online status |
| WebSocket `/ws` | ChatWebSocketHandler | Real-time send/receive, typing, receipts |

---

## Suggested first session (90 minutes)
1. Read §0 + skim `V1__init.sql` and `application.yaml` (15 min).
2. Read §1 infra — focus on the **outbox trio** (Writer/Poller/Processor) and `WebSocketGateway` (25 min).
3. Read §2 auth end-to-end (10 min).
4. Read §3 and **trace one message** through `ChatService` and the outbox (30 min).
5. Skim §5 notifications to see the outbox pay off (10 min).

After that, the remaining features (friends, presence, media, AI) are variations on the same four-layer + outbox patterns you've already seen.

---

## Appendix A — Sequence diagrams for the two core flows

### A.1 Sending a message (WebSocket + outbox + fan-out)

```
Client A        ChatWebSocketHandler     ChatService            Postgres (one tx)        OutboxPoller / Processor / Dispatcher     WebSocketGateway        Client B
  |  SEND_MESSAGE     |                       |                       |                                |                                  |                   |
  |------------------>|                       |                       |                                |                                  |                   |
  |                   |   send(senderId,...)  |                       |                                |                                  |                   |
  |                   |---------------------->|                       |                                |                                  |                   |
  |                   |                       | nextSequenceNumber()  |                                |                                  |                   |
  |                   |                       |---------------------->|                                |                                  |                   |
  |                   |                       | save(Message)         |                                |                                  |                   |
  |                   |                       |---------------------->|                                |                                  |                   |
  |                   |                       | advanceReadCursor(self)|                               |                                  |                   |
  |                   |                       |---------------------->|                                |                                  |                   |
  |                   |                       | outbox.write(MESSAGE_CREATED)  + (MESSAGE_EMBEDDING_REQUESTED)                            |                   |
  |                   |                       |---------------------->|  [COMMIT — message + outbox rows atomic]                          |                   |
  |                   |                       |                       |                                |                                  |                   |
  |                   |                       | AfterCommit: push     |                                |                                  |                   |
  |                   |                       |-------------------------------------------------------------------------->| MESSAGE_ACK ->|  (back to A)
  |                   |                       |-------------------------------------------------------------------------->| MESSAGE ----->|------------------>| (live, best-effort)
  |                   |                       |                       |                                |                                  |                   |
  |                   |                       |                       |   ~1s later: poll PENDING      |                                  |                   |
  |                   |                       |                       |<-------------------------------| (REQUIRES_NEW tx, SKIP LOCKED)   |                   |
  |                   |                       |                       |  dispatch MESSAGE_CREATED -> NotificationService.createAndPush    |                   |
  |                   |                       |                       |  dispatch MESSAGE_EMBEDDING_REQUESTED -> embed + store vector      |                   |
  |                   |                       |                       |                                |  push NOTIFICATION ------------->|------------------>| (durable path)
```

Two delivery rails: the **live push** via `AfterCommit` (fast, best-effort) and the **durable outbox** (at-least-once; survives a crash). Cross-instance delivery (Client B on another server) goes through `CrossServerRelay` (Redis Pub/Sub) inside `WebSocketGateway`.

### A.2 RAG "ask your chat history" (`POST /api/conversations/{id}/ask`)

```
Client     ConversationController   ConversationRagService   ParticipantRepo   EmbeddingService   MessageEmbeddingRepo (pgvector)   MessageRepo   ChatCompletionService (LLM)
  | POST /ask {question}  |               |                       |                 |                       |                          |                   |
  |---------------------->|               |                       |                 |                       |                          |                   |
  |                       | ask(caller,id,q)|                     |                 |                       |                          |                   |
  |                       |-------------->|  validate q (>=2 chars) |                 |                       |                          |                   |
  |                       |               | existsBy...UserId? (403 if not a member)|                       |                          |                   |
  |                       |               |---------------------->|                 |                       |                          |                   |
  |                       |               | embed(question)        |                |                       |                          |                   |
  |                       |               |--------------------------------------->|                       |                          |                   |
  |                       |               | searchByVectorInConversation(id, vec, k=10)  [ORDER BY embedding <=> q, HNSW]              |                   |
  |                       |               |------------------------------------------------------------->|                          |                   |
  |                       |               |   (top-k VectorSearchHit: messageId + similarity)            |                          |                   |
  |                       |               | findAllById(hit ids)   |                |                       |                          |                   |
  |                       |               |------------------------------------------------------------------------------------->|                   |
  |                       |               | build context: "[<msgId>] name: text\n..."  +  citations[]    |                          |                   |
  |                       |               | complete(SYSTEM, context=cached prefix, question)             |                          |                   |
  |                       |               |--------------------------------------------------------------------------------------------------->|
  |                       |               |   grounded answer (cite ids; "I don't know" if absent)        |                          |                   |
  |                       |  AskResponse{answer, citations[]} |   |                 |                       |                          |                   |
  |<----------------------|<--------------|                       |                 |                       |                          |                   |
```

If `embed()` or retrieval returns nothing, the service short-circuits with a friendly message and never calls the LLM. The retrieved messages become both the **grounding context** (prefixed with ids so the model can cite them) and the returned **citations**.

---

## Appendix B — Glossary

- **Watermark** — a per-participant sequence cursor on `ConversationParticipant`: `lastReadSeq` (read up to here) and `lastDeliveredSeq` (delivered up to here). Read/delivery state is derived from these cursors instead of a per-message flag, so 1:1 ticks and group receipts share one code path. Advances are *monotonic* (never move backward).

- **Sequence number** — `Message.sequenceNumber`, a per-conversation monotonically increasing counter (`uk_message_sequence` unique on `(conversation_id, sequence_number)`). Orders history and is what watermarks point at.

- **Transactional Outbox** — instead of doing a side effect (notification, embedding) inline, you write an `outbox_events` row **inside the same DB transaction** as the state change. A `@Scheduled` poller drains it later. Guarantees the side effect is owed iff the business change committed — no phantom effects, no lost effects (**at-least-once**).

- **`AfterCommit`** — a helper that defers an action until the current transaction commits. Used for the live WebSocket push, so a rolled-back message is never broadcast. (Complement to the outbox: fast/best-effort vs durable/reliable.)

- **`SKIP LOCKED`** — a Postgres row-locking clause the outbox processor uses so concurrent pollers (or multiple app instances) each grab *different* pending rows instead of blocking or double-processing the same one.

- **Coalescing** — collapsing repeated notification events for the same `(recipient, reference, type)` into a single row with an `eventCount`, so "50 new messages in one chat" is one feed item, not fifty.

- **Tombstone (soft delete)** — marking a row deleted with a `deletedAt` timestamp instead of physically removing it. Read queries filter `deletedAt IS NULL`; a daily job hard-deletes tombstones older than the retention window. Used for messages, groups, and notifications.

- **Idempotency key** — `Message.clientMessageId`, a client-supplied id unique per `(conversation, clientMessageId)`. A retried send returns the existing message instead of creating a duplicate.

- **pgvector / HNSW / `<=>`** — the Postgres extension storing embeddings as a `vector` type; HNSW is its approximate-nearest-neighbour index; `<=>` is the cosine-distance operator. `similarity = 1 - distance`. Search must `ORDER BY embedding <=> :q` (the raw operator) for the index to be used.

- **Embedding** — a numeric vector representing a message's meaning. Similar meanings sit near each other, so semantic search = "find the nearest vectors to the query vector."

- **RRF (Reciprocal Rank Fusion)** — how hybrid search merges keyword and vector result lists: `rankScore = Σ 1/(60 + rank)` across both lists. Fuses by *rank*, so the two sources' incomparable score scales never need normalizing.

- **RAG (Retrieval-Augmented Generation)** — answer a question by first *retrieving* the most relevant real messages (pgvector), then having the LLM *generate* an answer grounded only in those messages, with citations — instead of relying on the model's own memory.

- **Prompt caching** — reusing a large stable prompt prefix (here, the conversation transcript) across LLM calls at ~0.1× cost. The volatile question goes after the cached breakpoint, so follow-up asks over the same context are cheap.

- **Provider-agnostic LLM layer** — `ChatCompletionService` / `EmbeddingService` are interfaces; the Anthropic / OpenAI-compatible classes are swappable implementations selected by config, so callers never depend on a specific vendor.

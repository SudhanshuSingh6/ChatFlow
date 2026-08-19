# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Layout

Two top-level directories, developed together but run independently:

- `chatflow-backend/` — Java 21 / Spring Boot 4 Maven multi-module project
- `chatflow-frontend/` — React 19 / Vite / TypeScript SPA

---

## Backend

### Modules

| Module | Purpose | Default port |
|---|---|---|
| `chatflow-contracts` | Shared DTOs and Kafka event records (no Spring, library only) | — |
| `chatflow-storage` | S3/local storage abstraction (library only) | — |
| `chatflow-core` | Main chat service: REST, WebSocket, outbox, notifications | 8080 |
| `chatflow-ai` | RAG / embeddings / conversation summary; owns its own Postgres | 8081 |
| `chatflow-media` | Kafka-driven media processing (thumbnails, S3 writes) | 8082 |
| `chatflow-realtime` | Dedicated WebSocket service; delegates commands to core via `/internal` | 8083 |
| `chatflow-gateway` | Spring Cloud Gateway — public entry point, JWT edge auth | 8088 |

### Commands (run from `chatflow-backend/`)

```bash
# Start all infrastructure (Postgres, Postgres-AI, Redis, Kafka, MinIO, Jaeger, Grafana)
docker compose up -d

# Start infra + all app containers
docker compose --profile apps up --build

# Run only core service locally (install siblings first on first run)
./mvnw clean install -DskipTests && ./mvnw spring-boot:run -pl chatflow-core

# Run a specific module's tests
./mvnw test -pl chatflow-core

# Run all tests (Postgres container must be running)
./mvnw test

# Build all JARs
./mvnw clean package
```

### Infrastructure ports (local dev)

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

### Key configuration toggles

| Env var | Values | Effect |
|---|---|---|
| `APP_OUTBOX_TRANSPORT` | `in-process` (default) / `kafka` | In-process dispatches outbox events in-JVM; `kafka` publishes to `chatflow.outbox.events` and all consumers pick them up independently |
| `APP_REALTIME_MODE` | `embedded` (default) / `external` | `embedded`: core handles `/ws` directly on :8080; `external`: `chatflow-realtime` handles WebSockets and calls core's `/internal/realtime/*` |
| `SPRING_PROFILES_ACTIVE=s3` | — | Switches media storage from local disk to MinIO/S3 |

### Core architecture

**Conversation model**: DIRECT and GROUP chats share the same `Conversation` entity and message pipeline. Membership size is the only difference.

**Read/delivery receipts**: participant-level cursor columns (`last_read_seq`, `last_delivered_seq`), not per-message flags. "Has user X read message N?" = `participant.last_read_seq >= message.sequence_number`.

**Transactional outbox**: `ChatService` and `MediaMessageService` write `OutboxEvent` rows in the same transaction as the message insert. `OutboxPoller` picks them up and fans out notifications. At-least-once delivery guaranteed.

**WebSocket fanout**: `WebSocketGateway` attempts local delivery first, then publishes to the Redis `chat:relay` pub/sub channel. Every instance subscribes; `sourceInstanceId` prevents self-delivery.

**Internal service auth**: service-to-service calls use a shared `INTERNAL_TOKEN` header validated by `SecretsGuard`. Not routed through the gateway.

**AI service**: its own Postgres (`chatflow_ai`) with the `pgvector` extension. Consumes outbox events with consumer group `chatflow-ai-embedding`. Uses OpenAI-compatible embeddings + Anthropic Claude for RAG completions.

### Flyway migrations

Schema owned by Flyway; Hibernate runs in `validate` mode. The dev database must be fresh (drop and recreate) when V1 changes.

| Version | Description |
|---|---|
| V1 | Full baseline — unified conversation model |
| V2 | Soft-delete columns |
| V3 | `message_embeddings` (later dropped) |
| V4 | Drop `message_embeddings` from core |
| V5 | `processed_events` idempotency table |

---

## Frontend

### Commands (run from `chatflow-frontend/`)

```bash
npm install
npm run dev      # Vite dev server with proxy
npm run build    # tsc + Vite production build
npm run lint     # ESLint
```

### Dev proxy

The Vite dev server proxies requests defined in `vite.config.ts`:

| Path | Default target |
|---|---|
| `/api/**` | `http://localhost:8088` (gateway) |
| `/ai/**` | `http://localhost:8088` (gateway) |
| `/ws` | `ws://localhost:8083` (realtime) |

**Important**: when running backend in the default `embedded` mode (`APP_REALTIME_MODE=embedded`), WebSocket is served by core on :8080, not the realtime service. Override via `chatflow-frontend/.env.local`:
```
VITE_WS_URL=ws://localhost:8080
```

### State management

| Store | File | Persisted | Owns |
|---|---|---|---|
| `authStore` | `src/store/authStore.ts` | `localStorage` | JWT token, `{ userId, username }` |
| `messageStore` | `src/store/messageStore.ts` | no | Live messages, optimistic sends, delivered/read watermarks |
| `presenceStore` | `src/store/presenceStore.ts` | no | Online map `userId → boolean` |
| `typingStore` | `src/store/typingStore.ts` | no | Typing users per conversation |
| `wsStore` | `src/store/wsStore.ts` | no | WebSocket connection status |

Auth state lives in Zustand (not React context) so the Axios interceptor and WS client can read the token synchronously outside of React.

`messageStore.updateMessage(messageId, conversationId, patch)` patches fields on an existing live message — used by `MEDIA_THUMBNAIL_READY` frames to add `thumbnailUrl` without re-fetching.

### React Query

All REST fetches use TanStack Query v5. Query keys are centralised in `src/config/queryKeys.ts`:

```ts
me, conversations, conversation(id), messages(id),
conversationPresence(id), friends, friendRequests(box),
userPresence(userId), userSearch(q),
notifications, notificationUnreadCount,
messageSearch(id, q)
```

### API layer

`src/lib/api/client.ts` — Axios instance that attaches the JWT and handles 401 expiry. Backend uses RFC-7807 `ProblemDetail`; `getErrorMessage()` extracts `detail` or `message`.

API functions are split by domain:

| File | Functions |
|---|---|
| `src/lib/api/conversations.ts` | `listConversations`, `getConversation`, `getMessages`, `getMessagesAfter`, `searchMessages`, `createDirect`, `createGroup`, `deleteGroup`, `addParticipant`, `removeParticipant`, `updateMemberRole`, `transferOwnership` |
| `src/lib/api/notifications.ts` | `getNotifications`, `getUnreadCount`, `markNotificationRead`, `markAllNotificationsRead`, `deleteNotification` |
| `src/lib/api/media.ts` | `uploadMedia` (multipart, with `onProgress` callback), `getMediaUrl` |
| `src/lib/api/users.ts` | `getMe`, `searchUsers` |
| `src/lib/api/friends.ts` | friend request CRUD |
| `src/lib/api/presence.ts` | `getConversationPresence` |

### Hooks

| File | Hooks |
|---|---|
| `src/hooks/useMe.ts` | `useMe()` — fetches `/api/users/me`, syncs to authStore via AuthProvider |
| `src/hooks/useConversations.ts` | `useConversations`, `useConversation`, `useMessages`, `useConversationPresence`, `useCreateDirect`, `useCreateGroup`, `useDeleteGroup`, `useAddParticipant`, `useRemoveParticipant`, `useUpdateRole`, `useTransferOwnership` |
| `src/hooks/useNotifications.ts` | `useNotifications` (infinite), `useNotificationUnreadCount`, `useMarkNotificationRead`, `useMarkAllRead`, `useDeleteNotification` |
| `src/hooks/useMessageSearch.ts` | `useMessageSearch(conversationId, query)` — debounced 300 ms, enabled when query ≥ 2 chars |
| `src/hooks/useUserSearch.ts` | `useUserSearch(query)` — debounced people-picker |
| `src/hooks/useChat.ts` | Merges REST history + live Zustand messages, sends messages via WebSocket |

### WebSocket client

`src/lib/ws/WebSocketClient.ts` manages the socket lifecycle (auto-reconnect, exponential backoff, PING/PONG every 25 s).

`src/app/provider/WebSocketProvider.tsx` mounts the client, calls `dispatchFrame` for inbound frames, and performs **post-reconnect catch-up**: on every reconnect after the first connect, it calls `getMessagesAfter` for each conversation that has live messages cached.

`src/lib/ws/dispatcher.ts` routes inbound frames:

| Frame | Action |
|---|---|
| `MESSAGE` | `messageStore.addIncoming` + invalidate conversations |
| `MESSAGE_ACK` | `messageStore.ackMessage` + invalidate conversations |
| `STATUS_UPDATE` | `messageStore.setDelivered` |
| `SEEN_UPDATE` | `messageStore.setRead` |
| `PRESENCE` | `presenceStore.setPresence` |
| `TYPING` | `typingStore.setTyping` (filtered to others only) |
| `MEDIA_MESSAGE` | `messageStore.addIncoming` + invalidate conversations |
| `MEDIA_THUMBNAIL_READY` | `messageStore.updateMessage` (patches `thumbnailUrl`, `mediaId`) |
| `FRIEND_*` | Invalidate friends + friend request queries |
| `GROUP_*` | Invalidate conversations |
| `NOTIFICATION` / `NOTIFICATION_READ` | Invalidate notifications + unread count |

### Key components

| Component | Location | Notes |
|---|---|---|
| `Sidebar` | `src/components/nav/Sidebar.tsx` | Brand, nav links, notification bell with badge + panel, user profile menu |
| `ConversationView` | `src/components/chat/ConversationView.tsx` | Shared pane for DIRECT + GROUP; owns search and group-settings panel state |
| `ConversationHeader` | `src/components/chat/ConversationHeader.tsx` | Normal mode (avatar + name + search + settings icons) or search mode (autofocused input) |
| `MessageSearchPanel` | `src/components/chat/MessageSearchPanel.tsx` | Absolute overlay below header; highlights matching text; click selects + scrolls |
| `GroupSettingsPanel` | `src/components/chat/GroupSettingsPanel.tsx` | Right-side overlay; member list with role badges; OWNER danger zone (transfer + delete) |
| `MessageList` | `src/components/chat/MessageList.tsx` | Renders messages; adds `id="msg-seq-{n}"` for scroll-to; accepts `highlightedSeq` |
| `MessageBubble` | `src/components/chat/MessageBubble.tsx` | TEXT and MEDIA rendering; `MEDIA` shows thumbnail or file icon; click fetches signed URL |
| `MessageInput` | `src/components/chat/MessageInput.tsx` | Text + typing indicator; attach button triggers hidden file input and calls `uploadMedia`; upload progress bar |

### Domain types (`src/types/domain.ts`)

All types mirror backend DTOs exactly. Key ones:

- `Conversation` — unified DIRECT/GROUP; `participants` is null in list views, populated in detail
- `Message` — includes optional `mediaId`, `thumbnailUrl`, `originalFilename`, `mediaType` fields (only present on MEDIA messages)
- `Participant` — `userId`, `username`, `role: ParticipantRole`, `lastReadSeq`, `lastDeliveredSeq`
- `Notification` — `type: NotificationType`, `referenceType: ReferenceType`, `read`, `eventCount`

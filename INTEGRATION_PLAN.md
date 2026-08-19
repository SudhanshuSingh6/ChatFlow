# Frontend ↔ Backend Integration Plan

## Table of Contents

- [Current State](#current-state)
- [Gap Analysis](#gap-analysis)
- [Phase 1 — Quick Wins](#phase-1--quick-wins)
- [Phase 2 — Notifications Feature](#phase-2--notifications-feature)
- [Phase 3 — Group Management UI](#phase-3--group-management-ui)
- [Phase 4 — Message Search](#phase-4--message-search)
- [Phase 5 — Media Messages](#phase-5--media-messages)
- [Phase 6 — AI Features](#phase-6--ai-features)
- [Files Changed](#files-changed)
- [Verification](#verification)

---

## Current State

### Already Wired (Working Today)

| Feature | Frontend | Backend |
|---|---|---|
| Register / Login | `AuthProvider` → `POST /api/auth/login`, `POST /api/auth/register` | `AuthController` |
| List conversations | `useConversations()` → `GET /api/conversations` | `ConversationController` |
| Conversation detail | `useConversation(id)` → `GET /api/conversations/{id}` | `ConversationController` |
| Paginated message history | `useMessages(id)` → `GET /api/conversations/{id}/messages` | `ConversationController` |
| Create direct chat | `useCreateDirect()` → `POST /api/conversations/direct` | `ConversationController` |
| Create group | `useCreateGroup()` → `POST /api/conversations/group` | `ConversationController` |
| Friends list | `useFriends()` → `GET /api/friends` | `FriendController` |
| Friend requests | `useFriendRequests(box)` → `GET /api/friends/requests/{box}` | `FriendController` |
| Send friend request | `useSendFriendRequest()` → `POST /api/friends/requests` | `FriendController` |
| Accept / decline request | `useRespondToRequest()` → `POST /api/friends/requests/{id}/accept\|decline` | `FriendController` |
| Unfriend | `useUnfriend()` → `DELETE /api/friends/{userId}` | `FriendController` |
| User search | `useUserSearch()` → `GET /api/users/search` | `UserController` |
| Conversation presence | `useConversationPresence()` → `GET /api/conversations/{id}/presence` | `PresenceController` |
| WS: send message | `useChat.sendMessage()` → `SEND_MESSAGE` frame | `ChatWebSocketHandler` |
| WS: receive message | dispatcher `MESSAGE` → `messageStore` | `WebSocketGateway` |
| WS: message ACK | dispatcher `MESSAGE_ACK` → `messageStore.ackMessage()` | `WebSocketGateway` |
| WS: delivery receipt | dispatcher `STATUS_UPDATE` → `messageStore.setDelivered()` | `WebSocketGateway` |
| WS: read receipt | dispatcher `SEEN_UPDATE` → `messageStore.setRead()` | `WebSocketGateway` |
| WS: presence | dispatcher `PRESENCE` → `presenceStore` | `WebSocketGateway` |
| WS: typing | dispatcher `TYPING` → `typingStore` | `WebSocketGateway` |
| WS: friend events | dispatcher `FRIEND_*` → invalidate React Query | `WebSocketGateway` |
| WS: group events | dispatcher `GROUP_*` → invalidate React Query | `WebSocketGateway` |

### Defined but Unused

| Item | File | Issue |
|---|---|---|
| `getMe()` | `src/lib/api/users.ts` | Function exists, no hook calls it |
| `getUserPresence()` | `src/lib/api/presence.ts` | Function exists, never used |
| `queryKeys.me` | `src/config/queryKeys.ts` | Key defined, no query uses it |
| `queryKeys.userPresence` | `src/config/queryKeys.ts` | Key defined, no query uses it |

---

## Gap Analysis

The following backend endpoints and WebSocket frame types have **no frontend wiring at all**:

### REST Endpoints Not Yet in Frontend

| Endpoint | Feature Area | Priority |
|---|---|---|
| `GET /api/users/me` | Current user profile | High |
| `GET /api/notifications` | Notifications | High |
| `GET /api/notifications/unread-count` | Notifications | High |
| `POST /api/notifications/{id}/read` | Notifications | High |
| `POST /api/notifications/read-all` | Notifications | High |
| `DELETE /api/notifications/{id}` | Notifications | High |
| `GET /api/conversations/{id}/messages/after` | Post-reconnect sync | High |
| `DELETE /api/conversations/{id}` | Group management | Medium |
| `POST /api/conversations/{id}/participants` | Group management | Medium |
| `DELETE /api/conversations/{id}/participants/{userId}` | Group management | Medium |
| `PUT /api/conversations/{id}/participants/{userId}/role` | Group management | Medium |
| `POST /api/conversations/{id}/transfer-ownership` | Group management | Medium |
| `GET /api/messages/search` | Message search | Medium |
| `GET /api/messages/search/hybrid` | Message search | Medium |
| `POST /api/messages/media` | Media upload | Medium |
| `GET /api/messages/media/{id}/url` | Media display | Medium |
| `DELETE /api/messages/media/{id}` | Media management | Medium |
| `GET /media/**` | Static file access | Medium |
| `GET /ai/conversations/{id}/summary` | AI summary | Low |
| `POST /ai/conversations/{id}/ask` | AI Q&A | Low |

### WebSocket Frames Not Yet Handled

| Frame | Direction | Issue |
|---|---|---|
| `NOTIFICATION` | Server → Client | Dispatcher has no case for it |
| `NOTIFICATION_READ` | Server → Client | Dispatcher has no case for it |
| `MEDIA_MESSAGE` | Server → Client | Dispatcher has no case for it |
| `MEDIA_THUMBNAIL_READY` | Server → Client | Dispatcher has no case for it |
| `ERROR` | Server → Client | Received but silently ignored |
| `PONG` | Server → Client | Benign — no action needed |

---

## Phase 1 — Quick Wins

These require no new UI components — just wiring existing infrastructure.

### 1a. Fetch current user profile (`GET /api/users/me`)

**Why:** The sidebar currently shows whatever username was returned at login. `GET /api/users/me` gives the canonical profile and should be the source of truth for the logged-in user's display.

**Changes:**

- **`src/hooks/useMe.ts`** *(new)* — React Query hook:
  ```ts
  export function useMe() {
    return useQuery({ queryKey: queryKeys.me, queryFn: getMe });
  }
  ```
- **`src/app/provider/AuthProvider.tsx`** — Call `useMe()` after successful login/register to hydrate `authStore` with the full user profile.
- `queryKeys.me` already defined — no change needed there.

---

### 1b. Handle `NOTIFICATION` and `NOTIFICATION_READ` WS frames

**Why:** The server pushes `NOTIFICATION` frames in real time (e.g. "someone sent you a friend request"). Without handling them, the unread badge never updates live.

**Changes:**

- **`src/lib/ws/dispatcher.ts`** — Add cases:
  ```ts
  case "NOTIFICATION":
  case "NOTIFICATION_READ":
    queryClient.invalidateQueries({ queryKey: queryKeys.notifications });
    queryClient.invalidateQueries({ queryKey: queryKeys.notificationUnreadCount });
    break;
  ```
- **`src/config/queryKeys.ts`** — Add two keys:
  ```ts
  notifications: ["notifications"],
  notificationUnreadCount: ["notifications", "unread-count"],
  ```

---

### 1c. Post-reconnect message sync (`GET /api/conversations/{id}/messages/after`)

**Why:** When the WebSocket reconnects after a drop, messages sent while offline are not automatically replayed. The `/after` endpoint fetches everything after a known sequence number.

**Changes:**

- **`src/lib/api/conversations.ts`** — Add:
  ```ts
  export const getMessagesAfter = (id: string, after: number, limit = 20) =>
    client.get<MessagePage>(`/api/conversations/${id}/messages/after`, { params: { after, limit } });
  ```
- **`src/app/provider/WebSocketProvider.tsx`** — On transition to `status === "open"` after a reconnect (track attempt count), call `getMessagesAfter` for the currently open conversation using the last known `sequenceNumber` from `messageStore`.

---

## Phase 2 — Notifications Feature

Notifications are a complete missing feature — no API layer, no hooks, no UI.

### 2a. API Layer

**`src/lib/api/notifications.ts`** *(new file)*:

```ts
GET  /api/notifications?cursor={instant}&limit={n}  → NotificationResponse[]
GET  /api/notifications/unread-count                → { count: number }
POST /api/notifications/{id}/read                  → 204
POST /api/notifications/read-all                   → 204
DELETE /api/notifications/{id}                     → 204
```

### 2b. Hooks

**`src/hooks/useNotifications.ts`** *(new file)*:

| Hook | Query | Description |
|---|---|---|
| `useNotifications()` | Infinite query, cursor-based | Paginated notification feed |
| `useNotificationUnreadCount()` | Query, refetch on window focus | Badge count |
| `useMarkNotificationRead()` | Mutation | Marks one notification read; invalidates count |
| `useMarkAllRead()` | Mutation | Marks all read; invalidates count + list |
| `useDeleteNotification()` | Mutation | Removes one; invalidates list |

### 2c. UI

**`src/components/nav/Sidebar.tsx`** — Add a notification bell icon above the user profile section:
- Use existing `Badge` component (`src/components/ui/Badge.tsx`) to show unread count.
- Clicking opens a dropdown panel listing recent notifications.
- Each item has a "mark read" button.
- "Mark all read" button at the top of the panel.

**Notification item types to render** (based on backend `NotificationResponse.type`):
- `FRIEND_REQUEST` — "{username} sent you a friend request"
- `FRIEND_REQUEST_ACCEPTED` — "{username} accepted your request"
- `MESSAGE` — "New message in {conversationName}"

---

## Phase 3 — Group Management UI

The backend has full group admin APIs. The frontend has no UI to use them.

### 3a. API Layer

Add to **`src/lib/api/conversations.ts`**:

```ts
deleteGroup(id)
addParticipant(id, userId)
removeParticipant(id, userId)
updateMemberRole(id, userId, role: "ADMIN" | "MEMBER")
transferOwnership(id, newOwnerId)
```

### 3b. Hooks

Add to **`src/hooks/useConversations.ts`**:

| Hook | Mutation | Invalidates |
|---|---|---|
| `useDeleteGroup()` | `DELETE /api/conversations/{id}` | conversations list |
| `useAddParticipant()` | `POST /api/conversations/{id}/participants` | conversation detail |
| `useRemoveParticipant()` | `DELETE /api/conversations/{id}/participants/{userId}` | conversation detail |
| `useUpdateRole()` | `PUT /api/conversations/{id}/participants/{userId}/role` | conversation detail |
| `useTransferOwnership()` | `POST /api/conversations/{id}/transfer-ownership` | conversation detail |

### 3c. UI

**`src/components/chat/ConversationHeader.tsx`** — Add a settings icon (gear) for group conversations.

Clicking opens a **Group Settings panel** (new component):
- **Member list** — shows each participant's username and role badge.
- **Remove member** button — visible to `ADMIN` and `OWNER`, not for self or owner.
- **Change role** dropdown — `ADMIN` ↔ `MEMBER`, visible to `OWNER` only.
- **Transfer ownership** button — visible to `OWNER` only; prompts confirmation.
- **Delete group** button — visible to `OWNER` only; prompts confirmation.

Access control is driven by `conversation.callerRole` already returned in the API response.

---

## Phase 4 — Message Search

### 4a. API Layer

Add to **`src/lib/api/conversations.ts`**:

```ts
searchMessages(query: string, cursor?: string, limit = 20) →
  GET /api/messages/search?query={q}&cursor={c}&limit={n}
```

### 4b. Hook

**`src/hooks/useMessageSearch.ts`** *(new file)* — debounced query (300 ms), enabled only when `query.length >= 2`. Same pattern as existing `useUserSearch`.

### 4c. UI

**`src/components/chat/ConversationHeader.tsx`** — Add a search icon. Clicking toggles an inline search bar.

Results display below the header:
- Each result shows: sender name, message preview, timestamp.
- Clicking a result scrolls the message list to that `sequenceNumber`.

---

## Phase 5 — Media Messages

### 5a. API Layer

**`src/lib/api/media.ts`** *(new file)*:

```ts
uploadMedia(conversationId: string, file: File) →
  POST /api/messages/media  (multipart/form-data)  → MediaMessageResponse

getMediaUrl(id: string) →
  GET /api/messages/media/{id}/url  → { url: string }
```

### 5b. WebSocket Dispatcher

Add to **`src/lib/ws/dispatcher.ts`**:

```ts
case "MEDIA_MESSAGE":
  // treat same as MESSAGE — add to messageStore
  messageStore.addIncoming(frame.payload);
  break;

case "MEDIA_THUMBNAIL_READY":
  // update the existing media message entry with the thumbnail URL
  messageStore.updateMediaThumbnail(frame.payload.messageId, frame.payload.thumbnailUrl);
  break;
```

Also add `updateMediaThumbnail` action to **`src/store/messageStore.ts`**.

### 5c. UI

**`src/components/chat/MessageInput.tsx`** — Wire the existing attach button (currently a no-op):
- Opens a file picker (`<input type="file" accept="image/*,video/*,audio/*,.pdf,.doc*">`)
- On file select: calls `uploadMedia(conversationId, file)`
- Shows an upload progress indicator while the server processes it
- On `MEDIA_MESSAGE` WS frame: the message appears in the thread

**`src/components/chat/MessageBubble.tsx`** — Render media when `message.mediaUrl` is present:
- `image/*` → `<img>` with thumbnail; click to view full size
- `video/*` → `<video controls>`
- Others → file download link with filename and size

---

## Phase 6 — AI Features

### 6a. API Layer

**`src/lib/api/ai.ts`** *(new file)*:

```ts
getConversationSummary(id: string) →
  GET /ai/conversations/{id}/summary  → { summary: string }

askConversation(id: string, question: string) →
  POST /ai/conversations/{id}/ask  → { answer: string }
```

### 6b. Hooks

**`src/hooks/useAi.ts`** *(new file)*:
- `useConversationSummary(id)` — lazy query (only fetches when user requests it)
- `useAskConversation()` — mutation

### 6c. UI

**`src/components/chat/ConversationHeader.tsx`** — Add an AI sparkle icon.

Clicking opens an **AI panel**:
- **"Catch me up"** button — fetches and displays the conversation summary
- **Ask a question** text input + submit — displays the RAG answer inline

> AI features require `chatflow-ai` to be running (`./mvnw spring-boot:run -pl chatflow-ai -am`).

---

## Files Changed

| File | Change |
|---|---|
| `src/lib/api/conversations.ts` | Add `getMessagesAfter`, group management, `searchMessages` |
| `src/lib/api/notifications.ts` | **Create** — full notifications API |
| `src/lib/api/media.ts` | **Create** — upload + URL fetch |
| `src/lib/api/ai.ts` | **Create** — summary + ask |
| `src/hooks/useMe.ts` | **Create** — current user profile hook |
| `src/hooks/useNotifications.ts` | **Create** — notifications hooks |
| `src/hooks/useMessageSearch.ts` | **Create** — debounced search hook |
| `src/hooks/useAi.ts` | **Create** — AI summary + ask hooks |
| `src/hooks/useConversations.ts` | Add group management mutations |
| `src/config/queryKeys.ts` | Add `notifications`, `notificationUnreadCount` keys |
| `src/lib/ws/dispatcher.ts` | Handle `NOTIFICATION`, `NOTIFICATION_READ`, `MEDIA_MESSAGE`, `MEDIA_THUMBNAIL_READY` |
| `src/store/messageStore.ts` | Add `updateMediaThumbnail` action |
| `src/app/provider/WebSocketProvider.tsx` | Post-reconnect sync on WS open |
| `src/app/provider/AuthProvider.tsx` | Call `useMe` on login to hydrate full user profile |
| `src/components/nav/Sidebar.tsx` | Add notification bell + unread badge |
| `src/components/chat/ConversationHeader.tsx` | Add search, group settings, AI panel triggers |
| `src/components/chat/MessageInput.tsx` | Wire attach button to file upload |
| `src/components/chat/MessageBubble.tsx` | Render image/video/file previews |

---

## Verification

After each phase, verify end-to-end with the backend running:

```bash
# Backend
cd chatflow-backend
docker compose up -d
./mvnw clean install -DskipTests
./mvnw spring-boot:run -pl chatflow-gateway &
./mvnw spring-boot:run -pl chatflow-core

# Frontend
cd chatflow-frontend
npm run dev
```

| Phase | Test |
|---|---|
| 1a | Login → sidebar shows real username from `/api/users/me` |
| 1b | Receive a friend request in another tab → notification badge increments live |
| 1c | Disconnect from backend → send messages → reconnect → messages appear |
| 2 | Click notification bell → list appears; clicking "mark read" clears badge |
| 3 | Open a group → settings gear → remove a member → member list updates live |
| 4 | Type in search bar → results appear within 300 ms; click → scrolls to message |
| 5 | Click attach → select image → thumbnail appears in chat thread |
| 6 | Click AI icon → "Catch me up" → summary appears (requires chatflow-ai running) |

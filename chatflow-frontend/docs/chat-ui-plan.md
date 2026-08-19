# ChatFlow Frontend — Chat UI Plan

**Goal:** a 3-pane messenger UI (icon rail · contextual list · conversation) on the
existing React 19 + Vite + Tailwind v4 + Zustand + React Query stack, wired to the
ChatFlow backend's REST + raw-WebSocket API.

## Locked decisions
- **Routed panes** — `/chats/:id`, `/groups/:id`, `/friends` are real URLs
  (deep-linkable, back-button works, refresh stays put) via nested routes + `<Outlet>`.
- **Build order** — static shell with mock data → WS client + stores → replace mocks.

---

## Layout (3 panes)

```
┌──────┬─────────────────────────┬──────────────────────────────┐
│ RAIL │   LIST PANEL            │   CONVERSATION                │
│ 64px │   ~320px                │   fills rest                  │
│ logo │  chats / groups /       │  selected conversation, or    │
│ nav  │  friends (per rail tab) │  empty state                  │
│ out  │                         │                               │
└──────┴─────────────────────────┴──────────────────────────────┘
```

---

## Folder structure

```
src/
├── app/
│   ├── provider/  QueryProvider ✅  AuthProvider ✅  WebSocketProvider (P3)
│   └── router/    AppRouter ✅  ProtectedRoute ✅  GuestRoute ✅
├── pages/
│   ├── auth/      LoginPage ✅  RegisterPage ✅
│   ├── AppShell.tsx                 ← Rail + list <Outlet/>
│   ├── chats/     ChatsPage.tsx  ConversationPage.tsx
│   ├── groups/    GroupsPage.tsx  GroupConversationPage.tsx
│   ├── friends/   FriendsPage.tsx
│   └── EmptyState.tsx
├── components/
│   ├── nav/       Rail.tsx  RailButton.tsx
│   ├── list/      ListPanel.tsx  SearchBar.tsx  ConversationRow.tsx
│   ├── chat/      ConversationHeader.tsx  MessageList.tsx  MessageBubble.tsx
│   │              MessageInput.tsx  TypingDots.tsx  DateDivider.tsx
│   ├── friends/   FriendRow.tsx  FriendRequestRow.tsx
│   ├── presence/  PresenceDot.tsx
│   └── ui/        Avatar.tsx  IconButton.tsx  Badge.tsx
├── lib/
│   ├── api/       client ✅  auth ✅  conversations  groups  friends  media  search  (P2)
│   ├── ws/        types  WebSocketClient  dispatcher  (P3)
│   ├── mock/      data.ts   (P1, removed in P2)
│   └── utils/     cn.ts  time.ts
├── store/         authStore ✅  wsStore  messageStore  presenceStore  typingStore  (P3)
├── hooks/         useConversations, useMessages, useSendMessage, … (P2/P3)
├── types/         domain.ts
└── config/        env.ts  queryKeys.ts  (P2/P3)
```

---

## Routing tree

```
/login /register                       → GuestRoute
/  (ProtectedRoute → AppShell)
├── index → /chats
├── /chats        ChatsPage
│     ├── index → EmptyState
│     └── :conversationId → ConversationPage
├── /groups       GroupsPage
│     ├── index → EmptyState
│     └── :groupId → GroupConversationPage
└── /friends      FriendsPage
```

`AppShell` = Rail + `<Outlet>`. Each list page = list + nested `<Outlet>` (conversation
or EmptyState).

---

## State split

| State | Owner | Examples |
|---|---|---|
| Server data (REST) | React Query | conversation/group/friend lists, message history, search |
| Realtime / ephemeral | Zustand | live messages, optimistic sends, presence, typing, WS status |
| Auth/session | Zustand (persisted) ✅ | token, user |
| Routed UI | URL | active tab, selected conversation |

REST loads history into React Query; the WS pushes deltas into Zustand. The
conversation view merges `[history] + [live]`. Optimistic send writes to the store
immediately, reconciles on `MESSAGE_ACK` via `clientMessageId`.

---

## WebSocket client (P3)

- Connect `ws(s)://<host>/ws?token=<jwt>` (token read synchronously from authStore).
- Auto-reconnect w/ backoff; status → `wsStore`.
- `send(type, payload)` → `{type, requestId, payload}`; `requestId` → ack correlation.
- Inbound → dispatcher → stores: `MESSAGE`/`GROUP_MESSAGE` → messageStore;
  `MESSAGE_ACK`/`STATUS_UPDATE`/`SEEN_UPDATE` → reconcile + ticks; `PRESENCE` →
  presenceStore; `TYPING` → typingStore; `FRIEND_*`/`GROUP_*` → invalidate RQ keys;
  `MEDIA_THUMBNAIL_READY` → patch message.
- `ws/types.ts` mirrors backend `InboundMessage.Type` / `OutboundMessage.Type`.

---

## Build phases

1. **Shell & look** — ui primitives, Rail, AppShell, nested routes, list +
   conversation components, EmptyState. **Mock data.** ← *this phase*
2. **REST wiring** — api modules, React Query hooks, queryKeys. Lists + history real.
3. **WebSocket core** — ws types/client/dispatcher, stores, WebSocketProvider.
   Live receive + presence + typing + receipts.
4. **Send pipeline** — optimistic send via WS, clientMessageId dedup, tick
   transitions, unread counts, scroll-to-bottom.
5. **Polish** — friend requests, group mgmt, media upload, list virtualization,
   dark mode + logo color system.

Each phase ends green on `tsc -b` + `eslint` + `npm run build`.

## Added deps
`clsx`, `tailwind-merge` (cn helper), `date-fns` (time formatting). Everything else
uses the existing stack.

## Theme
Brand gradient `from-blue-500 to-purple-600` (matches the logo) for the rail logo,
active nav, own-message bubbles, and avatars.

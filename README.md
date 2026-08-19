# ChatFlow

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java_21-007396?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img alt="Spring Boot 4" src="https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
  <img alt="React 19" src="https://img.shields.io/badge/React_19-61DAFB?style=for-the-badge&logo=react&logoColor=black" />
  <img alt="TypeScript" src="https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white" />
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" />
  <img alt="Redis" src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" />
  <img alt="Kafka" src="https://img.shields.io/badge/Apache_Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" />
  <img alt="Docker" src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" />
</p>

<p align="center">
  <b>Full-stack real-time chat platform — private messaging, group chats, friends, notifications, media, and AI-powered search.</b>
</p>

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Repository Layout](#repository-layout)
- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [1. Start Infrastructure](#1-start-infrastructure)
  - [2. Run the Backend](#2-run-the-backend)
  - [3. Run the Frontend](#3-run-the-frontend)
- [Environment Variables](#environment-variables)
- [Backend Modules](#backend-modules)
- [API Reference](#api-reference)
- [WebSocket Protocol](#websocket-protocol)
- [Data Model](#data-model)
- [Key Design Decisions](#key-design-decisions)
- [Infrastructure Ports](#infrastructure-ports)

---

## Overview

ChatFlow is a production-grade real-time chat application. The backend is a Java 21 / Spring Boot 4 multi-module Maven project structured as a set of microservices behind a Spring Cloud Gateway. The frontend is a React 19 / TypeScript SPA built with Vite.

```
Browser ──► Spring Cloud Gateway (8088)
                 │
                 ├── /api/**  ──► chatflow-core  (8080)  ← auth, chat, groups, friends, notifications
                 ├── /ai/**   ──► chatflow-ai    (8081)  ← RAG, embeddings, summaries
                 └── /ws      ──► chatflow-core  (8080)  ← WebSocket (embedded mode, default)
                                  chatflow-realtime (8083) ← WebSocket (external mode)
```

---

## Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                         Browser / Client                         │
│           React SPA  ·  REST (Axios)  ·  WebSocket               │
└────────────────────────────┬─────────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │ chatflow-gateway │  JWT edge auth · routing
                    │    port 8088    │
                    └────────┬────────┘
           ┌─────────────────┼──────────────────┐
           │                 │                  │
  ┌────────▼───────┐ ┌───────▼──────┐  ┌───────▼──────────┐
  │ chatflow-core  │ │ chatflow-ai  │  │chatflow-realtime │
  │   port 8080    │ │  port 8081   │  │   port 8083      │
  │ auth · chat    │ │ embeddings   │  │ WebSocket-only   │
  │ groups · friends│ │ RAG · summary│  │ (external mode)  │
  │ notifications  │ └──────────────┘  └──────────────────┘
  │ presence · media│
  │ outbox         │
  └───┬────────┬───┘
      │        │
┌─────▼──┐ ┌───▼──────────────────────────────┐
│Postgres│ │ Redis  Kafka  MinIO  Jaeger  Grafana│
│  5432  │ │ 6379   9092   9000   16686    3000  │
└────────┘ └──────────────────────────────────-─┘
```

### Message Flow

```
Client ──SEND_MESSAGE──► ChatWebSocketHandler
                              │
                         ChatService
                              │
                    ┌─────────┴──────────┐
                    │    PostgreSQL       │
                    │  lock conversation  │
                    │  insert message     │
                    │  advance cursors    │
                    │  write outbox event │
                    └─────────┬──────────┘
                              │ after commit
                    ┌─────────┴──────────┐
              MESSAGE_ACK to sender   MESSAGE to recipients
                              │
                    WebSocketGateway
                    ├── local delivery (same JVM)
                    └── Redis chat:relay (other instances)
```

### Redis Pub/Sub Fanout

Each backend instance subscribes to `chat:relay`. On receive, it checks `targetUserId` against its local session registry and delivers only if the user is local — ignoring frames whose `sourceInstanceId` matches its own.

---

## Tech Stack

### Backend

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4, Spring Web, Spring Security, Spring WebSocket |
| Persistence | PostgreSQL, Spring Data JPA, Flyway |
| Messaging | Apache Kafka, Redis Pub/Sub |
| Auth | JWT (jjwt 0.12.6) |
| Media | Local disk or MinIO/S3 (AWS SDK v2), Thumbnailator, FFmpeg |
| AI | OpenAI-compatible embedding API, Anthropic Claude (RAG), pgvector |
| Observability | OpenTelemetry (OTLP → Jaeger), Micrometer → Prometheus, Grafana |
| Resilience | Resilience4j circuit breaker (Spring Cloud) |
| Build | Maven Wrapper, Docker Compose |

### Frontend

| Layer | Technology |
|---|---|
| Language | TypeScript |
| UI Framework | React 19 |
| Build Tool | Vite |
| Styling | Tailwind CSS v4, Material Symbols (Google Fonts) |
| State | Zustand (auth, messages, presence, typing, ws status) |
| Server State | TanStack React Query v5 |
| Forms | React Hook Form |
| HTTP | Axios with JWT interceptor |
| Routing | React Router v7 |
| Icons | React Icons (Feather), Material Symbols Outlined |

---

## Repository Layout

```
ChatFlow/
├── chatflow-backend/          Java Maven multi-module project
│   ├── chatflow-contracts/    Shared DTOs and Kafka event records (library)
│   ├── chatflow-storage/      S3/local storage abstraction (library)
│   ├── chatflow-core/         Main service: REST, WebSocket, outbox       :8080
│   ├── chatflow-ai/           RAG, embeddings, summaries (own Postgres)   :8081
│   ├── chatflow-media/        Kafka-driven media processing               :8082
│   ├── chatflow-realtime/     Dedicated WebSocket service (external mode) :8083
│   ├── chatflow-gateway/      Spring Cloud Gateway, JWT edge auth         :8088
│   ├── docker-compose.yml
│   └── pom.xml
│
└── chatflow-frontend/         React 19 / Vite / TypeScript SPA
    ├── src/
    │   ├── app/               Providers (Auth, WebSocket, Query, Router)
    │   ├── components/        UI components (auth, chat, nav, modals, ui)
    │   ├── hooks/             React Query hooks
    │   ├── lib/               Axios client, WebSocket client + dispatcher
    │   ├── pages/             Route-level page components
    │   ├── store/             Zustand stores
    │   └── types/             Domain types
    └── vite.config.ts
```

---

## Features

- **Authentication** — Register and login with JWT. Tokens persisted to `localStorage`.
- **Direct messaging** — Open a 1:1 conversation with any user; idempotent (re-opening returns the same conversation).
- **Group chats** — Create group conversations; add/remove members; roles: `OWNER`, `ADMIN`, `MEMBER`; transfer ownership.
- **Friends** — Send, accept, decline, and remove friend requests. Real-time WS events on all state changes.
- **Real-time delivery** — WebSocket with exponential-backoff auto-reconnect; PING/PONG heartbeat.
- **Delivery & read receipts** — Stored as participant-level sequence cursors, not per-message flags.
- **Typing indicators** — Broadcast to conversation participants; auto-cleared after 6 s TTL on the client.
- **Presence** — Online/offline state via WS `PRESENCE` frames; seeded from REST snapshot on conversation open.
- **Offline replay** — Missed messages replayed on reconnect using participant `last_delivered_seq`.
- **Media messages** — Upload images/video/audio/files; thumbnails processed asynchronously; signed access URLs.
- **Notifications** — Persistent notification feed with unread count badge; mark-read / mark-all-read.
- **Message search** — Keyword search across all conversations; hybrid keyword + vector search via AI service.
- **AI features** — Conversation summaries and RAG-powered "ask" queries (chatflow-ai service).
- **Transactional outbox** — Events written atomically with business data; at-least-once delivery via idempotency guard.
- **Multi-instance fanout** — Redis Pub/Sub relay so users on different backend instances receive messages.
- **Observability** — Distributed tracing (Jaeger), Prometheus metrics, Grafana dashboards, structured logs.

---

## Getting Started

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21 |
| Maven Wrapper | included (`chatflow-backend/mvnw`) |
| Node.js | 18+ |
| Docker + Docker Compose | any recent version |
| FFmpeg | optional (video thumbnails) |

### 1. Start Infrastructure

```bash
cd chatflow-backend
docker compose up -d
```

This starts Postgres, Postgres-AI, Redis, Kafka, MinIO, Jaeger, Prometheus, and Grafana. See [Infrastructure Ports](#infrastructure-ports) for all URLs.

### 2. Run the Backend

**Option A — core only (minimum for the frontend to work):**

```bash
cd chatflow-backend

# Build shared library modules first (only needed once)
./mvnw clean install -DskipTests

# Start the gateway
./mvnw spring-boot:run -pl chatflow-gateway

# Start core (in a separate terminal)
./mvnw spring-boot:run -pl chatflow-core
```

**Option B — full platform via Docker:**

```bash
cd chatflow-backend
docker compose --profile apps up --build
```

### 3. Run the Frontend

```bash
cd chatflow-frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

The Vite dev server proxies all API and WebSocket traffic:

| Path | Target |
|---|---|
| `/api/**` | Gateway `http://localhost:8088` |
| `/ai/**` | Gateway `http://localhost:8088` |
| `/ws` | Core `ws://localhost:8080` (default) |

> **Note:** By default the proxy sends `/ws` to port 8080 (core in embedded mode). If you are running `chatflow-realtime` instead, create `chatflow-frontend/.env.local`:
> ```
> VITE_WS_URL=ws://localhost:8083
> ```

---

## Environment Variables

### Backend (chatflow-core)

| Variable | Default | Description |
|---|---|---|
| `DB_USERNAME` | `chatflow` | PostgreSQL username |
| `DB_PASSWORD` | `chatflow` | PostgreSQL password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `JWT_SECRET` | dev placeholder | JWT signing secret (**change in prod**) |
| `INTERNAL_TOKEN` | `dev-internal-token` | Service-to-service token (must match ai/realtime) |
| `APP_OUTBOX_TRANSPORT` | `in-process` | `in-process` or `kafka` |
| `APP_REALTIME_MODE` | `embedded` | `embedded` (core handles `/ws`) or `external` (chatflow-realtime) |
| `SPRING_PROFILES_ACTIVE` | — | Set to `s3` to use MinIO/S3 instead of local disk |
| `S3_ENDPOINT` | `http://localhost:9000` | MinIO/S3 endpoint |
| `S3_BUCKET` | `chatflow` | Bucket name |
| `S3_ACCESS_KEY` | `minioadmin` | Access key |
| `S3_SECRET_KEY` | `minioadmin` | Secret key |
| `AI_BASE_URL` | `http://localhost:8081` | chatflow-ai base URL |
| `FFMPEG_PATH` | `ffmpeg` | FFmpeg binary path |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | Jaeger OTLP endpoint |

### Frontend

| Variable | Default | Description |
|---|---|---|
| `VITE_GATEWAY_URL` | `http://localhost:8088` | Backend gateway URL |
| `VITE_WS_URL` | `ws://localhost:8083` | WebSocket URL (core embedded → use 8080) |

---

## Backend Modules

| Module | Port | Responsibility |
|---|---|---|
| `chatflow-contracts` | — | Shared DTOs and Kafka event records (library, no Spring) |
| `chatflow-storage` | — | S3/local storage abstraction (library) |
| `chatflow-core` | 8080 | Auth, conversations, messages, friends, notifications, presence, media records, outbox |
| `chatflow-ai` | 8081 | Embedding generation, pgvector storage, RAG chat completions, conversation summaries |
| `chatflow-media` | 8082 | Kafka-driven thumbnail processing; writes results to S3 |
| `chatflow-realtime` | 8083 | Standalone WebSocket server; delegates commands to core via `/internal/realtime/*` |
| `chatflow-gateway` | 8088 | Spring Cloud Gateway; JWT validation at the edge; routes `/api/**` and `/ai/**` |

---

## API Reference

### Authentication

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/register` | `{ username, password }` | `{ token, userId, username }` |
| POST | `/api/auth/login` | `{ username, password }` | `{ token, userId, username }` |

### Users

| Method | Path | Response |
|---|---|---|
| GET | `/api/users/me` | `UserSummary` |
| GET | `/api/users/search?q={query}&limit={n}` | `UserSummary[]` |

### Conversations

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/conversations` | — | `ConversationResponse[]` |
| GET | `/api/conversations/{id}` | — | `ConversationResponse` |
| POST | `/api/conversations/direct` | `{ userId }` | `ConversationResponse` |
| POST | `/api/conversations/group` | `{ name, memberIds[] }` | `ConversationResponse` 201 |
| DELETE | `/api/conversations/{id}` | — | 204 |

### Messages

| Method | Path | Response |
|---|---|---|
| GET | `/api/conversations/{id}/messages?before={seq}&limit={n}` | `MessagePageResponse` |
| GET | `/api/conversations/{id}/messages/after?after={seq}&limit={n}` | `MessagePageResponse` |

### Group Management

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/conversations/{id}/participants` | `{ userId }` | `ParticipantResponse` 201 |
| DELETE | `/api/conversations/{id}/participants/{userId}` | — | 204 |
| PUT | `/api/conversations/{id}/participants/{userId}/role` | `{ role }` | `ParticipantResponse` |
| POST | `/api/conversations/{id}/transfer-ownership` | `{ newOwnerId }` | `ConversationResponse` |

### Friends

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/friends` | — | `FriendshipResponse[]` |
| GET | `/api/friends/requests/received` | — | `FriendshipResponse[]` |
| GET | `/api/friends/requests/sent` | — | `FriendshipResponse[]` |
| POST | `/api/friends/requests` | `{ username }` | `FriendshipResponse` 201 |
| POST | `/api/friends/requests/{id}/accept` | — | `FriendshipResponse` |
| POST | `/api/friends/requests/{id}/decline` | — | `FriendshipResponse` |
| DELETE | `/api/friends/{userId}` | — | 204 |

### Notifications

| Method | Path | Response |
|---|---|---|
| GET | `/api/notifications?cursor={instant}&limit={n}` | `NotificationResponse[]` |
| GET | `/api/notifications/unread-count` | `{ count }` |
| POST | `/api/notifications/{id}/read` | 204 |
| POST | `/api/notifications/read-all` | 204 |
| DELETE | `/api/notifications/{id}` | 204 |

### Media

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/messages/media` | multipart: `conversationId` + `file` | `MediaMessageResponse` 202 |
| GET | `/api/messages/media/{id}` | — | `MediaMessageResponse` |
| GET | `/api/messages/media/{id}/url` | — | `{ url }` |
| DELETE | `/api/messages/media/{id}` | — | 204 |
| GET | `/media/**` | — | Static file |

### Search

| Method | Path | Response |
|---|---|---|
| GET | `/api/messages/search?query={q}&cursor={c}&limit={n}` | `SearchPageResponse` |
| GET | `/api/messages/search/hybrid?query={q}&limit={n}` | `RankedSearchResult[]` |

### AI

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/ai/conversations/{id}/summary` | — | `{ summary }` |
| POST | `/ai/conversations/{id}/ask` | `{ question }` | `{ answer }` |

### Presence

| Method | Path | Response |
|---|---|---|
| GET | `/api/users/{userId}/presence` | `{ online }` |
| GET | `/api/conversations/{id}/presence` | `ConversationPresenceResponse` |

---

## WebSocket Protocol

Connect: `ws://localhost:8080/ws?token=<JWT>`

All frames use the envelope:
```json
{ "type": "FRAME_TYPE", "requestId": "uuid", "payload": { ... } }
```

### Client → Server

| Type | Payload |
|---|---|
| `SEND_MESSAGE` | `{ conversationId, clientMessageId, content }` |
| `CONVERSATION_OPEN` | `{ conversationId }` |
| `MARK_READ` | `{ conversationId, upToSeq }` |
| `MESSAGE_DELIVERED` | `{ conversationId, upToSeq }` |
| `TYPING` | `{ conversationId, typing: boolean }` |
| `PING` | `{}` |

### Server → Client

| Type | Description |
|---|---|
| `MESSAGE` | New message in a conversation |
| `MESSAGE_ACK` | Confirmation of sent message (includes assigned `sequenceNumber`) |
| `STATUS_UPDATE` | Delivery receipt from a recipient |
| `SEEN_UPDATE` | Read receipt from a recipient |
| `PRESENCE` | User came online or went offline |
| `TYPING` | User started or stopped typing |
| `NOTIFICATION` | New notification |
| `NOTIFICATION_READ` | Notification marked as read |
| `MEDIA_MESSAGE` | Media message processed and ready |
| `MEDIA_THUMBNAIL_READY` | Thumbnail generated for a media message |
| `FRIEND_REQUEST` | Incoming friend request |
| `FRIEND_REQUEST_ACCEPTED` | A sent request was accepted |
| `FRIEND_REQUEST_DECLINED` | A sent request was declined |
| `FRIEND_REMOVED` | A friendship was removed |
| `GROUP_CREATED` | New group you were added to |
| `GROUP_MEMBER_ADDED` | Member added to a group |
| `GROUP_MEMBER_REMOVED` | Member removed from a group |
| `GROUP_ROLE_CHANGED` | A member's role was updated |
| `GROUP_OWNERSHIP_TRANSFERRED` | Group ownership changed |
| `GROUP_DELETED` | Group was deleted |
| `PONG` | Heartbeat response to `PING` |
| `ERROR` | Server-side error |

---

## Data Model

```
USERS
  id · username · password_hash · created_at

CONVERSATIONS
  id · type (DIRECT|GROUP) · name · dm_key · last_message_at · last_message_preview

CONVERSATION_PARTICIPANTS
  conversation_id · user_id · role · last_read_seq · last_delivered_seq · joined_at

MESSAGES
  id · conversation_id · sender_id · type · content · sequence_number · created_at · deleted_at

MEDIA_MESSAGES
  id · message_id · storage_key · mime_type · file_size · thumbnail_key · status

FRIENDSHIPS
  id · requester_id · addressee_id · status (PENDING|ACCEPTED|DECLINED)

NOTIFICATIONS
  id · user_id · type · payload (JSON) · read · created_at · deleted_at

OUTBOX_EVENTS
  id · aggregate_type · aggregate_id · event_type · payload (JSON) · processed · created_at

PROCESSED_EVENTS
  event_id (idempotency guard for at-least-once Kafka consumers)
```

**Delivery / read receipts** are stored as participant-level cursors — not per-message flags:

```
Has user X read message N?
→ participant.last_read_seq >= message.sequence_number
```

This scales to both direct chats (2 participants) and large group chats (N participants) with no extra rows per message.

---

## Key Design Decisions

**Unified conversation model** — Direct and group chats share the same `Conversation` entity and message pipeline. Participant count is the only difference. This simplifies queries, reduces code duplication, and makes future features (e.g. adding a third person to a DM) trivial.

**Transactional outbox** — `ChatService` writes `OutboxEvent` rows in the same DB transaction as the message insert. A poller picks them up and fans out notifications. This guarantees at-least-once delivery without two-phase commit. Transport is swappable between in-process (dev) and Kafka (production) via a single env var.

**Redis Pub/Sub for fanout** — `WebSocketGateway` first tries local delivery, then publishes to `chat:relay`. Each instance ignores its own frames (`sourceInstanceId` check). This handles horizontal scaling without sticky sessions.

**Sequence numbers, not timestamps** — Messages carry a monotonically increasing `sequence_number` per conversation (not a timestamp). This makes pagination cursor-stable, receipt watermarks exact, and replay deterministic.

**Zustand for WebSocket state** — Auth and live state live in Zustand stores so the Axios interceptor and WebSocket client can read the JWT synchronously outside React's render cycle.

---

## Infrastructure Ports

| Service | URL | Credentials |
|---|---|---|
| ChatFlow App | `http://localhost:5173` | — |
| Gateway | `http://localhost:8088` | — |
| Core | `http://localhost:8080` | — |
| AI Service | `http://localhost:8081` | — |
| Realtime Service | `ws://localhost:8083` | — |
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

# ChatFlow — Project Walkthrough (Beginner-Friendly Onboarding)

Welcome! This document explains the **entire ChatFlow backend** from the ground up. It assumes
you're new to systems like this, so it explains the *ideas* (what a "token" is, what "Kafka" does,
why we split one app into many) before showing how ChatFlow uses them.

How to read it:
1. **§1–§2** — what we're building and the words/concepts you'll keep meeting. Don't skip these.
2. **§3** — the map of all the pieces.
3. **§4** — the handful of patterns that show up everywhere.
4. **§5** — follow a real request from the user's screen all the way through the system. This is
   where it clicks.
5. **§6** — how the **code itself** is organized, so you can open a file and know where you are.
6. **§7–§8** — run it yourself, and a glossary you can come back to.
7. **§9** — a complete reference of **every API endpoint** (the exhaustive list the flows draw from).

Take your time. By the end you'll be able to point at any part of the system and say what it does.

---

## 1. What is ChatFlow?

ChatFlow is the **backend** ("the server side") for a chat app — think WhatsApp or Slack. A
*backend* is the part you don't see: it runs on servers, stores data, and answers requests from
the app on your phone or in your browser (the *frontend*).

ChatFlow lets users:
- **Sign up and log in.**
- **Chat 1-on-1 or in groups**, in **real time** (messages appear instantly, no refresh).
- **Send media** (images, videos, files) and see **thumbnails** (small previews).
- See who's **online** and who's **typing**.
- Get **notifications** when they're away.
- Add **friends**.
- **Search** their messages — both by keyword *and* by meaning.
- Use **AI features** — ask a question about a conversation, or get a summary.

The interesting part (and the reason this project exists as a learning piece) is **how** it's
built: not as one big program, but as several smaller programs that cooperate. That style is
called **microservices**, and §2 explains what that means and why anyone would do it.

---

## 2. Core concepts, explained simply

These are the building blocks. Each gets a plain-language definition, an everyday analogy, and how
ChatFlow uses it. If you already know one, skim it.

### 2.1 Client and server
- **Client** = the program that *asks* for things (the phone app, the web page).
- **Server** = the program that *answers* (our backend).

Analogy: the client is a customer; the server is the kitchen. The customer orders; the kitchen
prepares and sends back the dish.

### 2.2 API and REST (how the client talks to the server)
An **API** ("Application Programming Interface") is the **menu of things you can ask the server to
do**. Each item on the menu is an **endpoint** — a specific address for a specific action.

ChatFlow uses **REST**, a common style where each endpoint is a URL plus a verb:
- `POST /api/auth/login` — "log me in" (`POST` = "here's some data, do something with it").
- `GET /api/conversations/{id}/messages` — "give me the messages in this conversation" (`GET` =
  "fetch something"). `{id}` is a placeholder you fill in.

The verbs you'll see: **GET** (read), **POST** (create / do), **PUT** (update), **DELETE** (remove).

### 2.3 HTTP request and response
When a client calls an endpoint, it sends an **HTTP request** and gets back an **HTTP response**.

Analogy: mailing a letter (request) and getting a reply (response). Each request is independent —
the server doesn't "stay on the line."

A request/response carries:
- a **URL** and **verb** (which endpoint),
- **headers** (extra info, like "who am I" — see tokens below),
- a **body** (the data, usually in **JSON** — see next).

### 2.4 JSON (the data format)
**JSON** is just text that represents data, readable by humans and machines:
```json
{ "username": "alice", "message": "hello!" }
```
Curly braces hold `"key": value` pairs. Almost everything sent between client and server (and
between our services) is JSON.

### 2.5 Authentication with JWT tokens (proving who you are)
The server needs to know *who* is making each request — but checking your username/password on
every single request would be slow and unsafe. Solution: when you log in once, the server gives
you a **token** — specifically a **JWT** ("JSON Web Token").

Analogy: at a festival you show your ID once at the gate and get a **wristband**. After that,
guards just glance at the wristband. The JWT is that wristband — a string the client sends on
every request in a header: `Authorization: Bearer <token>`.

The token is **digitally signed** with a secret key, so the server can tell it's genuine and
hasn't been tampered with — without storing anything or re-checking your password. ChatFlow's token
says "I am user X" and expires after 24 hours.

### 2.6 HTTP vs WebSocket (why chat needs something extra)
Plain HTTP is "ask, get an answer, hang up." That's fine for loading a page, but bad for chat: how
would the server *push* a new message to you the instant it arrives? With plain HTTP, your app
would have to keep asking "any new messages? any new messages?" forever.

A **WebSocket** fixes this. It's a **connection that stays open** in both directions, like a phone
call that doesn't hang up. Once open, the server can send you a message the moment it happens, and
you can send messages without re-establishing anything.

ChatFlow uses HTTP (REST) for normal actions (log in, load history) and **WebSockets** for the
live stuff (a new message appearing, someone typing, presence changes).

### 2.7 Monolith vs microservices (why there are many programs)
A **monolith** is one big program that does everything. Simple to start, but as it grows: one
small change means redeploying the whole thing, one heavy feature (like AI) can slow everything
down, and the code gets tangled.

**Microservices** split that one program into several smaller ones, each owning one job, talking
over the network. Analogy: instead of one giant restaurant doing everything, a **food court** of
specialized stalls — a pizza stall, a sushi stall, a drinks stall. Each can be improved or scaled
on its own.

ChatFlow began as a monolith and was gradually split — a well-known approach called the
**"strangler fig"** pattern (grow the new services around the old monolith until they replace its
responsibilities). The original program, now slimmed down, is `chatflow-core`. The split-off
pieces are `chatflow-ai`, `chatflow-media`, `chatflow-realtime`, and a front-door called
`chatflow-gateway`.

### 2.8 Database and PostgreSQL (where data lives)
A **database** stores data permanently and lets you query it. **PostgreSQL** ("Postgres") is the
specific database ChatFlow uses. Picture a set of **tables** (like spreadsheets): a `users` table,
a `messages` table, a `conversations` table, etc., with rows and columns.

We use **Flyway** to manage the database's structure. Flyway runs numbered **migration** scripts
(`V1`, `V2`, …) so every developer's database has the exact same shape. (First-boot note: the very
first migration builds the whole schema fresh, so wipe your dev database once before the first run.)

### 2.9 Kafka (the event bus / message broker)
This one is new to most beginners but central here. **Kafka** is a **message broker**: a system
where one program can announce "this just happened!" and other programs that care can react —
*without the announcer needing to know who's listening.*

Analogy: a **conveyor belt** in a sushi restaurant, or a **notice board**. A service drops a note
("a new message was created") onto the belt/board (a Kafka **topic**). Other services watch that
topic and pick up the notes they care about, whenever they're ready. The announcer and the
listeners are **decoupled** — neither waits on the other.

Why this matters: when you send a chat message, core shouldn't have to *wait* for the AI service
to embed it or for the thumbnail worker to run. It just announces "message created" to Kafka and
moves on; the other services do their slow work in the background.

Vocabulary:
- **Topic** — a named stream of events (e.g. `chatflow.outbox.events`).
- **Producer** — a service that puts events onto a topic.
- **Consumer** — a service that reads events from a topic.
- **Consumer group** — lets each service track its own reading position independently, so everyone
  sees every event.
- **"At least once"** — Kafka may deliver the same event twice (rare, but possible), so consumers
  must handle duplicates safely (see "idempotent" in the glossary).

### 2.10 Redis (fast shared memory + broadcast)
**Redis** is a very fast in-memory data store. ChatFlow uses one specific Redis feature:
**pub/sub** ("publish/subscribe") — a **broadcast channel**.

Analogy: an **intercom**. One service "publishes" a message on a channel; every service
"subscribed" to that channel hears it instantly. ChatFlow uses a Redis channel called
`chat:relay` to shuttle outbound chat frames to whichever server is holding a given user's
WebSocket. (Why needed? With many server instances, the user you want to deliver to might be
connected to a *different* instance than the one that has the new message — Redis broadcasts so the
right instance picks it up.)

### 2.11 Object storage: S3 / MinIO (where files live)
Databases are bad at storing big files (photos, videos). For those we use **object storage** — a
service built to hold **files** ("objects") and hand them back by a key (a kind of filename/path).

Amazon's version is called **S3**. **MinIO** is a free, S3-compatible version you can run on your
own machine for development — same commands, no cloud account. ChatFlow stores the original upload
and its thumbnail in this object store. The chat database only stores a *reference* (the key), not
the bytes.

### 2.12 Embeddings and vector search (search by meaning)
Normal search is **keyword** search: it matches the exact words you typed. But "the meeting got
moved" and "they rescheduled our call" mean the same thing with no shared keywords.

**Embeddings** solve this. An AI model turns a piece of text into a list of numbers (a **vector**)
that captures its *meaning*. Texts with similar meaning get vectors that are **close together** —
imagine each message as a dot on a map, where related ideas cluster near each other. **Vector
search** then means "find the dots nearest to my question's dot."

ChatFlow's AI service stores these vectors in a special Postgres extension called **pgvector** and
uses them for semantic search and for the "ask about this conversation" feature.

### 2.13 The transactional outbox (never lose an event)
Here's a subtle problem. When you send a message, core must do two things: (1) save the message to
the database, and (2) announce "message created" to Kafka. What if it saves the message but then
*crashes before announcing*? The AI service never embeds it; the message is silently missing from
search. What if it announces but the save rolls back? Now there's a ghost event for a message that
doesn't exist.

The **transactional outbox** pattern fixes this: instead of talking to Kafka directly, core writes
the event into a special `outbox_events` **table in the same database save**. Saving the message
and recording "I need to announce this" happen together — either both succeed or both fail. A
separate background job then reads the outbox table and publishes to Kafka, marking each row done.

Analogy: rather than running to the post office mid-task (and maybe forgetting), you write the
outgoing letter into the *same notebook* as your work. Later, a courier empties the notebook's
outbox page. Nothing gets lost, nothing gets sent for work you undid.

### 2.14 Circuit breakers and idempotency (staying reliable)
Two reliability ideas you'll see named in the code:
- **Circuit breaker** — like an electrical fuse. If one service keeps failing (say the AI service
  is down), the breaker "trips" and we stop hammering it, returning a sensible fallback instead
  (e.g. search falls back to keyword-only) so the whole app doesn't freeze.
- **Idempotent** — an operation safe to repeat. Pressing an elevator button twice doesn't summon
  two elevators. Because Kafka can deliver an event twice, consumers are written so that handling
  the same event twice has no extra effect.

---

## 3. The big picture: all the pieces

ChatFlow is built as **seven modules** managed together by Maven (a build tool). Five are
**runnable services** (programs with a "start" button); two are **shared libraries** (reusable
code other modules include, not run on their own).

```
                                   ┌──────────────────────────────┐
   browser / phone  ───HTTPS────▶  │   chatflow-gateway  :8088    │   public door for HTTP
                          ▲        │   • routes requests          │
                          │        │   • checks your token        │
                          │        └───────┬───────────────┬──────┘
                          │  WS (direct,    │              │ /ai/**
                          │  bypasses       │ /api/**
                          │  gateway)       │              │
                          │                 ▼              ▼
        ┌─────────────────┴──────┐   ┌──────────────┐  ┌──────────────┐
        │  chatflow-realtime     │   │ chatflow-core│  │ chatflow-ai  │
        │  :8083                 │   │ :8080        │  │ :8081        │
        │  holds the WebSockets, │◀─▶│ the main app │  │ embeddings,  │
        │  pushes live messages  │   │ (most logic) │  │ search, RAG, │
        └────────────────────────┘   │              │  │ summaries    │
                          ▲          └───┬──────┬───┘  └──────┬───────┘
                          │ chat:relay   │ events│ (Kafka)    │ has its OWN
                          │ (Redis)      │       ▼            │ vector database
                          │           ┌──────────────┐        │
                          └───────────│ chatflow-    │        │
                                      │ media :8082  │        │
                                      │ makes        │        │
                                      │ thumbnails   │        │
                                      └──────────────┘        │
   Shared libraries (code, not running services):             │
     • chatflow-contracts — the shapes of events/messages ────┘ (everyone agrees on these)
     • chatflow-storage   — the file-storage code (core + media both use it)
```

### What each module does
| Module | Port | Plain-English job |
|--------|------|-------------------|
| **chatflow-gateway** | 8088 | The **front door for HTTP.** Every REST/AI request from the outside world hits this first — it checks your token and forwards you to the right service. (The live WebSocket is the exception: it connects straight to chatflow-realtime on `:8083`.) | 
| **chatflow-core** | 8080 | The **main application** — and the original monolith. Handles login, conversations, messages, friends, notifications, presence, and recording media. Owns the main database. Most logic lives here. |
| **chatflow-ai** | 8081 | The **AI brain.** Stores message "meaning vectors" in its **own** database, powers semantic search, answers questions about a chat (RAG), and writes summaries. |
| **chatflow-media** | 8082 | The **thumbnail factory.** A small worker that makes image/video previews in the background so uploads stay fast. |
| **chatflow-realtime** | 8083 | The **switchboard.** Holds users' live WebSocket connections and delivers messages to them instantly. |
| **chatflow-contracts** | *(library)* | The **shared dictionary** — the exact shape of every event and cross-service message, so services agree on the format. |
| **chatflow-storage** | *(library)* | The **shared file-cabinet code** — how to save/read files on local disk or in S3/MinIO. Used by core (uploads) and media (thumbnails). |

Each module also has its own `README.md` with deeper detail — linked from the migration notes.

### The supporting infrastructure (the services *our* services rely on)
These run alongside ChatFlow (Docker Compose starts them for you):
| Infrastructure | Port | What it's for (recall §2) |
|----------------|------|---------------------------|
| **PostgreSQL** | 5432 | core's main database |
| **PostgreSQL + pgvector** | 5433 | ai's separate database (for meaning-vectors) |
| **Redis** | 6379 | presence + the `chat:relay` broadcast channel |
| **Kafka** | 9092 | the event bus between services |
| **MinIO (S3)** | 9000 (console 9001) | file storage for media |
| **Jaeger** | 16686 | viewing **traces** (following one request across services) |
| **Prometheus / Grafana** | 9090 / 3000 | metrics + dashboards (graphs of how the system is doing) |

---

## 4. Patterns you'll see everywhere

Before the flows, here are five recurring ideas — most were introduced in §2, now stated as "how
ChatFlow applies them."

1. **Two kinds of "who are you?"**
   - **User token (JWT):** an end user's wristband (§2.5). The gateway checks it at the door, and
     core/ai check it *again* themselves (belt-and-suspenders).
   - **Internal token:** a shared secret password (`X-Internal-Token` header) that services use to
     trust *each other* on private `/internal/...` endpoints — e.g. when ai asks core "is this user
     allowed in this conversation?"

2. **The outbox is how core tells everyone else things happened** (§2.13). Messages created, media
   uploaded, conversations deleted — all become events on Kafka via the outbox.

3. **ai keeps its own database** and is *fed* by events rather than reading core's tables. Core
   packs everything ai needs *into* the event, so ai never has to call back for the message text.

4. **Live delivery uses the Redis relay** (§2.10): outbound chat frames go core → `chat:relay` →
   the server holding your socket → your screen.

5. **Everything talks carefully:** synchronous service-to-service calls have **timeouts, circuit
   breakers, and fallbacks** (§2.14), so one slow service can't freeze the app.

---

## 5. Following real requests through the system

This is the heart of the doc. Each flow names the **actual endpoints** and traces the path step by
step, explaining *what just happened and why*. (HTTP endpoints are shown without the `gateway:8088`
prefix; in production REST/AI calls always go through the gateway. The WebSocket flow in §5 is the
exception — it connects directly to chatflow-realtime on `:8083`.)

### Flow 0 — Sign up and log in
**Goal:** get a token so you can do everything else.
```
1. client → POST /api/auth/register {username, password}   → gateway → core
   core saves a new user (password stored securely, never in plain text).

2. client → POST /api/auth/login {username, password}      → gateway → core
   core checks the password, then creates a signed JWT token that says "this is user X".

3. client ← { "token": "eyJ..." }
   The app stores this token and attaches it to every future request.
```
**Why `/api/auth/**` is special:** it's the *only* path the gateway lets through **without** a
token — you obviously can't have a token before you log in.

### Flow 1 — Start a conversation and load its history
```
client → POST /api/conversations/direct {otherUserId}   → core   (start/get a 1-on-1 chat)
client → POST /api/conversations/group  {name, members} → core   (create a group)
client → GET  /api/conversations/{id}/messages          → core   (load past messages, in pages)
```
Every one of these carries your token. The gateway verifies it; core verifies it again **and**
checks you're actually a member of that conversation before returning anything.

### Flow 2 — Send a message in real time ⭐ (the signature flow)
This uses the **WebSocket** (§2.6). Over a WebSocket, both sides exchange small JSON
**frames** shaped like `{ "type": "...", "requestId": "...", "payload": {...} }`. `type` says what
kind of frame it is; `payload` is the data.

**Step A — open the live connection** (in "external realtime" mode, where `chatflow-realtime`
holds the sockets):
```
client → WS connect: ws://…:8083/ws?token=<JWT>   → chatflow-realtime
  realtime checks your token during the handshake and remembers your connection ("session").
  Since this is your first open socket, realtime tells core (POST /internal/realtime/connect),
  and core marks you ONLINE and replays anything you missed while away.
```

**Step B — you send a message:**
```
1. client → WS frame { type:"SEND_MESSAGE", payload:{ conversationId, content:"hi!" } } → realtime

2. realtime forwards it to core:
   POST /internal/realtime/inbound   (with the internal-token password)
   → core figures out it's a SEND_MESSAGE and calls ChatService.sendMessage(...)

3. Inside core, in ONE database transaction:
   • insert the message into the messages table (giving it the next sequence number)
   • write outbox events (e.g. "message.created") — for notifications, AI embedding, etc.

4. To deliver it live, core publishes a ready-to-send frame to the Redis "chat:relay" channel,
   addressed to each recipient.

5. Whichever realtime server holds a recipient's socket hears the relay broadcast and pushes:
   recipient ← WS frame { type:"MESSAGE", payload:{…the new message…} }
```
**The big idea:** core does the *durable* work (save + announce) and hands off *delivery* to the
relay. The sender isn't blocked waiting on the AI service, notifications, or the recipient's
device.

**Frame types you'll meet:**
- **You can send** (inbound): `SEND_MESSAGE`, `MESSAGE_DELIVERED` (ack you got it), `CONVERSATION_OPEN`,
  `MARK_READ`, `TYPING`. (`PING` is answered instantly by the socket layer with `PONG` to keep the
  connection alive — it never reaches core.)
- **You can receive** (outbound): `MESSAGE`, `MEDIA_MESSAGE`, `MEDIA_THUMBNAIL_READY`, `TYPING`,
  `PRESENCE`, `NOTIFICATION`, `MESSAGE_ACK`, `SEEN_UPDATE`, plus friend/group updates.

> *Simpler "embedded" mode:* if `chatflow-realtime` isn't used, **core itself** serves `/ws`. The
> flow is the same minus the realtime → core hop. The relay is still used so multiple core
> instances can reach each other.

### Flow 3 — Upload an image and get a thumbnail
A great example of **doing slow work in the background.**
```
1. client → POST /api/messages/media  (the file + conversationId, as "multipart" upload)
            → gateway → core

2. core, in ONE transaction:
   • create the message + a media record in the database
   • save the ORIGINAL file to object storage (MinIO/S3) using the shared chatflow-storage code
   • drop an outbox event "media.processing_requested" (this carries the file's storage KEY,
     NOT the file bytes — so big files never travel through Kafka)
   • immediately push a MEDIA_MESSAGE frame so chat members see the image right away

3. (background) chatflow-media is watching that Kafka topic. It picks up the event and:
   • reads the original file from the SAME object store (by its key)
   • makes a small thumbnail (Thumbnailator for photos; ffmpeg grabs a frame for videos)
   • saves the thumbnail back to the store
   • announces "media.thumbnail_ready" on another Kafka topic

4. core hears "thumbnail_ready", saves the thumbnail's URL on the media record, and pushes:
   members ← WS frame { type:"MEDIA_THUMBNAIL_READY", … }
```
Why split it: thumbnailing (especially video) is slow and CPU-heavy. If core did it inline, every
upload would lag. Offloading it keeps chatting snappy. Notice the worker never receives the file
*through* Kafka — only its storage key — and reads the bytes straight from shared storage.

To actually view a file later: `GET /api/messages/media/{id}/url` returns a **temporary link**
(valid for a limited time), and only if you're a member of that conversation.

### Flow 4 — Ask the AI about a conversation (RAG)
**RAG** = "Retrieval-Augmented Generation": *retrieve* the relevant messages, then ask an AI to
*generate* an answer using them.
```
client → POST /ai/conversations/{id}/ask { question:"what did we decide about pricing?" }
         → gateway → chatflow-ai

chatflow-ai:
  1. re-checks your token.
  2. asks core "is this user a member here?"  (GET /internal/conversations/{id}/participants/{userId})
  3. turns your question into a meaning-vector and searches ITS OWN pgvector database for the
     most relevant messages in that conversation (semantic search, §2.12).
  4. sends those messages + your question to the LLM (Anthropic's Claude) to compose an answer.

client ← { "answer": "You agreed to a $20/month tier…" }
```
**How did ai have the messages?** Earlier, *every* message core created emitted a
`message.embedding_requested` event. ai consumed those in the background, turned each into a vector,
and stored it. So "ask" reads only ai's own database — it never has to fetch message text from core
at question time.

### Flow 5 — Summarize what you missed
```
client → GET /ai/conversations/{id}/summary   → gateway → chatflow-ai

chatflow-ai:
  • fetches your UNREAD messages from core (GET /internal/conversations/{id}/transcript/unread)
  • asks the LLM to summarize them
client ← { "summary": "3 people discussed the launch date and moved it to Friday…" }
```
**Why fetch from core here (instead of ai's vector store)?** A summary needs *every* unread message
in order, not just the few most "similar" to something — so ai grabs the actual transcript from
core, which owns that data.

### Flow 6 — Search messages (keyword + meaning, combined)
```
client → GET /api/messages/search/hybrid?q=launch date   → gateway → core

core:
  • keyword search in its own Postgres (exact word matches)
  • asks ai for semantic matches (POST /internal/embeddings/search → returns message IDs + scores)
  • merges and ranks both sets, then returns the full messages
client ← ranked results
```
**Resilience in action:** if ai is down, core's **circuit breaker** trips and search quietly falls
back to **keyword-only** instead of erroring. You still get results, just not the meaning-based
ones.

### Flow 7 — Friends, notifications, presence (quick tour)
- **Friends:** `POST /api/friends/requests` to ask, `.../accept` or `.../decline` to respond,
  `DELETE /api/friends/{userId}` to unfriend. Accepting pushes a live `FRIEND_REQUEST_ACCEPTED`
  frame to the other person.
- **Notifications:** when you receive a message while offline, core records a **durable**
  notification (via the outbox). Endpoints: `GET /api/notifications/unread-count`,
  `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all`.
- **Presence & typing:** "online/offline" is driven by your socket connecting/disconnecting.
  "Typing…" is a `TYPING` frame you send, which core fans out to the other participants.
  Check status with `GET /api/users/{userId}/presence` and
  `GET /api/conversations/{id}/presence`.

---

## 6. How the code is organized (so you can read it)

You now understand the system. This section bridges to the **actual files**, so you can open the
project and know where you are. Good news: every service follows the **same simple recipe**.

### 6.1 The four layers (the recipe for handling a request)
Inside a service, a request flows through up to four layers. Each has one job:

```
   HTTP/WS request
        │
        ▼
 ┌──────────────┐   "the waiter" — receives the request, hands back the reply.
 │  Controller  │   Does NO real logic. Validates input, calls a Service.        (…Controller.java)
 └──────┬───────┘
        ▼
 ┌──────────────┐   "the chef" — the actual business logic lives here.
 │   Service    │   Decides what happens; calls Repositories to read/write data. (…Service.java)
 └──────┬───────┘
        ▼
 ┌──────────────┐   "the pantry clerk" — reads/writes the database. Nothing else.
 │  Repository  │   In Spring you just declare an INTERFACE; Spring writes it.   (…Repository.java)
 └──────┬───────┘
        ▼
 ┌──────────────┐   "the label on the jar" — a Java class mapped to a DB table.
 │   Entity     │   One object = one row.                                        (…entity/*.java)
 └──────────────┘

   DTO = "Data Transfer Object": the shape of the JSON going in/out (…dto/*.java).
         We keep DTOs separate from Entities so the API's shape and the DB's shape
         can change independently.
```

Why layers? So each file has *one* reason to change, and you always know where to look: a URL
problem → a Controller; a "what should happen" problem → a Service; a "how is it stored" problem →
a Repository/Entity.

### 6.2 How Spring wires it together (annotations you'll see)
ChatFlow uses **Spring Boot**. A few annotations explain most files:
- `@RestController` — this class handles HTTP endpoints.
- `@Service`, `@Component` — this class is business logic / a reusable bean Spring manages.
- `@Repository` (or just extending `JpaRepository`) — database access.
- `@PostMapping("/login")`, `@GetMapping(...)` — "this method handles POST/GET on this path."
- `@RequestBody @Valid` — "fill this argument from the request's JSON, and validate it."

**Dependency injection** (how objects get their helpers): you'll see
```java
@RequiredArgsConstructor          // Lombok: generates a constructor for the final fields
public class AuthService {
    private final UserRepository userRepository;   // Spring "injects" the real one at startup
    private final JwtService jwtService;
}
```
You don't create these helpers with `new` — Spring builds one of each and hands it to whoever
declares it as a `private final` field. So to learn what a class depends on, **read its
`private final` fields.**

### 6.3 The package layout (where files live)
Code is grouped **by feature** ("vertical slices"), not by layer. core's top-level packages:

```
com.chatflow
├── auth          ── login/registration, JWT, security
├── user          ── the User entity + repository
├── conversation  ── conversations & messages (the heart)
├── friend        ── friend requests
├── notification  ── notifications
├── presence      ── online/offline
├── typing        ── typing indicators
├── media         ── media messages (records + upload)
├── realtime      ── the WebSocket handler + inbound dispatch
├── infra         ── plumbing: outbox, redis relay, websocket sessions, idempotency
└── config        ── app-wide setup (security, websocket, scheduling, error handling)
```

…and **inside one feature**, the four layers appear as sub-packages:

```
com.chatflow.conversation
├── controller   ── ConversationController, MessageSearchController, …   (waiters)
├── service      ── ChatService, ConversationService, …                 (chefs)
├── repository   ── MessageRepository, ConversationRepository, …         (pantry)
├── entity       ── Message, Conversation, ConversationParticipant, …    (table maps)
└── dto          ── SendMessageRequest, MessageResponse, …               (JSON shapes)
```

**This means you can usually *guess* a filename.** "Where's the code that sends a message?" →
conversation feature → it's logic → `conversation/service/ChatService.java`. The naming is
consistent: `XController`, `XService`, `XRepository`, request DTOs end in `Request`, responses in
`Response`.

### 6.4 Worked trace #1 — the login request, mapped to files
Recall Flow 0. Here it is again, but pointing at the actual classes:
```
POST /api/auth/login {username, password}
   │
   ▼  auth/controller/AuthController.java        @PostMapping("/login") → calls authService.login(request)
   ▼  auth/service/AuthService.java              checks the password, then asks JwtService for a token
   ▼  user/repository/UserRepository.java        findByUsername(...) → loads the user row
   ▼  user/entity/User.java                      the loaded row, as a Java object
   ▼  auth/security/JwtService.java              generateToken(userId) → the signed JWT
   ▲  auth/dto/AuthResponse.java                 the {token, …} shape sent back as JSON
```
Open those six files in order and you've read an entire feature end to end. Notice
`UserRepository` is just an **interface** with `findByUsername(String)` — you never write the SQL;
Spring Data generates it from the method name. That's the norm across the project.

### 6.5 Worked trace #2 — a real-time "send message", mapped to files
Recall Flow 2. In code:
```
WS frame {type:"SEND_MESSAGE", …}
   │
   ▼  realtime/ChatWebSocketHandler.java         (embedded mode) receives the frame
        — or chatflow-realtime forwards it to core's realtime/InternalRealtimeController.java
   ▼  realtime/RealtimeInboundService.java       dispatch(): a switch on the frame type → SEND_MESSAGE
   ▼  conversation/service/ChatService.java       sendMessage(): saves the message + writes outbox events
   ▼  conversation/repository/MessageRepository.java   inserts the row, allocates the sequence number
   ▼  infra/outbox/OutboxWriter.java              records "message.created" etc. in the same transaction
        … later the outbox is published to Kafka, and the message is pushed to recipients via the relay
```
The `infra/` package is the **plumbing** the features stand on — you'll dip into it to understand
*how* things move (outbox, relay, websocket sessions), but the business decisions live in the
feature `service` classes.

### 6.6 The other services (smaller, same idea)
core is the big one; the rest are smaller and follow the same Controller/Service/Repository shape
(some skip layers they don't need):
- **chatflow-ai** — `rag/`, `summary/`, `search/`, `embedding/` packages. Controllers
  (`RagController`, `SummaryController`) → services → its own pgvector database via JDBC. Plus an
  `EmbeddingEventConsumer` that listens to Kafka.
- **chatflow-media** — no controllers worth speaking of; it's a **worker**. The entry point is
  `MediaProcessingConsumer` (a Kafka listener) → `ThumbnailService`.
- **chatflow-realtime** — `ws/` (the socket handler + session registry), `relay/` (Redis
  subscriber), `client/` (the calls to core). No database.
- **chatflow-gateway** — tiny: a routing config + `EdgeAuthFilter` (token check). No business logic.
- **chatflow-contracts / chatflow-storage** — libraries: just classes (event shapes / storage
  code), no endpoints.

### 6.7 How to find anything fast
- **From a URL to the code:** search the project for the path string (e.g. `"/login"` or
  `"conversations"`); it lands you on the `@…Mapping` in a Controller.
- **From a frame type to the code:** search for the `type` value (e.g. `SEND_MESSAGE`) → the
  `switch` in `RealtimeInboundService`.
- **From an event to its handler:** search the event name (e.g. `MediaProcessingRequested` or
  `message.embedding_requested`) → the `@KafkaListener` that consumes it.
- **Follow the types:** click into a method's return type or a `private final` field to jump to the
  next layer. Reading top-down (Controller → Service → Repository) almost always works.

### 6.8 The guardrail that keeps it tidy
core has an automated test, `ModuleBoundaryTest` (an **ArchUnit** test), that **fails the build**
if someone makes the layers depend on each other the wrong way (e.g. the plumbing reaching into a
feature). You don't have to memorize the rules — if you wire something backwards, the test tells
you. That's why the structure above stays consistent over time.

---

## 7. Running it yourself

From the project root (`chatflow-backend/`):

```bash
# 1. Build all seven modules at once (./mvnw is the bundled Maven — no install needed):
./mvnw package

# 2a. Start just the infrastructure (databases, Kafka, Redis, MinIO, Jaeger, …):
docker compose up -d
#     then run a service (e.g. core) from your IDE, or:
./mvnw -pl chatflow-core -am spring-boot:run

# 2b. …OR run EVERYTHING (infra + all services) in containers:
docker compose --profile apps up --build
```

Run one module's tests:
```bash
./mvnw -pl chatflow-core -am -Dsurefire.failIfNoSpecifiedTests=false test
```

**Who listens where:** clients hit the **gateway at `:8088`**. Behind it: core `:8080`, ai `:8081`,
media `:8082`, realtime `:8083`. Handy UIs: **Jaeger** (traces) `http://localhost:16686`,
**Grafana** (dashboards) `http://localhost:3000`, **MinIO console** `http://localhost:9001`.

**Two beginner gotchas:**
1. **Wipe the dev database once** before the very first run (the first migration builds the whole
   schema fresh).
2. The background features (AI embeddings, thumbnails) only fully work when the event bus is on
   (`APP_OUTBOX_TRANSPORT=kafka`) **and** the relevant worker service is running. Otherwise core
   falls back to simpler behaviour (e.g. search becomes keyword-only).

---

## 8. Glossary (quick reference)

| Term | One-line meaning |
|------|------------------|
| **Backend / frontend** | The server side (this project) / the app the user sees. |
| **Client / server** | The asker / the answerer. |
| **API** | The menu of actions the server offers. |
| **Endpoint** | One specific action's address, e.g. `POST /api/auth/login`. |
| **REST** | The style of API ChatFlow uses (URLs + verbs GET/POST/PUT/DELETE). |
| **HTTP request/response** | One ask-and-answer exchange (like a letter and reply). |
| **JSON** | The text format for data: `{ "key": value }`. |
| **JWT / token** | Your signed "wristband" proving who you are after login. |
| **WebSocket** | A connection that stays open both ways, for live updates. |
| **Frame** | One JSON message over a WebSocket: `{type, requestId, payload}`. |
| **Monolith** | One big program doing everything. |
| **Microservices** | Many small programs, each with one job, cooperating. |
| **Strangler fig** | Gradually replacing a monolith with services around it. |
| **Module / reactor** | One sub-project / the Maven setup that builds them together. |
| **Library vs service** | Reusable code included by others / a program you run. |
| **Database / Postgres** | Where data is stored permanently. |
| **Flyway / migration** | Tool/scripts that build & version the database structure. |
| **Kafka / topic / event** | The event bus / a named stream / a "this happened" note. |
| **Producer / consumer / consumer group** | Puts events on a topic / reads them / tracks its own position. |
| **Redis / pub-sub / chat:relay** | Fast store / broadcast channel / the channel for live chat frames. |
| **Object storage / S3 / MinIO** | File storage / Amazon's version / a local, free, compatible version. |
| **Embedding / vector / pgvector** | Text turned into meaning-numbers / that list of numbers / the DB extension storing them. |
| **Semantic vs keyword search** | By meaning / by exact words. |
| **RAG** | Retrieve relevant text, then have an AI answer using it. |
| **LLM** | "Large Language Model" — the AI that writes answers/summaries (here, Claude). |
| **Transactional outbox** | Saving an event in the same DB transaction so it's never lost. |
| **Internal token** | The shared password services use to trust each other. |
| **Circuit breaker** | A fuse that trips to stop hammering a failing service. |
| **Idempotent** | Safe to repeat (handling an event twice changes nothing extra). |
| **Trace (Jaeger)** | Following one request as it hops across services. |

### Where to go next
1. Re-read §5 with the code open. Pick **Flow 2 (send a message)**, find `ChatService.sendMessage`
   in `chatflow-core`, and set a breakpoint.
2. Skim each module's `README.md` for depth on that one service.
3. Read [`ARCHITECTURE.md`](ARCHITECTURE.md) for the rules that keep the modules tidy.
4. For the full history of *why* it's split this way, read
   [`microservices-migration.md`](microservices-migration.md).

You've got this. Welcome to the team!

---

## 9. Appendix — Complete API reference (every endpoint)

The exhaustive list, so nothing is left implicit. Unless noted **Internal**, an endpoint is a
**public** REST call: the client sends it through the **gateway (`:8088`)** with a user token
(`Authorization: Bearer <JWT>`). `{…}` are path placeholders. "Who serves it" is the service that
ultimately handles the request behind the gateway.

### Auth — `chatflow-core`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Create an account. **(No token required.)** |
| POST | `/api/auth/login` | Log in; returns a JWT token. **(No token required.)** |

### Conversations & messages — `chatflow-core`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/conversations` | List my conversations (the inbox). |
| POST | `/api/conversations/direct` | Get or create the 1-on-1 conversation with another user. |
| POST | `/api/conversations/group` | Create a group conversation. |
| GET | `/api/conversations/{id}` | Get one conversation's details. |
| GET | `/api/conversations/{id}/messages` | Page back through message history. |
| GET | `/api/conversations/{id}/messages/after` | Get messages *after* a cursor — used to catch up / re-sync after reconnecting. |
| DELETE | `/api/conversations/{id}` | Delete a (group) conversation. |
| POST | `/api/conversations/{id}/participants` | Add a member to a group. |
| DELETE | `/api/conversations/{id}/participants/{userId}` | Remove a member from a group. |
| PUT | `/api/conversations/{id}/participants/{userId}/role` | Change a member's role (e.g. admin/member). |
| POST | `/api/conversations/{id}/transfer-ownership` | Hand group ownership to another member. |

### Search — `chatflow-core` (calls `chatflow-ai` for the semantic half)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/messages/search` | Keyword search across my messages. |
| GET | `/api/messages/search/hybrid` | Keyword **+** semantic search, merged & ranked (degrades to keyword-only if ai is down). |

### Media — `chatflow-core` (thumbnails generated by `chatflow-media`)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/messages/media` | Upload a file as a media message (multipart form). |
| GET | `/api/messages/media/{id}` | Get a media message's metadata. |
| GET | `/api/messages/media/{id}/url` | Get a time-limited (signed) link to download the file. |
| DELETE | `/api/messages/media/{id}` | Delete a media message. |

### Friends — `chatflow-core`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/friends` | List my friends. |
| POST | `/api/friends/requests` | Send a friend request. |
| GET | `/api/friends/requests/received` | List friend requests sent **to** me. |
| GET | `/api/friends/requests/sent` | List friend requests **I** sent. |
| POST | `/api/friends/requests/{friendshipId}/accept` | Accept a request. |
| POST | `/api/friends/requests/{friendshipId}/decline` | Decline a request. |
| DELETE | `/api/friends/{userId}` | Unfriend someone. |

### Notifications — `chatflow-core`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/notifications` | List my notifications (feed; supports `cursor` + `limit` for paging). |
| GET | `/api/notifications/unread-count` | How many are unread (the badge number). |
| POST | `/api/notifications/{id}/read` | Mark one as read. |
| POST | `/api/notifications/read-all` | Mark all as read. |
| DELETE | `/api/notifications/{id}` | Delete one. |

### Presence — `chatflow-core`
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users/{userId}/presence` | Is this user online? |
| GET | `/api/conversations/{id}/presence` | Who's online in this conversation? |

### AI — `chatflow-ai`
| Method | Path | Description |
|--------|------|-------------|
| POST | `/ai/conversations/{id}/ask` | Ask a question about a conversation (RAG). |
| GET | `/ai/conversations/{id}/summary` | Summarize my unread messages in a conversation. |

### Internal (service-to-service only — guarded by the `X-Internal-Token` secret, **not** a user JWT)
| Method | Path | Served by | Called by | Purpose |
|--------|------|-----------|-----------|---------|
| GET | `/internal/conversations/{id}/participants/{userId}` | core | ai | Membership check (for RAG). |
| GET | `/internal/conversations/{id}/transcript/unread` | core | ai | Fetch the unread transcript (for summaries). |
| POST | `/internal/realtime/connect` | core | realtime | A user's first socket opened → mark online, replay. |
| POST | `/internal/realtime/disconnect` | core | realtime | A user's last socket closed → mark offline. |
| POST | `/internal/realtime/inbound` | core | realtime | Forward an inbound WebSocket command (e.g. SEND_MESSAGE). |
| POST | `/internal/embeddings/search` | ai | core | Vector-search hits for hybrid search. |

### WebSocket (not REST)
| Endpoint | Served by | Notes |
|----------|-----------|-------|
| `/ws?token=<JWT>` | `chatflow-realtime` (external mode) **or** `chatflow-core` (embedded mode) | The live channel. Exchanges JSON frames `{type, requestId, payload}` — inbound/outbound `type`s are listed in **Flow 2 (§5)**. |

> Note: `chatflow-media` exposes **no business API** — it's a background worker driven by Kafka
> events. `chatflow-contracts` and `chatflow-storage` are libraries (code only, no endpoints).
> All services also expose Spring Actuator endpoints (`/actuator/health`, `/actuator/info`, etc.)
> for health checks and metrics.

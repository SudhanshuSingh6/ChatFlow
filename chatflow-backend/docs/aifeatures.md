# Feature: AI — Semantic Search, Summarizer & RAG ("Ask your chat history")

## What we're building
Three layered AI capabilities on top of the existing unified `Message` model, reusing the
infrastructure we already run (Postgres + Flyway, the transactional outbox, per-participant
read watermarks, conversation-membership authorization, and the new `deleted_at` hygiene).

1. **Semantic search** — find messages by *meaning*, not just literal keyword match.
   Each message is embedded into a vector; queries are embedded the same way and matched
   by cosine similarity in **pgvector**. Embeddings are generated **asynchronously** off a
   **dedicated `MESSAGE_EMBEDDING_REQUESTED` event** (decoupled from `MESSAGE_CREATED`), so
   re-embedding, failure tracking, and model swaps are independent of message creation.
   The vector layer returns a raw **`similarity`** (cosine); the **hybrid** endpoint
   (existing `LIKE` + vector ANN, merged) exposes a **`rankScore`**, since a single "score"
   is ambiguous once two sources are combined.

2. **Summarizer** — "catch me up on what I missed." Goes through a **provider-agnostic
   `ChatCompletionService`** (Claude today; OpenAI / Gemini / a local model tomorrow —
   the architecture doesn't care) to summarize a range of messages, defaulting to
   *everything since my `lastReadSeq` watermark*. The first implementation uses Claude with
   **prompt caching** so follow-up asks are cheap.

3. **RAG — "ask your chat history"** — combine the two: embed the user's question,
   retrieve the top-k most relevant messages with pgvector, then have the
   `ChatCompletionService` answer **grounded in those real messages**, returning
   **citations (message ids) from day one** so answers feel trustworthy, not "ChatGPT over
   my messages." Starts **conversation-scoped** (`/api/conversations/{id}/ask`) before any
   global search. Scales past the context window and avoids hallucination.

### Architecture (data flow)
```
write:  ChatService tx writes BOTH outbox rows atomically:
          ├─ MESSAGE_CREATED            → OutboxDispatcher → notifications (existing)
          └─ MESSAGE_EMBEDDING_REQUESTED → Embedding Worker → pgvector
        (no event spawns another event — both originate in the same transaction)

search: query → embed → pgvector ANN (similarity)  ⨉  LIKE search  → merge → rankScore  (scoped to my convos)

summarize: messages since lastReadSeq → ChatCompletionService (cached transcript) → summary

RAG:   question → embed → top-k messages (pgvector) → ChatCompletionService (grounded) → answer + citations
```

### Shared infrastructure reused
| Concern | Reused from codebase |
|---|---|
| Async embedding generation | existing **outbox** — `ChatService` writes a **`MESSAGE_EMBEDDING_REQUESTED`** row alongside `MESSAGE_CREATED` in the same tx; an embedding worker drains it |
| Vector storage | **Postgres + Flyway** (`V3__` + pgvector) — no new datastore |
| Search authorization | "messages in conversations I belong to" filter in `MessageSearchService` |
| Unread range | per-participant **watermarks** (`lastReadSeq`) |
| Delete hygiene | new `deleted_at` cascade — purged messages/groups must drop their embeddings |
| LLM calls | **provider-agnostic `ChatCompletionService`** (Claude/OpenAI/Gemini/local behind one interface); first impl is Anthropic SDK via the `claude-api` skill (prompt caching, latest models) |

### Models / config (to set)
- Embedding model + dimension (e.g. 1536) → `app.ai.embedding.*`
- Chat LLM behind `ChatCompletionService` → `app.ai.chat.{provider,model,max-tokens}`
  (provider-selectable; default provider `anthropic`, model `claude-opus-4-8`)
- Provider keys via env (`ANTHROPIC_API_KEY`, etc.) + the embedding provider key.

## Subphases

- [x] 1. **pgvector + schema** — Flyway `V3__ai_embeddings.sql`: `create extension vector`, `message_embeddings(message_id pk → messages, embedding vector(N), model, dimensions, embedded_at)`, HNSW cosine index. Metadata columns make future model swaps / re-embedding safe.
- [x] 2. **Embedding client** — `EmbeddingService` wrapping the embedding API; config `app.ai.embedding.{model,dimensions,api-key}`; returns `float[]` + the model/dimension used
- [x] 3. **Embedding event + worker** — `ChatService` writes **both** `MESSAGE_CREATED` **and** `MESSAGE_EMBEDDING_REQUESTED` (new `OutboxEventType`) in the **same transaction** (no event spawns another event). An **embedding worker** branch in `OutboxDispatcher` drains the new type → embeds + upserts the row (`model`/`dimensions`/`embedded_at`). Skip non-text/empty; idempotent on retry; failures/re-embeds tracked independently
- [x] 4. **Backfill job** — one-shot/scheduled task that enqueues `MESSAGE_EMBEDDING_REQUESTED` for messages with no embedding row (batched), so history is searchable
- [x] 5. **Vector search repo + query** — `MessageEmbeddingRepository` cosine query (`embedding <=> :q`) scoped to caller's conversations, `deleted_at IS NULL`, top-k, returning raw **`similarity`** (0–1 cosine) per row
- [x] 6. **Hybrid search endpoint** — extend `MessageSearchService`/`MessageSearchController` to embed the query and merge vector + existing `LIKE` results (deduped). Response uses **`rankScore`** (the merged ranking — unambiguous once two sources are combined), optionally also surfacing raw `similarity` on vector-origin hits: `{ messageId, content, rankScore }`
- [x] 7. **LLM client (provider-agnostic)** — `ChatCompletionService` interface; first impl Anthropic SDK with **prompt caching**; provider/model selectable via `app.ai.chat.{provider,model,max-tokens}` so OpenAI/Gemini/local can drop in later
- [x] 8. **Summarize endpoint** — `POST /api/conversations/{id}/summary`; gather messages since caller's `lastReadSeq` (membership-checked); summary via `ChatCompletionService`; optional cache on conversation
- [x] 9. **RAG endpoint (conversation-scoped first)** — `POST /api/conversations/{id}/ask`: embed question → top-k via pgvector (this conversation) → grounded answer via `ChatCompletionService` **with message-id citations from day one**. Global `POST /api/ask` is a later follow-up
- [x] 10. **Cleanup integration + tests + verify** — purge `message_embeddings` in `DailyCleanupService` message/group cascade; unit tests (mock `EmbeddingService` + `ChatCompletionService`); `./mvnw test`; app boots with V3

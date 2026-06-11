# chatflow-contracts

The **shared wire contract** between ChatFlow's services — the Kafka event payloads and the
sync request/response DTOs that cross service boundaries. Every other module
(`core`, `ai`, `media`, `realtime`) depends on this one; it depends on nothing.

## Design rules

- **Dependency-free by design.** Plain Java `record`s, no framework on the classpath. Shared
  contracts must not drag Spring/Jackson/etc. onto every consumer.
- **Backward-compatible / additive only.** Append fields, never repurpose or remove them, so an
  older consumer keeps deserializing a newer producer's payload.
- Each **event** carries a `public static final String TYPE` — the canonical `event_type`
  written to the outbox and matched by consumers (they filter on it before parsing the payload).

## Events (`events/`)

Asynchronous, fire-and-forget payloads published via the outbox → Kafka and consumed by their
own consumer groups.

| Event | `TYPE` | Producer → Consumer | Fields |
|-------|--------|---------------------|--------|
| `MessageEmbeddingRequested` | `message.embedding_requested` | core → ai | `messageId, conversationId, senderId, senderName, sequenceNumber, content, messageType, createdAt` |
| `ConversationDeleted` | `conversation.deleted` | core → ai | `conversationId` |
| `MediaProcessingRequested` | `media.processing_requested` | core → media | `mediaMessageId, storageKey, messageType, mimeType` |
| `MediaThumbnailReady` | `media.thumbnail_ready` | media → core | `mediaMessageId, thumbnailUrl` |

Notes on intent:
- **`MessageEmbeddingRequested`** is **event-carried state transfer** — it carries everything
  ai needs to embed + populate its denormalized snippet store, so ai never reads core's
  `messages`/`users` tables. That decoupling is the whole point of the split.
- **`MediaProcessingRequested`** carries only the `storageKey`, **not the bytes** — the media
  worker reads the original from the shared object store, so large files never transit Kafka.
- **`ConversationDeleted`** lets ai evict orphaned embeddings when core purges a conversation.

## DTOs (`dto/`)

Synchronous request/response bodies for `/internal/**` service-to-service HTTP calls.

| DTO | Direction | Purpose |
|-----|-----------|---------|
| `EmbeddingSearchRequest` | core → ai | Semantic search request: `query`, `conversationIds` (the caller's visible scope — core owns membership), `limit`. |
| `EmbeddingSearchHit` | ai → core | One hit: `messageId` + `similarity` (cosine, 0–1). core hydrates the message from its own store and merges with keyword results. |
| `ConversationTranscript` | core → ai | Unread transcript for summarization: `messageCount, fromSequence, toSequence, List<Entry>` where `Entry(senderName, content)`. Summary needs the full chronological backlog past the read watermark, so unlike RAG it can't come from ai's embedding store. |

**Authorization stays in core, the vector store stays in ai:** the search DTOs deliberately
split it that way — core decides *which conversations* the caller may search, ai does the
embedding + ANN lookup scoped to them.

## Build

```bash
./mvnw -pl chatflow-contracts install
```

It is consumed as an internal Maven dependency (version managed by the parent reactor pom), so
the other modules declare it without a version. There's nothing to run — it's a pure library.

Relates to the living migration plan in [`../docs/microservices-migration.md`](../docs/microservices-migration.md)
(see §2 for the sync transcript fetch and §4b for event-carried state transfer).

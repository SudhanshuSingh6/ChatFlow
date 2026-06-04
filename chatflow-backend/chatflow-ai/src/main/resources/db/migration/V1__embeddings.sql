-- ---------------------------------------------------------------------------
-- ai-service's own datastore: per-message embeddings for semantic search / RAG.
--
-- Database-per-service: this lives in ai-service's database, NOT core's. The table is
-- DENORMALIZED — it carries the message snippet + sender/sequence/conversation inline so
-- vector search needs no cross-database JOIN back to core's `messages`/`users` tables
-- (docs/microservices-migration.md §4b). Rows are populated by consuming the
-- MessageEmbeddingRequested event off the outbox topic (event-carried state transfer);
-- there is deliberately no foreign key to core.
--
-- Requires the pgvector extension (use the pgvector/pgvector image).
-- ---------------------------------------------------------------------------
create extension if not exists vector;

create table message_embeddings (
    message_id         uuid          not null,
    conversation_id    uuid          not null,
    sender_id          uuid,
    sender_name        varchar(255),
    sequence_number    bigint        not null,
    content_snippet    text          not null,
    embedding          vector(1536)  not null,
    model              varchar(100)  not null,
    dimensions         integer       not null,
    message_created_at timestamp(6) with time zone not null,
    embedded_at        timestamp(6) with time zone not null,
    primary key (message_id)
);

-- Approximate-nearest-neighbour index for cosine similarity (<=> with vector_cosine_ops).
create index idx_message_embedding_hnsw
    on message_embeddings using hnsw (embedding vector_cosine_ops);

-- Conversation-scoped search filters on this before the vector ordering.
create index idx_message_embedding_conversation
    on message_embeddings (conversation_id);

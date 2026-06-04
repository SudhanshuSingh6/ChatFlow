-- ---------------------------------------------------------------------------
-- AI: per-message embeddings for semantic search / RAG.
--
-- Requires the pgvector extension to be available in the Postgres instance
-- (the `vector` type + HNSW index). Embeddings are written asynchronously by the
-- embedding worker draining MESSAGE_EMBEDDING_REQUESTED outbox events.
--
-- The vector column has a fixed dimension (1536 — a common embedding size); the
-- `model` / `dimensions` columns record what actually produced each row so a future
-- model swap or re-embedding can be reasoned about and migrated safely.
-- ---------------------------------------------------------------------------
create extension if not exists vector;

create table message_embeddings (
    message_id  uuid          not null,
    embedding   vector(1536)  not null,
    model       varchar(100)  not null,
    dimensions  integer       not null,
    embedded_at timestamp(6) with time zone not null,
    primary key (message_id),
    constraint fk_message_embedding_message
        foreign key (message_id) references messages (id)
);

-- Approximate-nearest-neighbour index for cosine similarity (<=> with vector_cosine_ops).
create index idx_message_embedding_hnsw
    on message_embeddings using hnsw (embedding vector_cosine_ops);

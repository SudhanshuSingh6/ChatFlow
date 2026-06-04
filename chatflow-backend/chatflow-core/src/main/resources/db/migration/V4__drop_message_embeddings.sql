-- ---------------------------------------------------------------------------
-- Phase 1.5: embeddings are owned by ai-service now (its own database). core no longer
-- reads or writes embeddings — semantic search is delegated to ai over HTTP — so its local
-- table and the messages FK are dropped. core still emits MessageEmbeddingRequested events
-- that ai consumes to populate its store.
--
-- The pgvector extension is intentionally left installed (harmless; avoids churn if a future
-- core feature needs it).
-- ---------------------------------------------------------------------------
drop table if exists message_embeddings;

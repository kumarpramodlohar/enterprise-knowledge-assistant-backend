-- One vector table per provider profile because embedding dimensions differ (DEC-002).
-- Column layout matches the Spring AI PgVectorStore. Each entry's id is the document_chunks.id.
CREATE TABLE vector_store_ollama (
    id        UUID PRIMARY KEY REFERENCES document_chunks (id) ON DELETE CASCADE,
    content   TEXT,
    metadata  JSON,
    embedding vector(768)
);
CREATE INDEX idx_vector_store_ollama_embedding
    ON vector_store_ollama USING hnsw (embedding vector_cosine_ops);

CREATE TABLE vector_store_openai (
    id        UUID PRIMARY KEY REFERENCES document_chunks (id) ON DELETE CASCADE,
    content   TEXT,
    metadata  JSON,
    embedding vector(1536)
);
CREATE INDEX idx_vector_store_openai_embedding
    ON vector_store_openai USING hnsw (embedding vector_cosine_ops);

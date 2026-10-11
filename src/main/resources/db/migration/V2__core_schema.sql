CREATE TABLE collections (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id    UUID        NOT NULL REFERENCES collections (id),
    file_name        TEXT        NOT NULL,
    content_type     TEXT        NOT NULL,
    size_bytes       BIGINT      NOT NULL CHECK (size_bytes > 0),
    checksum_sha256  CHAR(64)    NOT NULL,
    version          TEXT,
    document_type    TEXT,
    tags             TEXT[]      NOT NULL DEFAULT '{}',
    status           TEXT        NOT NULL
        CHECK (status IN ('QUEUED', 'PROCESSING', 'INDEXED', 'FAILED', 'DELETED')),
    failure_reason   TEXT,
    uploaded_by      TEXT        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);

-- Duplicate detection within a collection (DEC-004); soft-deleted documents do not block re-upload.
CREATE UNIQUE INDEX uq_documents_collection_checksum
    ON documents (collection_id, checksum_sha256) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_collection_status ON documents (collection_id, status);
CREATE INDEX idx_documents_purge ON documents (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE ingestion_jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    status        TEXT        NOT NULL
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    attempts      INT         NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ingestion_jobs_status ON ingestion_jobs (status, created_at);

-- Application-owned chunk metadata. Vector entries reference chunk id, so citations
-- resolve through trusted metadata and permission checks.
CREATE TABLE document_chunks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID    NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index   INT     NOT NULL CHECK (chunk_index >= 0),
    content       TEXT    NOT NULL,
    page_number   INT,
    section       TEXT,
    UNIQUE (document_id, chunk_index)
);

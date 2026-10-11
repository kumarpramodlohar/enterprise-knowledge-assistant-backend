-- Retry backoff: a queued job is only claimable once available_at has passed.
ALTER TABLE ingestion_jobs ADD COLUMN available_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Soft-delete lifecycle (DEC-006): the purge deadline is persisted at deletion time.
ALTER TABLE documents ADD COLUMN purge_after TIMESTAMPTZ;
ALTER TABLE documents ADD COLUMN deleted_by TEXT;
CREATE INDEX idx_documents_purge_after ON documents (purge_after) WHERE deleted_at IS NOT NULL;

-- Content-free audit trail; intentionally has no FK so it survives the purge of the document.
CREATE TABLE document_audit (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id   UUID        NOT NULL,
    collection_id UUID        NOT NULL,
    action        TEXT        NOT NULL CHECK (action IN ('REINDEX', 'DELETE', 'PURGE')),
    outcome       TEXT        NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILED')),
    actor         TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_audit_document ON document_audit (document_id);

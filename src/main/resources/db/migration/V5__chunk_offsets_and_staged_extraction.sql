ALTER TABLE document_chunks ADD COLUMN start_offset INT;

-- Extracted text staged at upload so ingestion is durable without retaining the original file (DEC-005).
-- Derived text is removed with the document (cascade) and purged with it.
CREATE TABLE document_extraction_segments (
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    seq         INT  NOT NULL CHECK (seq >= 0),
    page_number INT,
    section     TEXT,
    text        TEXT NOT NULL,
    PRIMARY KEY (document_id, seq)
);

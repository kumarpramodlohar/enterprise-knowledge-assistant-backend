# Product and Architecture Decisions

Decisions that unblock the [Implementation Backlog](Implementation_Backlog_v1.md). Date: 2026-10-09. Owner: Product owner.

If a decision changes, update this file first, then the affected spec or task.

| ID | Topic | Decision | Status |
|---|---|---|---|
| DEC-001 | Authentication | Local dev mode + OIDC | Approved |
| DEC-002 | AI providers | Ollama (local), OpenAI (deployed) | Approved |
| DEC-003 | Upload limits | 10 MB, extension + content check | Approved |
| DEC-004 | Duplicate uploads | Reject with 409 | Approved |
| DEC-005 | Original files | Not stored | Approved |
| DEC-006 | Deletion | Soft delete, purge after 7 days | Approved |
| DEC-007 | RAG tuning values | Starting values below | Approved |
| DEC-008 | MVP scope | Optional features deferred | Approved |

---

## DEC-001: EKA-01 - Authentication

**Decision:** Support two modes through Spring Security.
- **Local dev mode:** simple local users for development and tests only.
- **Deployed environments:** OIDC provider (Spring Security OAuth2 resource server validating tokens).

**Notes**
- Local dev mode must be disabled by default outside the `local` profile.
- Roles and collection permissions are stored in PostgreSQL and mapped from the user's identity subject.
- OIDC provider product is not chosen yet; the backend uses standard OIDC so any provider works.

**Affects:** EKA-04, EKA-06, EKA-20

## DEC-002: EKA-01 - Chat and embedding providers

**Decision:**
- **Local dev:** Ollama for chat and embeddings.
- **Deployed:** OpenAI for chat and embeddings.
- Switching is done with Spring profiles and configuration, not code changes.

**Notes**
- Ollama and OpenAI embedding models have different vector dimensions. Each environment has its own index; switching provider means re-indexing all documents.
- Similarity scores differ per model, so the evidence threshold (DEC-007) is set per profile and tuned during evaluation.
- Remove unused starters (Anthropic, Pinecone) from `build.gradle` in EKA-02.
- Specific model names are set in configuration during EKA-02.

**Affects:** EKA-02, EKA-11, EKA-16, EKA-20

## DEC-003: EKA-01 - Upload limits and validation

**Decision:**
- Maximum file size: **10 MB** per file.
- Allowed types: PDF, DOCX, TXT, Markdown.
- Validation: file extension **and** server-side content check. Client-supplied content type is never trusted alone.

**Affects:** EKA-07, EKA-08, EKA-09

## DEC-004: EKA-01 - Duplicate uploads

**Decision:** Reject a duplicate with **409 Conflict** when the same file checksum already exists in the same collection.

**Notes**
- Soft-deleted documents (DEC-006) do not count as duplicates, so a deleted file can be uploaded again.
- The error message tells the user which document already exists, only if they can access it.

**Affects:** EKA-03, EKA-05, EKA-07, EKA-08

## DEC-005: EKA-01 - Original file retention

**Decision:** Original files are **not stored**. Only extracted text, chunks, vectors and metadata are kept. Temporary upload files are deleted after processing, on success or failure.

**Notes**
- Re-indexing therefore needs the stored chunk text (or a new upload) because the original file is gone. For the MVP, re-index reuses stored extracted text.
- Changing the extraction logic requires re-uploading the file.

**Affects:** EKA-07, EKA-09, EKA-12, EKA-13

## DEC-006: EKA-01 - Deletion

**Decision:** **Soft delete.**
1. Deletion marks the document as deleted. It is excluded from retrieval, listings and citations immediately.
2. Records are kept for **7 days**.
3. After 7 days, a purge job permanently removes chunks, vectors and derived records.
4. Each deletion and purge is written to the audit log.

**Notes**
- Deleted content still exists in the database for up to 7 days but is not reachable through the API.
- Purge failures are retried and logged.

**Affects:** EKA-03, EKA-13, EKA-14

## DEC-007: EKA-01 - Initial RAG tuning values

All values are configurable and will be tuned during evaluation (EKA-19).

| Setting | Initial value |
|---|---|
| Chunk size | 800 tokens |
| Chunk overlap | 100 tokens |
| Top-K | 5 |
| Context budget | 4000 tokens |
| Minimum similarity (evidence threshold) | 0.70 |
| Ingestion worker concurrency | 2 |

**Affects:** EKA-02, EKA-10, EKA-12, EKA-15, EKA-16

## DEC-008: EKA-01 - MVP scope of optional features

**Decision:** The following are **deferred** (not in MVP):
- Hybrid (keyword + vector) retrieval
- Reranking
- Answer feedback
- Conversation history
- Kafka or other message broker for ingestion (use the bounded executor with persisted jobs)

**Notes**
- Without conversation history, questions and answers are not stored.
- These can be added after the MVP baseline evaluation.

**Affects:** EKA-17, EKA-19

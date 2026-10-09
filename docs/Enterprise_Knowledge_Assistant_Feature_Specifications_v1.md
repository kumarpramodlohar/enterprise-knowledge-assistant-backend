# Enterprise Knowledge Assistant

## Feature Specifications

**Document ID:** EKA-FS-001  
**Version / status:** 1.0 / Draft for review  
**Prepared date:** 2026-10-09  
**Target release:** MVP v1  
**Product requirements:** [`Enterprise_Knowledge_Assistant_PRD_v1.md`](Enterprise_Knowledge_Assistant_PRD_v1.md)  
**Architecture:** [`Enterprise_Knowledge_Assistant_Architecture_v1.md`](Enterprise_Knowledge_Assistant_Architecture_v1.md)  
**Canonical requirements:** [`Enterprise_Knowledge_Assistant_Requirements_v1.md`](Enterprise_Knowledge_Assistant_Requirements_v1.md)

> This document turns the product and canonical requirements into feature-level behavior and acceptance criteria. It describes target behavior, not proof that the features have already been implemented. Where a detail remains undecided, it is called out rather than silently assumed.

## 1. Purpose and Scope

This specification defines four MVP features:

1. Document upload
2. Document ingestion and indexing
3. Retrieval
4. Chat answers and citations

Each feature distinguishes the React UI from the Spring Boot/Spring AI backend. PostgreSQL with pgvector is the target persistence and vector-search layer. All authorization decisions are enforced on the backend; UI restrictions are for usability only.

### Shared behavior and terms

- **Authorized scope:** Collections and documents the authenticated principal is permitted to use, derived by the backend from identity and permission data.
- **Indexed document:** A document whose active content has completed required extraction, chunking, embedding, and indexing.
- **Source chunk:** A stored passage linked to a document and, when available, a page, section, or source offset.
- **Safe error:** A user-readable message/code that does not disclose credentials, provider internals, stack traces, or unauthorized document content.
- **MVP response mode:** Synchronous query/answer response. Document upload/ingestion may be asynchronous.
- **Approved decisions:** Limits, providers, retention and tuning values come from [`implementation/product-decisions.md`](implementation/product-decisions.md) (DEC-001 to DEC-008): 10 MB upload limit, Ollama locally and OpenAI when deployed, no original-file storage, soft delete with a 7-day purge, chunk 800/100 tokens, top-K 5, context budget 4000 tokens, evidence threshold 0.70 (tuned per provider), worker concurrency 2.
- **Still open:** OIDC provider product, exact model names, and request quotas.

## 2. Feature: Document Upload

### User outcome

An authorized Knowledge Manager can submit a supported document to an authorized collection and see whether it was accepted for processing. Invalid files are rejected clearly and never become searchable.

### React UI responsibilities

- Present an upload control for PDF, DOCX, TXT, and Markdown files and collection selection from collections available to the user.
- Show the selected filename and upload progress or pending state; prevent accidental duplicate submissions while a request is in flight.
- On acceptance, show the created document and its initial ingestion status.
- On validation or authorization failure, show a safe, actionable message and allow correction/retry.
- Do not rely on client-side MIME checks, hidden controls, or collection IDs as authorization.

### Spring Boot backend responsibilities

- Expose the proposed `POST /api/v1/documents` multipart endpoint.
- Authenticate the caller and authorize document creation in the requested collection before accepting the file.
- Enforce configured request and file size limits; validate allowed file type using server-side checks, reject empty/corrupt/unsupported files, and avoid trusting the filename or client-declared content type by itself.
- Register an application document and ingestion job only for a successfully accepted upload. Return `202 Accepted` with document ID, status, and a status-resource link.
- Calculate a checksum and return `409 Conflict` when a non-deleted document with the same checksum already exists in the collection (DEC-004).
- Do not store the original file; remove temporary files after processing (DEC-005).
- Return consistent safe errors; do not expose parser/provider exceptions or leave partial searchable content on rejection.

### Acceptance criteria

| ID | Given / when | Then | Verification |
|---|---|---|---|
| UP-01 | An authenticated Knowledge Manager with create permission submits a non-empty supported PDF, DOCX, TXT, or Markdown file to an authorized collection. | The API returns `202 Accepted` with a document ID and status resource; the UI displays the accepted document and initial status. | API integration + UI component/E2E |
| UP-02 | An unauthenticated caller submits a document. | The API returns `401`; no document, job, chunk, or vector is created. | API security integration |
| UP-03 | An authenticated user lacks permission to create in the requested collection. | The API denies the operation (`403`, or the approved non-disclosing equivalent); no document/job/index record is created and no collection content is disclosed. | API authorization integration |
| UP-04 | A caller submits an unsupported, empty, corrupt, or over-limit file. | The API rejects the request with a safe, user-readable error; no document becomes searchable and no active chunks/vectors are created. | API integration |
| UP-05 | A client supplies a misleading extension or content type for a disallowed file. | Server-side validation rejects it; client-provided metadata alone cannot cause the file to be accepted. | API security/integration |
| UP-06 | An upload is in progress or has failed validation. | The UI communicates the pending/error state, prevents unintended parallel duplicate submission, and provides an accessible recovery path. | UI component/E2E |
| UP-07 | A file larger than 10 MB is submitted; a file of exactly 10 MB is submitted. | The larger file is rejected with a clear message; the 10 MB file is accepted. | API integration |
| UP-08 | A file with the same checksum as a non-deleted document in the same collection is uploaded. | The API returns `409 Conflict` and creates no document or job; the UI shows a duplicate message. Uploading the same file after the original is soft-deleted succeeds. | API integration + UI component |

### Out of scope

Bulk import, crawling, automatic external-system synchronization, and user-controlled retention settings are not required for this feature's MVP.

## 3. Feature: Ingestion and Indexing

### User outcome

An accepted document is transformed into searchable source-linked chunks. The Knowledge Manager can distinguish queued, processing, indexed, and failed states and safely retry or re-index failed work.

### React UI responsibilities

- Show each permitted document's status and last-updated information.
- Refresh or poll status through the API without treating a timeout as proof of success.
- Display a safe error summary for failed ingestion and expose retry/re-index actions only when authorized.
- Update to Indexed only when the backend reports that the active document version is searchable.

### Spring Boot / Spring AI backend responsibilities

- Persist a durable document and job record before asynchronous work starts; use a bounded worker/executor for the MVP, with startup reconciliation for queued/stale work.
- Process stages in order: extract text and source metadata; normalize and chunk using configured size/overlap; generate embeddings through the configured Spring AI embedding model; persist linked chunks/vectors and model metadata; mark the active index version searchable only after all required writes complete.
- Support PDF, DOCX, TXT, and Markdown extraction. Preserve page/section/source offsets when available.
- Use stable chunk identifiers and idempotent job/document-version keys so retry does not produce duplicate active chunks.
- Bound embedding batch size, provider timeout, concurrency, and transient retries. Record a safe terminal failure when recovery is exhausted.
- On re-index, prevent old and replacement chunks from being simultaneously active after successful activation. Keep embedding model/version and dimensions available to detect incompatible index configurations.
- Expose document status and safe error summary only after authorization checks.
- Handle deletion as a soft delete (DEC-006): the document is excluded from retrieval, listings and citations immediately; a purge job removes chunks, vectors and derived records 7 days later. Record each deletion and purge in the audit log and retry failed purges.
- Re-index from stored extracted text, because original files are not kept (DEC-005).

### State model

```text
QUEUED -> PROCESSING -> INDEXED
                    \-> FAILED
FAILED -> QUEUED (authorized retry)
INDEXED -> QUEUED (authorized re-index)
```

`INDEXED` means the active index version is complete and eligible for authorized retrieval. A document being uploaded, processing, failed, or deleted must not be returned as an active search result. Exact intermediate states may be added internally but must map to the four UI-visible states.

### Acceptance criteria

| ID | Given / when | Then | Verification |
|---|---|---|---|
| IN-01 | An accepted supported file is queued. | A persisted job exists and the document is reported as `QUEUED` or `PROCESSING`; it is not yet returned as searchable. | Integration |
| IN-02 | A valid supported file has extractable text. | Extraction produces non-empty normalized content; available page/section/source metadata is retained and traceable to the document. | Parser integration |
| IN-03 | A supported file is empty, corrupt, or produces no searchable text. | The job/document becomes `FAILED` with a safe error code/summary; no active chunks/vectors are searchable. | Parser integration |
| IN-04 | Text extraction succeeds. | Chunking applies the configured size and overlap; every chunk has a stable ID, parent document ID, deterministic ordering, and available source-location metadata. | Chunker unit + integration |
| IN-05 | The embedding provider succeeds for all chunks. | Each active chunk has a vector of the configured dimension and records the embedding model/version; the document becomes `INDEXED` only after all required vectors and metadata are committed. | Spring AI adapter + DB integration |
| IN-06 | The embedding provider times out or returns a transient failure. | The backend retries only within the configured limit; after retries are exhausted, the job is `FAILED` with a safe diagnostic and is not searchable. | Provider-failure integration |
| IN-07 | A retry is requested for a failed job. | The retry is authorized and idempotent; repeated execution does not create duplicate active chunks for the same document/index version. | Integration |
| IN-08 | A document is re-indexed with a changed chunking or embedding configuration. | A complete new active index is produced; after activation, only the intended active version is eligible for retrieval and no duplicate active chunks remain. | Integration |
| IN-09 | A user without document access requests status or retries by document ID. | The backend denies access without returning document metadata, failure details, or content. | Security integration |
| IN-10 | The UI receives each supported backend status. | It renders distinct queued, processing, indexed, and failed states; failure includes the safe summary, and retry is shown only when permitted. | UI E2E |
| IN-11 | A document is deleted. | It is excluded from retrieval, listings and citations immediately; after the 7-day retention period the purge job removes its chunks and vectors; deletion and purge are auditable. | Integration + security |

### Retry and failure rules

- Retry only transient provider/storage failures automatically; never loop indefinitely.
- Invalid content and permanent parser failures require correction/re-upload rather than repeated automatic retry.
- Do not suppress stage failures or mark a document Indexed after partial embedding/index writes.
- Persist enough job state to reconcile interrupted work after application restart.

## 4. Feature: Retrieval

### User outcome

An End User's question is searched against a bounded set of relevant documents they are authorized to access, with requested collection and metadata filters applied before content can reach answer generation.

### React UI responsibilities

- Offer only usable collection/version/document-type/tag filters based on available product data.
- Submit the user's question and chosen filters without sending a user identity as an authorization claim.
- Display safe query errors and empty/insufficient evidence outcomes distinctly from infrastructure failures.
- Do not locally filter a broad result set as a substitute for backend authorization.

### Spring Boot / Spring AI backend responsibilities

- Validate non-empty question text and filter schema; apply configured query length and top-K bounds.
- Resolve principal and permitted collection/document scope from server-side authorization.
- Combine mandatory authorization constraints with caller-selected filters in the pgvector search query. User filters may narrow the allowed scope but never expand it.
- Retrieve at most configured top-K authorized candidates, subject to availability; exclude non-indexed, failed, deleting, or deleted documents.
- Return passage IDs and trusted source metadata to the answer service; do not expose candidate content outside the authorized scope.
- Use semantic similarity as baseline. Hybrid keyword/vector fusion and reranking are optional, configuration-controlled stages and require evaluation before being relied upon.
- Treat vector similarity scores as ranking signals, not calibrated correctness probabilities. Apply an explicit configurable evidence policy before generation.

### Acceptance criteria

| ID | Given / when | Then | Verification |
|---|---|---|---|
| RT-01 | An authenticated user submits a valid question and has authorized indexed documents. | The backend performs semantic retrieval and returns no more than configured top-K eligible candidates to the answer pipeline. | Retrieval integration |
| RT-02 | A question is empty or whitespace-only. | The API rejects it as invalid input and does not call the embedding or chat model. | API unit/integration |
| RT-03 | A caller requests a collection, version, type, tag, or other supported filter. | Only documents satisfying both the server-derived authorization scope and supplied filters are eligible for retrieval. | Repository/vector-store integration |
| RT-04 | A client tampers with filters or supplies another user's/tenant's collection or document identifier. | The backend cannot retrieve or return unauthorized chunks; the attempt is denied or produces no disclosed result under the approved disclosure policy. | Adversarial security integration |
| RT-05 | Matching candidates include queued, processing, failed, deleting, or deleted documents. | Such candidates are excluded from retrieval. | Retrieval integration |
| RT-06 | An authorized search has fewer eligible results than top-K. | The backend returns only available authorized results; it does not pad with unauthorized or out-of-scope chunks. | Retrieval integration |
| RT-07 | No eligible result exists or evidence falls below the configured policy. | Retrieval marks the evidence insufficient and the answer pipeline does not present a factual answer grounded in nonexistent/weak evidence. | Retrieval + answer integration |
| RT-08 | A retrieval query is executed. | Access and metadata filters are applied in the database/vector-store query before passage text is made available to prompt construction. | Integration/security test |
| RT-09 | Vector-only, hybrid, or reranked modes are configured for evaluation. | The selected mode is recorded with its configuration; optional hybrid/reranking changes can be compared on the same labelled dataset. | Evaluation test |

### Retrieval response contract (logical)

The internal retrieval result should include, per passage: stable chunk ID, document ID, trusted title/filename, authorized collection scope, available page/section/source offsets, passage text, rank/score, and index/model version metadata. Do not treat model-generated citation labels or client-provided scope as trusted retrieval metadata.

## 5. Feature: Chat Answers and Citations

### User outcome

An End User receives a readable answer grounded in retrieved authorized passages, can inspect the supporting sources, and is told clearly when the available evidence is insufficient.

### React UI responsibilities

- Provide an accessible question form with submit state and duplicate-submit prevention while pending.
- Render answer, insufficient-evidence, validation, authorization, rate/availability, and provider errors as distinct states.
- Display citations as source links/details with title and available page/section/excerpt; do not fabricate a fallback citation when metadata is unavailable.
- Render answer and excerpts as inert text or safely sanitized formatting; never execute HTML or script originating from a model or document.
- Keep a citation usable only if the backend confirms the caller can access its source. On access denial or stale/deleted source, show a safe unavailable state.
- Conversation history and answer feedback are deferred (DEC-008); do not imply history survives reload.

### Spring Boot / Spring AI backend responsibilities

- Orchestrate query validation, authorization-scoped retrieval, evidence decision, context construction, chat model invocation, and response creation.
- Send only authorized retrieved passages to the model, with a bounded context and instructions to answer from evidence and state uncertainty.
- Treat document text as untrusted data; prompt-injection instructions in retrieved content must not override system policy or access controls.
- If evidence is insufficient, return a structured insufficient-evidence result without asking the model to invent an answer.
- For sufficient evidence, return answer text and citations derived from retrieved chunk/document metadata. The model may reference citation markers, but the backend validates/maps them; unknown or unauthorized references are excluded.
- Bound model timeout/retries. Return a safe provider error on exhaustion; never return raw prompts, provider payloads, credentials, or stack traces.
- Return a correlation ID and timing/usage fields where available and safe. Do not persist questions or answers; conversation history is deferred (DEC-008).

### Proposed logical response

```json
{
  "status": "ANSWERED",
  "answer": "The answer based on the retrieved sources.",
  "citations": [
    {
      "documentId": "document-id",
      "chunkId": "chunk-id",
      "title": "Source document",
      "page": 12,
      "section": "Optional section",
      "excerpt": "Supporting source passage"
    }
  ],
  "correlationId": "request-correlation-id"
}
```

For insufficient evidence, `status` is `INSUFFICIENT_EVIDENCE`, `answer` communicates that the source material does not establish the answer, and `citations` contains only valid sources if the UX chooses to show them. Exact JSON names and error envelope are API design decisions; the semantic distinction is required.

### Acceptance criteria

| ID | Given / when | Then | Verification |
|---|---|---|---|
| CH-01 | An authenticated user submits a valid question and retrieval finds sufficient authorized evidence. | The backend returns a non-empty answer grounded in the retrieved context and at least one citation resolving to an authorized indexed chunk. | Chat integration |
| CH-02 | The question is unanswerable, retrieval is empty, or evidence is below the configured threshold. | The response status is `INSUFFICIENT_EVIDENCE` (or the equivalent documented schema); it communicates uncertainty and contains no fabricated specific factual answer. | Chat integration + evaluation |
| CH-03 | Retrieved chunks contain prompt-injection text. | The text is treated as untrusted evidence, cannot change access scope or system policy, and cannot cause unauthorized context or secrets to be returned. | Adversarial prompt/security test |
| CH-04 | The model returns a citation marker that is not in the retrieved set or maps to an inaccessible source. | The backend does not expose it as a valid citation; the answer response contains only citations validated against retrieved authorized metadata. | Chat integration/security |
| CH-05 | A valid citation is returned. | Its document and chunk IDs resolve to a real indexed source; title and available page/section/excerpt match stored metadata and are accessible to the caller. | Citation integration |
| CH-06 | A cited document is deleted or the user no longer has access before citation details are opened. | The citation-detail request rechecks authorization and returns a safe unavailable/denied result without leaking source content. | API security integration + UI E2E |
| CH-07 | The model provider exceeds the configured timeout or exhausts bounded retries. | The request terminates within the configured upper bound plus documented application overhead and returns a safe error; it does not return a success-shaped empty answer. | Provider-failure integration |
| CH-08 | The response includes answer text or source excerpts containing markup/script-like text. | The React UI renders it inertly; no script or active markup executes. | UI security test |
| CH-09 | A chat response is produced. | It includes a correlation ID; provider usage/latency is included only when available, and logs do not include credentials or full document content by default. | API/observability integration |
| CH-10 | A user submits a question while a prior request is pending. | The UI shows the pending state, disables duplicate submission, and sends no second request until the first completes or is cancelled. | UI component/E2E |

## 6. Cross-Feature Test Scenarios

| ID | Scenario | Expected result | Primary test layer |
|---|---|---|---|
| E2E-01 | Upload a valid PDF, wait for ingestion to complete, then ask a question answered by its content. | Document reaches Indexed; answer includes a citation to the authorized source and the citation opens the correct metadata/excerpt. | End-to-end |
| E2E-02 | Upload an unsupported file and then query for its content. | Upload is rejected, no searchable chunks are created, and the answer does not claim evidence from that file. | End-to-end |
| E2E-03 | Force embedding failure, inspect status, then retry after restoring the provider. | First attempt reports safe failure; retry is successful and creates only one active chunk set. | End-to-end / fault injection |
| E2E-04 | Index documents for two versions; ask with one version filter. | Only authorized passages for the selected version reach the model and appear in citations. | End-to-end/security |
| E2E-05 | User A asks for content in a collection accessible only to User B. | No matching text reaches the model or response; access denial/no-result behavior follows the documented disclosure policy. | Adversarial end-to-end |
| E2E-06 | Delete a cited document, then repeat the query and attempt to reopen the old citation. | Deleted content is no longer retrieved; stale citation access does not disclose its content. | End-to-end |
| E2E-07 | Ask a question with no supporting content. | The response is explicitly insufficient evidence and does not assert a specific unsupported fact. | End-to-end/evaluation |

## 7. Test Data and Testability Requirements

- Maintain small, versioned fixtures for each supported format: valid extractable file, empty file, corrupt file, oversized file test case, and metadata/version examples.
- Use deterministic/fake embedding and chat adapters for most unit and integration tests. Keep a separately controlled provider smoke test for real configuration.
- Provide test users with different roles and collection grants to verify that the backend, retrieval store, and citation lookup enforce identical authorization scope.
- Instrument test adapters to assert whether the embedding/chat provider was called. Invalid upload/question and insufficient-evidence paths must assert no inappropriate model invocation.
- Assert database state as well as HTTP response: rejected uploads create no active index entries; successful ingestion has linked vectors; retries/re-indexing do not create duplicate active chunks.
- Keep evaluation queries, relevant source chunk labels, dataset version, model/configuration version, and metric output together for reproducibility.

## 8. Requirement Traceability

| Feature specification | Canonical functional requirements | Canonical acceptance tests |
|---|---|---|
| Document upload (UP-01–UP-08) | FR-001–FR-003, FR-017, FR-024 | AT-01, AT-02, AT-06 |
| Ingestion and indexing | FR-004–FR-008, FR-018, FR-021 | AT-01, AT-02, AT-07, AT-08 |
| Retrieval | FR-010–FR-014, FR-016 | AT-05, AT-06, AT-09 |
| Chat answers and citations | FR-009, FR-014–FR-016, FR-023–FR-024 | AT-03, AT-04, AT-06, AT-10 |

The canonical requirements document remains authoritative for requirement priority, system-wide non-functional requirements, and final API/error schemas. Decisions that resolve its open items are in [`implementation/product-decisions.md`](implementation/product-decisions.md).

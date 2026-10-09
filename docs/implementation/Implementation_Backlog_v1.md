# Enterprise Knowledge Assistant

## Ordered Implementation Backlog

**Document ID:** EKA-IB-001  
**Version / status:** 1.0 / Draft for planning  
**Prepared date:** 2026-10-09  
**Target release:** MVP v1  
**Feature specifications:** [`../Enterprise_Knowledge_Assistant_Feature_Specifications_v1.md`](../Enterprise_Knowledge_Assistant_Feature_Specifications_v1.md)  
**Architecture:** [`../Enterprise_Knowledge_Assistant_Architecture_v1.md`](../Enterprise_Knowledge_Assistant_Architecture_v1.md)  
**Product requirements:** [`../Enterprise_Knowledge_Assistant_PRD_v1.md`](../Enterprise_Knowledge_Assistant_PRD_v1.md)  
**Canonical requirements:** [`../Enterprise_Knowledge_Assistant_Requirements_v1.md`](../Enterprise_Knowledge_Assistant_Requirements_v1.md)

> This is an implementation backlog for the target architecture. It is not a statement that these tasks are complete. Acceptance criteria in the Feature Specifications remain the behavioral source of truth.

## 1. How to Use This Backlog

- Work top-to-bottom where dependencies require it. Tasks at the same stage may be parallelized when their dependencies are done.
- A task is complete only when its implementation, tests, and acceptance evidence are complete. “Test requirements” below describe required coverage, not optional suggestions.
- `Feature spec` references the acceptance criteria IDs in the Feature Specifications document. Foundation tasks support those features and cite canonical requirements where appropriate.
- Backend means Spring Boot/Spring AI/PostgreSQL work; UI means React + TypeScript work; QA/Platform means cross-cutting test or runtime work.
- The feature specification defines supported behavior. Product and architecture decisions (identity, providers, upload limits, duplicates, retention, deletion, tuning values, MVP scope) are recorded in [`product-decisions.md`](product-decisions.md) as DEC-001 to DEC-008.
- Hybrid retrieval, reranking, feedback, conversation history, and broker-based ingestion are deferred from the MVP (DEC-008).

## 2. Dependency-Ordered Summary

| Order | Task | Area | Depends on | Feature spec trace |
|---:|---|---|---|---|
| 1 | [EKA-01 Resolve MVP implementation decisions (done)](#eka-01-resolve-mvp-implementation-decisions) | Product / Architecture | — | Shared; UP-04, IN-05, RT-07, CH-07 |
| 2 | [EKA-02 Align application dependencies and configuration](#eka-02-align-application-dependencies-and-configuration) | Backend / Platform | EKA-01 | Shared |
| 3 | [EKA-03 Establish PostgreSQL, pgvector, and schema migrations](#eka-03-establish-postgresql-pgvector-and-schema-migrations) | Backend / Platform | EKA-01, EKA-02 | UP-01, IN-01–IN-09, RT-01–RT-08 |
| 4 | [EKA-04 Implement authentication, authorization, errors, and correlation](#eka-04-implement-authentication-authorization-errors-and-correlation) | Backend | EKA-01, EKA-02, EKA-03 | UP-02–UP-03, IN-09, RT-04, CH-06, CH-09 |
| 5 | [EKA-05 Define and verify API contracts](#eka-05-define-and-verify-api-contracts) | Backend / UI | EKA-01, EKA-04 | UP-01, IN-10, RT-01–RT-03, CH-01–CH-10 |
| 6 | [EKA-06 Build the React application shell and API client](#eka-06-build-the-react-application-shell-and-api-client) | UI | EKA-01, EKA-05 | UP-01, UP-06, IN-10, RT, CH |
| 7 | [EKA-07 Implement document upload API and validation](#eka-07-implement-document-upload-api-and-validation) | Backend | EKA-03, EKA-04, EKA-05 | UP-01–UP-05 |
| 8 | [EKA-08 Implement upload UI and feedback states](#eka-08-implement-upload-ui-and-feedback-states) | UI | EKA-06, EKA-07 | UP-01, UP-06 |
| 9 | [EKA-09 Implement extraction adapters](#eka-09-implement-extraction-adapters) | Backend | EKA-01, EKA-02 | IN-02–IN-03 |
| 10 | [EKA-10 Implement stable chunking and source metadata](#eka-10-implement-stable-chunking-and-source-metadata) | Backend | EKA-03, EKA-09 | IN-04 |
| 11 | [EKA-11 Implement Spring AI embeddings and pgvector storage](#eka-11-implement-spring-ai-embeddings-and-pgvector-storage) | Backend | EKA-01–EKA-03, EKA-10 | IN-05–IN-08 |
| 12 | [EKA-12 Implement durable ingestion processing and recovery](#eka-12-implement-durable-ingestion-processing-and-recovery) | Backend | EKA-03, EKA-07, EKA-09–EKA-11 | IN-01, IN-03, IN-05–IN-08 |
| 13 | [EKA-13 Implement document status, retry, re-index, and deletion APIs](#eka-13-implement-document-status-retry-re-index-and-deletion-apis) | Backend | EKA-03–EKA-05, EKA-12 | IN-07–IN-09, IN-11 |
| 14 | [EKA-14 Build the document library and ingestion status UI](#eka-14-build-the-document-library-and-ingestion-status-ui) | UI | EKA-06, EKA-13 | IN-09–IN-11 |
| 15 | [EKA-15 Implement authorization-scoped semantic retrieval](#eka-15-implement-authorization-scoped-semantic-retrieval) | Backend / Spring AI | EKA-03–EKA-05, EKA-11–EKA-12 | RT-01–RT-08 |
| 16 | [EKA-16 Implement evidence policy and grounded answer orchestration](#eka-16-implement-evidence-policy-and-grounded-answer-orchestration) | Backend / Spring AI | EKA-01, EKA-05, EKA-15 | RT-07, CH-01–CH-04, CH-07 |
| 17 | [EKA-17 Implement chat API and validated citations](#eka-17-implement-chat-api-and-validated-citations) | Backend | EKA-04–EKA-05, EKA-15–EKA-16 | CH-01–CH-07, CH-09 |
| 18 | [EKA-18 Build the assistant, answer, and citation UI](#eka-18-build-the-assistant-answer-and-citation-ui) | UI | EKA-06, EKA-05, EKA-17 | RT UI, CH-01–CH-10 |
| 19 | [EKA-19 Add end-to-end, security, and evaluation coverage](#eka-19-add-end-to-end-security-and-evaluation-coverage) | QA / Backend / UI | EKA-07–EKA-18 | UP-01–UP-06, IN-01–IN-11, RT-01–RT-09, CH-01–CH-10, E2E-01–E2E-07 |
| 20 | [EKA-20 Package local deployment and release readiness](#eka-20-package-local-deployment-and-release-readiness) | Platform / All | EKA-02–EKA-04, EKA-07–EKA-19 | Shared |

## 3. Task Specifications

### EKA-01 Resolve MVP implementation decisions

**Area:** Product / Architecture  
**Depends on:** None  
**Feature spec trace:** Shared decisions; UP-04, IN-05, RT-07, CH-07

**Status:** Decisions approved on 2026-10-09. See [`product-decisions.md`](product-decisions.md).

| Decision | Outcome |
|---|---|
| DEC-001 Authentication | Local dev mode + OIDC for deployed environments |
| DEC-002 AI providers | Ollama (local dev), OpenAI (deployed) |
| DEC-003 Upload limits | 10 MB; extension + server-side content check |
| DEC-004 Duplicates | Reject same checksum in same collection with `409` |
| DEC-005 Original files | Not stored |
| DEC-006 Deletion | Soft delete; purge after 7 days |
| DEC-007 Tuning values | Chunk 800/100, top-K 5, context 4000, threshold 0.70, concurrency 2 |
| DEC-008 MVP scope | Hybrid, reranking, feedback, history, Kafka deferred |

**Remaining work**

- Pick the OIDC provider product and the exact Ollama/OpenAI model names (set in configuration during EKA-02; no code change needed).
- Set the evidence threshold per provider profile after the first evaluation run (EKA-19), because similarity scores differ between models.

**Acceptance / test requirements**

- Decisions are recorded without credentials or secret values.
- Configuration validation tests fail startup clearly for missing mandatory production settings and succeed with the documented local profile.
- Tests prove configuration values can be changed without source edits.

### EKA-02 Align application dependencies and configuration

**Area:** Backend / Platform  
**Depends on:** EKA-01  
**Feature spec trace:** Shared

**Work**

- Align Spring Boot, Spring AI BOM/starters, Java 21, database driver, migration, security, and test dependencies to a compatible, reproducible dependency set.
- Keep the Ollama and OpenAI starters and pgvector (DEC-002); remove the Anthropic and Pinecone starters.
- Add typed externalized configuration for database, provider/model, chunking, retrieval bounds, upload limits, and worker/retry settings, using the DEC-003 and DEC-007 values as defaults.
- Add a `local` profile (Ollama, local dev auth) and a deployed profile (OpenAI, OIDC) with placeholder-only examples and no committed secrets.
- Use a separate embedding dimension and vector table per provider profile.

**Acceptance / test requirements**

- Gradle dependency resolution and application context startup pass with the selected configuration.
- A context/configuration test verifies required components and rejects invalid bounds (for example, non-positive top-K or chunk size).
- Secret scanning/review confirms no real credential is in source or example configuration.
- Application starts in the documented local profile using placeholder or local-service configuration.
- Switching between the Ollama and OpenAI profiles needs configuration changes only.

### EKA-03 Establish PostgreSQL, pgvector, and schema migrations

**Area:** Backend / Platform  
**Depends on:** EKA-01, EKA-02  
**Feature spec trace:** UP-01; IN-01–IN-09; RT-01–RT-08

**Work**

- Add versioned migrations for collection/scope and permission references, documents, ingestion jobs, chunks/index versions, audit events, and required indexes.
- Enable pgvector and configure the vector-store schema, embedding dimensions, distance metric, and stable link fields for document/chunk/index version.
- Define uniqueness/idempotency constraints for job and active chunk identities, and a unique (collection, checksum) constraint for non-deleted documents (DEC-004).
- Store extracted document text (not the original file) so re-indexing is possible (DEC-005). Add soft-delete fields (`deleted_at`, `purge_after`) to documents (DEC-006).
- Add repository interfaces/adapters for document, job, chunk metadata, and permission/scope reads.

**Acceptance / test requirements**

- A clean PostgreSQL/pgvector instance migrates to the latest schema; migration failure is visible and does not report readiness.
- Repository integration tests create, query, update, and delete document/job/chunk metadata.
- Database constraints reject duplicate active chunk identities and invalid required references.
- A soft-deleted document does not block re-upload of the same checksum.
- Vector metadata can be joined/filtered to application-owned document and collection scope.

### EKA-04 Implement authentication, authorization, errors, and correlation

**Area:** Backend  
**Depends on:** EKA-01, EKA-02, EKA-03  
**Feature spec trace:** UP-02–UP-03, IN-09, RT-04, CH-06, CH-09

**Work**

- Configure Spring Security with a `local` dev auth mode and OIDC token validation for deployed environments (DEC-001); local dev mode must be off outside the `local` profile.
- Implement role and collection/document permission checks for upload, listing/status, retry/re-index, delete, retrieval, and citation/source access.
- Establish a consistent safe error response policy, including unauthenticated, forbidden/not-found disclosure behavior, validation, provider failure, and internal failure.
- Add request/job correlation IDs and redacted structured logging.

**Acceptance / test requirements**

- Security integration tests verify protected endpoints return `401` without identity and deny users lacking required roles/scope.
- A test verifies local dev auth is unavailable under the deployed profile.
- Tests attempt direct-ID access and tampered collection/tenant filters for users with different grants; no unauthorized content or metadata is returned.
- Tests assert forbidden/error responses do not contain stack traces, provider payloads, credentials, or document text.
- Correlation IDs are present in API responses/log context and remain associated with asynchronous ingestion jobs.

### EKA-05 Define and verify API contracts

**Area:** Backend / UI  
**Depends on:** EKA-01, EKA-04  
**Feature spec trace:** UP-01; IN-10; RT-01–RT-03; CH-01–CH-10

**Work**

- Define request/response DTOs and documented routes for upload, document list/status, retry/re-index, deletion, chat query, and citation detail access as needed.
- Define pagination, metadata filter allowlist, multipart behavior, status codes, error envelope, and correlation ID behavior.
- Keep wire contracts independent of Spring AI internal types and provider response objects.
- Share or generate a typed frontend API contract from the agreed schemas without coupling UI authorization to client data.

**Acceptance / test requirements**

- API contract tests verify JSON/multipart schemas, required fields, status codes, and validation errors.
- Tests verify unknown/unsupported filters are rejected or ignored according to the documented contract, never interpreted as broader access.
- Frontend types match the API schemas; a contract test or compile-time fixture detects incompatible response changes.

### EKA-06 Build the React application shell and API client

**Area:** UI  
**Depends on:** EKA-01, EKA-05  
**Feature spec trace:** UP-01, UP-06, IN-10, RT UI, CH UI

**Work**

- Create the React + TypeScript application structure, routing/layout, environment-based API base URL, and centralized typed API client.
- Integrate the approved authentication flow without embedding secrets; handle session expiry and unauthorized responses.
- Establish accessible loading, empty, validation, authorization, and service-error presentation patterns.
- Configure UI tests and production build scripts using the selected frontend toolchain.

**Acceptance / test requirements**

- UI build and type-check pass; API client unit tests cover success, validation, unauthorized, and server-failure responses.
- Accessibility tests verify keyboard access and labels for navigation and shared form/status patterns.
- No protected content is treated as accessible solely because the client has a route or cached UI state.

### EKA-07 Implement document upload API and validation

**Area:** Backend  
**Depends on:** EKA-03, EKA-04, EKA-05  
**Feature spec trace:** UP-01–UP-05

**Work**

- Implement `POST /api/v1/documents` with collection authorization, multipart parsing, the 10 MB size limit, server-side extension and content validation (DEC-003), and safe temporary-file handling.
- Register document metadata and a queued ingestion job only after acceptance; return `202` with document ID and status URL.
- Calculate the checksum and return `409 Conflict` if a non-deleted document with the same checksum exists in the collection (DEC-004).
- Do not store the original file; delete temporary files after processing (DEC-005).
- Ensure rejected input cannot create active chunk/vector records or leak parser errors.

**Acceptance / test requirements**

- API integration tests cover each supported format, unsupported type, empty/corrupt/over-limit content, misleading extension/content type, missing identity, and insufficient collection permission.
- For every rejected upload, assert no document/job/index entry is left in a searchable state.
- Success test asserts `202`, document/job persistence, initial status, and status URL.
- Duplicate test asserts `409` and no new document/job; re-upload after soft delete succeeds.
- Test asserts a file just over 10 MB is rejected and a file at the limit is accepted.
- Upload tests cover configured request limits and cleanup of temporary files after success and failure.

### EKA-08 Implement upload UI and feedback states

**Area:** UI  
**Depends on:** EKA-06, EKA-07  
**Feature spec trace:** UP-01, UP-06

**Work**

- Add collection-aware file selection and supported-format guidance (PDF, DOCX, TXT, Markdown; 10 MB maximum).
- Implement upload progress/pending, accepted, validation-error, duplicate (`409`), forbidden, and recoverable service-error states.
- Prevent duplicate submission while pending and navigate/update to the returned document/status resource.

**Acceptance / test requirements**

- Component tests cover selection, pending state, success, validation error, duplicate document, authorization error, and server failure.
- E2E test uploads one accepted fixture and verifies the initial status appears.
- Keyboard-only test can select a collection/file and submit; error/status changes are announced accessibly.

### EKA-09 Implement extraction adapters

**Area:** Backend  
**Depends on:** EKA-01, EKA-02  
**Feature spec trace:** IN-02–IN-03

**Work**

- Implement isolated extractors for PDF, DOCX, TXT, and Markdown behind a common interface.
- Return normalized text plus page/section/offset metadata where available.
- Detect corrupt, empty, unsupported, or non-extractable files and map failures to safe domain error codes.
- Apply parser resource limits and clean up temporary resources.

**Acceptance / test requirements**

- Unit/integration fixture tests cover one valid document for each supported format and verify non-empty text.
- Tests verify page/section/source metadata is retained where the format/parser supports it.
- Empty, corrupt, malformed, and unsupported fixtures fail with safe typed errors and bounded resource use.
- Parser tests confirm document content is not included in logs or user-facing failure summaries.

### EKA-10 Implement stable chunking and source metadata

**Area:** Backend  
**Depends on:** EKA-03, EKA-09  
**Feature spec trace:** IN-04

**Work**

- Implement configurable chunk size/overlap and normalization while preserving ordering, parent document ID, source location, and deterministic chunk identity for an index version.
- Handle empty/short/long text and boundaries without generating empty or duplicate chunks.
- Persist chunking configuration/version with index metadata.

**Acceptance / test requirements**

- Unit tests verify configured chunk size and overlap, deterministic ordering/IDs, and no empty chunks.
- Integration test verifies chunks preserve document and source location metadata through persistence and can be resolved to their parent.
- Boundary tests cover text shorter than a chunk, exact boundaries, Unicode text, and page/section transitions.

### EKA-11 Implement Spring AI embeddings and pgvector storage

**Area:** Backend / Spring AI  
**Depends on:** EKA-01–EKA-03, EKA-10  
**Feature spec trace:** IN-05–IN-08

**Work**

- Configure the Spring AI embedding model (Ollama locally, OpenAI when deployed, DEC-002) and PostgreSQL/pgvector vector store, with the dimension set per profile.
- Implement bounded batch embedding and persist vectors with stable chunk/document/index-version and authorization metadata.
- Record embedding model/version and vector dimension; reject incompatible dimensions/configuration explicitly.
- Provide test doubles for deterministic embeddings and a controlled provider smoke test.

**Acceptance / test requirements**

- Adapter tests verify batching, metadata mapping, error propagation, and model/version capture.
- PostgreSQL integration test writes and retrieves vectors with the expected dimension and stable application IDs.
- Dimension mismatch and provider failure tests fail visibly without marking chunks indexed.
- A profile/configuration test proves provider and model can change via configuration without source edits.

### EKA-12 Implement durable ingestion processing and recovery

**Area:** Backend  
**Depends on:** EKA-03, EKA-07, EKA-09–EKA-11  
**Feature spec trace:** IN-01, IN-03, IN-05–IN-08

**Work**

- Implement bounded Spring-managed ingestion processing backed by persisted job state, including queued-job claiming, state transitions, retry policy, and startup reconciliation.
- Orchestrate extraction → chunking → embedding → index persistence → activation.
- Make writes idempotent and ensure a document is searchable only after the active index version is complete.
- On re-index, rebuild from the stored extracted text (the original file is not kept, DEC-005), activate the replacement safely, and exclude superseded chunks; record safe terminal errors.
- Use worker concurrency of 2 by default (DEC-007).

**Acceptance / test requirements**

- Integration tests verify valid ingestion reaches `INDEXED` and all chunks/vectors are queryable only after activation.
- Failure-injection tests cover parser, embedding, database write, and process-interruption/restart scenarios; no partial index is searchable.
- Retry/replay tests prove no duplicate active chunks and bounded attempts.
- Concurrency test proves a job is not simultaneously claimed by multiple workers and worker limits are enforced.

### EKA-13 Implement document status, retry, re-index, and deletion APIs

**Area:** Backend  
**Depends on:** EKA-03–EKA-05, EKA-12  
**Feature spec trace:** IN-07–IN-09, IN-11

**Work**

- Implement authorized list/get status, retry, re-index, and delete endpoints.
- Return only permitted metadata and safe error summaries; enforce permission checks per document/collection.
- Implement soft delete (DEC-006): mark the document deleted so it is excluded from retrieval, listings and citations immediately.
- Add a scheduled purge job that permanently removes chunks, vectors and derived records 7 days after deletion; retry purge failures and audit each deletion and purge.
- Ensure list pagination/filters are bounded and scope-constrained.

**Acceptance / test requirements**

- API tests cover permitted and denied list/status/retry/re-index/delete operations, including direct-ID access.
- Retry/re-index tests verify idempotency and correct visible state transitions.
- Deletion integration test verifies no retrieval, listing or citation access immediately after soft delete.
- Purge test verifies chunks and vectors are removed after the 7-day period (using a controllable clock) and that a purge failure is retried.
- Audit test verifies actor/action/outcome/timestamp/correlation ID are stored without raw document content or secrets.

### EKA-14 Build the document library and ingestion status UI

**Area:** UI  
**Depends on:** EKA-06, EKA-13  
**Feature spec trace:** IN-09–IN-11

**Work**

- Show only API-permitted documents and metadata with queued, processing, indexed, and failed statuses.
- Poll/refresh status with bounded cadence and clear network failure handling.
- Add authorized retry/re-index/delete controls and confirmation for deletion.
- Display safe failure summary without exposing internal error details.

**Acceptance / test requirements**

- Component tests cover all four visible states, stale/polling failure, safe error, permission-denied, retry, re-index, and delete interactions.
- E2E test observes status transition from queued/processing to indexed using a deterministic ingestion test setup.
- E2E/security test confirms denied actions are not exposed as successful and stale UI state cannot retrieve restricted metadata.
- Accessibility test covers keyboard controls, status announcements, and delete confirmation.

### EKA-15 Implement authorization-scoped semantic retrieval

**Area:** Backend / Spring AI  
**Depends on:** EKA-03–EKA-05, EKA-11–EKA-12  
**Feature spec trace:** RT-01–RT-08

**Work**

- Implement question embedding and pgvector similarity search for indexed active chunks, using top-K 5 by default (DEC-007) and excluding soft-deleted documents.
- Build mandatory collection/tenant/document filters from the authenticated principal and server-side grants; intersect them with supported user filters in the database query.
- Enforce top-K bounds and exclude non-active document/index versions.
- Return source-linked candidate objects to the answer service without exposing unauthorized passage text.

**Acceptance / test requirements**

- Integration tests verify top-K limits, exact metadata filters, active-index filtering, and fewer-than-K behavior.
- Adversarial tests tamper with collection/version/document identifiers and prove unauthorized text is absent from retrieval results and model-call arguments.
- Test provider spy confirms blank questions and unauthorized scopes do not invoke embedding/chat providers.
- Query plan/performance measurement records retrieval p95 on the documented local dataset; initial target is measured, not asserted without baseline.

### EKA-16 Implement evidence policy and grounded answer orchestration

**Area:** Backend / Spring AI  
**Depends on:** EKA-01, EKA-05, EKA-15  
**Feature spec trace:** RT-07, CH-01–CH-04, CH-07

**Work**

- Define deterministic context selection, deduplication, a 4000-token context budget, and a configurable evidence threshold starting at 0.70 per provider profile (DEC-007).
- Build a bounded prompt from only authorized retrieved chunks; instruct the model to answer from evidence and state uncertainty, treating document text as untrusted.
- Bypass the chat provider and return insufficient evidence when no candidates meet policy.
- Apply configured model timeout and bounded transient retries; surface safe errors.

**Acceptance / test requirements**

- Unit tests verify context budget, passage selection, prompt construction, and exclusion of non-authorized/low-evidence candidates.
- Integration tests use a deterministic fake chat model to prove the insufficient-evidence path makes no chat invocation.
- Prompt-injection fixtures verify the model cannot override policy or gain access to data outside retrieved context.
- Provider timeout/failure tests terminate within configured bounds and produce an explicit error, not a success-shaped empty answer.

### EKA-17 Implement chat API and validated citations

**Area:** Backend  
**Depends on:** EKA-04–EKA-05, EKA-15–EKA-16  
**Feature spec trace:** CH-01–CH-07, CH-09

**Work**

- Implement `POST /api/v1/chat/query` using the approved request/response contract and synchronous MVP behavior.
- Return `ANSWERED` with answer and citation objects, or `INSUFFICIENT_EVIDENCE` with clear uncertainty; include correlation ID and usage/timing only when available.
- Build citations from retrieved trusted chunk/document metadata; validate every model marker against the authorized retrieval set.
- Implement citation/source-detail lookup if the UI opens citations separately; recheck current permission and deletion state on every lookup.
- Do not persist questions or answers; conversation history and feedback are deferred (DEC-008).

**Acceptance / test requirements**

- Contract tests cover successful answer, insufficient evidence, blank input, unauthorized scope, validation error, and provider failure.
- Citation integration tests reject invented, unresolved, stale, or unauthorized model markers and verify valid metadata/excerpt resolution.
- Security tests revoke access/delete a source between answer and detail lookup and verify no source content is returned.
- Observability test verifies correlation ID propagation and confirms sensitive request/document/provider data is absent from default logs.

### EKA-18 Build the assistant, answer, and citation UI

**Area:** UI  
**Depends on:** EKA-06, EKA-05, EKA-17  
**Feature spec trace:** RT UI; CH-01–CH-10

**Work**

- Implement question input, authorized collection/version filters, pending/duplicate-submit handling, answer and insufficient-evidence states, and safe failure display.
- Render validated citations with source title and available page/section/excerpt; show unavailable state if source access is denied or stale.
- Render answers and excerpts as inert text or sanitized formatting, never active untrusted HTML.

**Acceptance / test requirements**

- Component tests cover pending, answered, insufficient evidence, validation, authorization, timeout, and provider-error states.
- E2E test asks an answerable question and opens the correct citation; a separate test asserts an unanswerable question shows uncertainty.
- UI security test renders script/markup-like model text and proves it does not execute.
- Accessibility test verifies keyboard submission, accessible labels/status, citation navigation, and error announcements.

### EKA-19 Add end-to-end, security, and evaluation coverage

**Area:** QA / Backend / UI  
**Depends on:** EKA-07–EKA-18  
**Feature spec trace:** UP-01–UP-06, IN-01–IN-11, RT-01–RT-09, CH-01–CH-10, E2E-01–E2E-07

**Work**

- Create versioned fixtures for supported document types, malformed/empty files, product/version metadata, and prompt-injection cases.
- Build an automated end-to-end path for upload → ingestion → retrieval → answer → citation.
- Add authorization isolation, tampered-filter, failure/retry, deletion, provider-timeout, and prompt-injection tests.
- Create a versioned labelled query set and repeatable evaluation output for retrieval and answer/citation quality.

**Acceptance / test requirements**

- E2E scenarios E2E-01 through E2E-07 in the Feature Specifications are automated where feasible; any manual-only case has a recorded repeatable procedure and result.
- Authorization and tenant-isolation suite passes with test identities having distinct grants.
- Evaluation run records dataset version, model/provider/configuration version, retrieval mode, Recall@5 and MRR or nDCG@5, citation validity, and latency.
- Release report records proposed PRD targets (80% supported/relevant answers and 95% citation validity) against the test set, identifies misses, and does not represent them as correctness guarantees.

### EKA-20 Package local deployment and release readiness

**Area:** Platform / All  
**Depends on:** EKA-02–EKA-04, EKA-07–EKA-19  
**Feature spec trace:** Shared; canonical Definition of Done

**Work**

- Add Docker Compose services and health dependencies for the backend and PostgreSQL/pgvector; include the frontend service or documented frontend startup.
- Document migrations, provider configuration with placeholders, startup, test commands, and safe cleanup/reset procedures.
- Configure readiness/liveness health, bounded worker settings, and redacted operational logging.
- Record known limitations and open decisions; ensure no secret values are committed.

**Acceptance / test requirements**

- A clean environment can follow the documented local startup path and complete the end-to-end smoke scenario.
- Health checks report database/application readiness without exposing secrets or detailed sensitive configuration.
- Automated build, unit, integration, security, and UI test commands are documented and pass in the supported environment.
- Setup/configuration review confirms credentials are externalized, original files are not stored, questions and answers are not persisted, and the 7-day soft-delete purge is enabled.

## 4. Release Gates

MVP implementation is ready for release review only when:

- All `Must` tasks and their required tests above are complete.
- Every acceptance criterion referenced by the four feature specifications has a test or an explicitly documented verification artifact.
- Authorization is enforced before retrieval text is used in Spring AI prompt/context construction, and no known critical isolation defect remains.
- A supported document can complete upload, ingestion, retrieval, grounded answer, and citation inspection end-to-end.
- Unsupported/invalid files, insufficient evidence, provider failures, retries, and deletion do not result in false success or stale searchable content.
- The versioned evaluation output reports actual results and known limitations.

## 5. Backlog Traceability

| Feature in Feature Specifications | Primary implementation tasks |
|---|---|
| Document upload (UP-01–UP-06) | EKA-04–EKA-08 |
| Ingestion and indexing (IN-01–IN-11) | EKA-03, EKA-09–EKA-14 |
| Retrieval (RT-01–RT-09) | EKA-04–EKA-06, EKA-11, EKA-15–EKA-16, EKA-18–EKA-19 |
| Chat answers and citations (CH-01–CH-10) | EKA-04–EKA-06, EKA-15–EKA-19 |
| Cross-feature / release readiness (E2E-01–E2E-07) | EKA-19–EKA-20 |

The Feature Specifications document remains authoritative for feature behavior and acceptance criteria. If this backlog's implementation detail conflicts with those criteria or the canonical requirements, update the backlog rather than weakening the acceptance contract.

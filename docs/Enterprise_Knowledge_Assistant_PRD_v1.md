# Enterprise Knowledge Assistant

## Product Requirements Document (PRD)

**Document ID:** EKA-PRD-001  
**Version / status:** 1.0 / Draft for review  
**Prepared date:** 2026-10-09  
**Product:** Enterprise Knowledge Assistant (EKA)  
**Canonical requirements source:** [`Enterprise_Knowledge_Assistant_Requirements_v1.md`](Enterprise_Knowledge_Assistant_Requirements_v1.md)  
**Target release:** MVP v1

> This PRD is a product-oriented view of the canonical requirements document, not a replacement for it. If details conflict, the canonical requirements document is authoritative. Proposed targets and assumptions remain subject to review and approval.

## 1. Product Summary

Enterprise Knowledge Assistant helps people find answers in an authorized collection of technical documents. A user asks a question in natural language; the product retrieves relevant passages and returns an answer with citations so the user can inspect the evidence. When the collection does not contain adequate evidence, the product communicates that limitation instead of presenting an unsupported answer as fact.

Knowledge managers can upload and manage documents and monitor whether they are ready to search. The MVP focuses on a dependable, verifiable document-question-answer workflow, with access controls applied throughout ingestion, retrieval, and citation display.

## 2. User Problems

| Problem | Who experiences it | Product response |
|---|---|---|
| Finding a specific answer across a collection of technical documents takes time and requires manually opening and searching files. | End User | Accept natural-language questions and retrieve relevant passages from indexed documents. |
| Answers without traceable evidence are difficult to trust or verify. | End User | Return citations linked to real, accessible source documents and passages; state when evidence is insufficient. |
| Information from the wrong product version or document can lead to outdated answers. | End User | Support collection and metadata filters, including product/version metadata, and enforce them on the server. |
| Uploaded knowledge is not useful until processing succeeds, but processing failures can be hard to diagnose. | Knowledge Manager | Show document ingestion status and a safe, actionable failure summary; support safe retry and re-indexing. |
| Sensitive or restricted documents must not become visible to the wrong users through search, citations, or generated context. | Administrators and all users | Enforce authorization during retrieval and before retrieved text reaches the language model. |

## 3. Goals and Success Measures

The measures below are proposed MVP targets from the canonical requirements document. They are evaluation targets, not guarantees of correctness or production service-level commitments.

| Goal | Proposed MVP measure |
|---|---|
| Provide useful answers grounded in the knowledge base. | At least 80% of a curated 50-question test set receives a supported, relevant answer. |
| Make answers verifiable. | At least 95% of factual answers in the evaluation set include valid source citations. |
| Protect restricted knowledge. | All authorization and tenant-isolation tests pass. |
| Establish retrieval quality. | Measure Recall@5 and MRR on a labelled retrieval dataset and record a baseline. |
| Make operation diagnosable. | Provide request correlation IDs, error logs, latency metrics, and token-usage metrics where available. |

The product should also report p50/p95 end-to-end answer latency, separating retrieval latency from model generation. Establish a target after measuring a representative baseline; do not infer a service commitment from an unbenchmarked estimate.

## 4. Personas

### End User — asks and verifies

An authenticated employee or project user who needs a reliable answer from documents they are permitted to access.

- **Needs:** Ask questions in ordinary language, narrow results to the relevant collection or version, understand when the evidence is incomplete, and inspect the sources behind an answer.
- **Success looks like:** The user can get a useful answer quickly, open its citations, and decide whether the evidence supports the answer.
- **MVP capabilities:** Submit a question; use supported collection/metadata filters; view an answer and its citations; receive a clear insufficient-evidence response.

### Knowledge Manager — curates the knowledge base

A user responsible for adding and maintaining technical documents in authorized collections.

- **Needs:** Upload supported files, see whether they are searchable, understand safe failure information, and correct processing issues without creating duplicate indexed content.
- **Success looks like:** A valid document can be uploaded and indexed, its status is visible, and it can be safely retried, re-indexed, or removed.
- **MVP capabilities:** Upload, list, inspect status, delete, and re-index documents within assigned permissions.

### Administrator — governs access and configuration

A privileged user responsible for managing roles, settings, and security-relevant activity.

- **Needs:** Control who can manage documents and access collections, configure approved system behavior, and review administrative or security events.
- **Success looks like:** Restricted actions are denied unless permitted, configuration is externalized, and important actions are auditable.
- **MVP capabilities:** Manage users/roles and system settings; inspect ingestion failures and audit events, subject to the implementation’s assigned administrative permissions.

### Operator / Developer — operates the service

A person who supports the running application and diagnoses service health without automatically receiving access to restricted document content.

- **Needs:** Health information, structured and correlated diagnostics, and safe signals about provider or ingestion failures.
- **Success looks like:** Operational issues can be diagnosed without exposing credentials or unrestricted document content.
- **MVP capabilities:** View health and redacted diagnostics; no implicit permission to read restricted document content.

## 5. MVP Product Experience

### Core user journey: ask with evidence

1. An authenticated End User opens the assistant and selects an authorized collection or applicable metadata filters.
2. The user submits a non-empty natural-language question.
3. EKA searches only content the user is authorized to access and applies the requested filters.
4. EKA returns a grounded answer with citations to accessible source documents and available page/section or passage details.
5. If there is no adequate evidence, EKA clearly says so rather than inventing a factual answer.
6. The user can inspect the cited source information.

### Core user journey: make knowledge searchable

1. An authorized Knowledge Manager uploads a supported PDF, DOCX, TXT, or Markdown document within configured limits.
2. EKA validates the file and exposes its ingestion state: queued, processing, indexed, or failed.
3. On success, extracted content is searchable and traceable to its source metadata.
4. On failure, the manager sees a safe error summary and can retry or re-index without creating duplicate active chunks.
5. An authorized manager can delete a document; after deletion completes, its content is excluded from retrieval.

## 6. MVP Boundaries

### Required for MVP

- Authenticated access and server-side role, collection, and document authorization.
- Upload validation and ingestion for PDF, DOCX, TXT, and Markdown, with configured size limits and clear rejection of unsupported, empty, corrupt, or oversized files.
- Text extraction, configurable chunking, embedding generation, indexing, and visible ingestion status.
- Safe, idempotent retries; re-indexing without duplicate active chunks; authorized document listing, inspection, and deletion.
- Natural-language question submission and semantic retrieval with configurable top-K and server-enforced access and metadata filters.
- Grounded answers with valid citations to authorized indexed sources, plus an insufficient-evidence response.
- Context and provider timeouts with bounded retries; safe errors, health checks, structured logs, correlation IDs, and relevant usage/latency metrics where available.
- A repeatable, versioned evaluation process that records dataset/configuration details and retrieval and answer-quality measures.
- Documented local development using Docker-based setup and automated tests for required behavior.

### Deferred after MVP

These are in the source requirements as lower priority and were deferred in [`implementation/product-decisions.md`](implementation/product-decisions.md) (DEC-008). Revisit them after the baseline evaluation.

- **Hybrid retrieval:** Combine semantic and keyword/full-text retrieval and record its measured effect.
- **Reranking:** Add when evaluation shows a retrieval gap.
- **Answer feedback:** Helpful/not-helpful rating with an optional reason.
- **Conversation history:** Questions and answers are not stored in the MVP.
- **Message broker (Kafka) for ingestion:** The MVP uses a bounded background worker with persisted jobs.

### Explicitly out of scope for MVP

- Autonomous agents that execute actions in enterprise systems.
- Training or fine-tuning a foundation model.
- Unrestricted crawling of company systems or the public web.
- Real-time voice, image understanding, or video transcription.
- Formal regulatory certification or claims of production compliance.
- Guaranteed correctness or use as the sole basis for high-impact decisions.
- Complex workflow approvals, billing, and multi-region disaster recovery.
- A production service-level commitment before representative benchmarking.

## 7. Product Requirements and Acceptance

| Priority | Product requirement | Acceptance outcome |
|---|---|---|
| Must | Authenticate users and authorize actions against roles and collection/document permissions. | Unauthenticated protected requests are rejected; unauthorized actions and retrievals disclose no document content. |
| Must | Accept and validate supported document uploads. | Valid supported files enter ingestion; invalid files receive a clear reason and are not indexed. |
| Must | Extract and index document text with traceable source metadata. | Supported test files produce searchable content or a recorded failure; indexed chunks map to their source document and available page/section metadata. |
| Must | Expose ingestion lifecycle and support safe recovery. | The UI distinguishes queued, processing, indexed, and failed; retries are idempotent and re-indexing leaves no duplicate active chunks. |
| Must | Answer questions from authorized evidence. | Answerable questions return supported answers with citations that resolve to accessible indexed sources. |
| Must | Handle missing or weak evidence honestly. | Unanswerable/out-of-domain questions receive an explicit uncertainty or insufficient-evidence response rather than a fabricated specific fact. |
| Must | Enforce filters and access policy before model context construction. | Tampering with client filters cannot expose chunks outside the caller’s authorization or requested scope. |
| Must | Support authorized document management and deletion. | Users see only permitted document metadata; after deletion completes, the document’s chunks are no longer retrievable and the outcome is auditable. |
| Must | Provide repeatable quality evaluation and operational diagnostics. | Evaluation runs record dataset and configuration/model versions; service diagnostics include correlation IDs and avoid logging secrets or full document content by default. |
| Deferred | Hybrid search, reranking and answer feedback (DEC-008). | Revisit after the baseline evaluation. |

Detailed functional, security, data, API, and non-functional requirements remain in the canonical requirements document.

## 8. UX and Trust Principles

- **Evidence first:** A citation must identify a real, accessible source; do not show invented or unresolved references.
- **Honest uncertainty:** When authorized retrieval does not provide adequate evidence, say so explicitly.
- **Access control is not a UI filter:** Authorization and metadata restrictions must be enforced before any text is placed in model context.
- **Clear processing state:** Distinguish queued, processing, indexed, and failed states, and give users understandable errors without sensitive payloads.
- **Safe by default:** Treat uploaded document text as untrusted input; do not let document instructions override system policies.
- **Understandable controls:** Core flows should be keyboard-operable and show clear loading, empty, success, and failure states.

## 9. Measurement and Evaluation

Evaluate each candidate release on the same versioned dataset and report:

- **Retrieval:** Recall@5 and MRR or nDCG@5 on 30–50 questions with known relevant source chunks.
- **Citation validity:** Percentage of citations resolving to real, authorized indexed chunks.
- **Groundedness and usefulness:** Human review using a documented rubric (correct, partially correct, incorrect); model-assisted judging must not be the sole measure.
- **Latency:** p50/p95 retrieval and end-to-end answer latency, reported separately.
- **Cost:** Provider token usage and cost per query where available.
- **Security:** Authorization and tenant-isolation scenarios, including tampered filters and attempts to access another user’s documents.

Report results alongside dataset version and model/retrieval configuration. The proposed 80% supported/relevant answer rate and 95% citation validity are initial targets that should be reviewed against the evaluation method and test corpus.

## 10. Risks, Assumptions, and Open Decisions

### Risks and mitigations

| Risk | Product impact | MVP response |
|---|---|---|
| Incorrect or weak retrieval produces a confident but unsupported answer. | Loss of user trust or misuse of the answer. | Require citations, insufficient-evidence behavior, and a repeatable groundedness/retrieval evaluation. |
| Authorization is applied too late or inconsistently. | Restricted document content may reach another user or an external model provider. | Enforce access policy during retrieval and before context construction; test adversarially. |
| Malformed or oversized files or provider failures interrupt ingestion and answering. | Users cannot make knowledge searchable or receive timely results. | Validate uploads, bound retries and timeouts, expose status, and return safe errors. |
| Retained documents, answers, or logs contain sensitive information longer than intended. | Privacy exposure and increased operational risk. | Minimize persisted conversation data, avoid sensitive logging, and define retention before enabling history. |
| Proposed quality or latency targets are treated as guarantees before benchmarking. | Misleading expectations and avoidable delivery commitments. | Label targets as proposed and establish baselines on representative data and hardware. |

### Assumptions and decisions to resolve

- Initial deployment is a single environment for a portfolio/MVP project.
- Preferred implementation stack: Java 21, Spring Boot 3.x, Spring AI, React + TypeScript, and PostgreSQL with pgvector.
- Select an LLM and embedding provider before implementation; keep provider settings externalized.
- PostgreSQL full-text search is the proposed first keyword-search option; Kafka is optional, and a simpler background mechanism is acceptable for the first thin slice.
- Decided (see [`implementation/product-decisions.md`](implementation/product-decisions.md)): local dev auth plus OIDC; Ollama locally and OpenAI when deployed; 10 MB upload limit; duplicates rejected; original files not stored; soft delete with a 7-day purge; hybrid retrieval, reranking, feedback, conversation history and Kafka deferred.
- Still open: OIDC provider product and exact model names.
- Benchmark performance on a named environment before committing to latency targets.

## 11. Release Readiness

MVP is ready for release review when:

- Every Must requirement has a test or explicit verification artifact.
- No known critical authorization or tenant-isolation defect remains.
- The end-to-end upload → indexing → cited answer journey works for supported files.
- Insufficient evidence and provider/ingestion failures are handled without silently claiming success.
- Evaluation data and benchmark results are versioned and reproducible.
- Secrets are externalized, logs avoid raw credentials and full document content by default, and retention behavior is documented.
- Docker-based setup instructions are reproducible, and known limitations and out-of-scope capabilities are documented.

## 12. Source Traceability

This PRD derives from [`Enterprise_Knowledge_Assistant_Requirements_v1.md`](Enterprise_Knowledge_Assistant_Requirements_v1.md). Requirement priorities and detailed acceptance criteria are maintained there. Key traceability:

| PRD area | Canonical source sections |
|---|---|
| Goals and measures | Goals and Success Measures |
| MVP boundaries and roles | Scope; Users and Roles |
| Product journeys | User Stories and Core Workflows |
| Product requirements | Functional Requirements; Acceptance Test Scenarios |
| UX, trust, and risks | Retrieval and RAG Behavior; Security and Abuse Cases |
| Quality and evaluation | Non-Functional Requirements; Evaluation Plan |
| Release readiness and open decisions | Definition of Done; Open Decisions Before Implementation |

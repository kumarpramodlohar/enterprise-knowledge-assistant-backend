# EKA Evaluation Suite

The canonical Node.js CLI is `node eka_eval_suite/evaluate.mjs`. The former `evaluation/` root and legacy CLI were removed by the user-approved consolidation on 2026-11-16.

Dataset-building utilities and examples are under `eka_eval_suite/datasets/`: `seed/` contains candidate questions, while `java-rag-smoke-test/` is a manual synthetic ingestion/chat smoke-test pack, not a scored representative evaluation set. `build-dataset.mjs` resolves seed evidence phrases to indexed chunk IDs; use only with an authorized collection and the documented local database credentials.

## Retrieval evaluation

Provide an authorized JSONL dataset outside source control and run the backend as a real authorized user. To include raw candidates in the response, activate `evaluation` together with the local profile, for example `SPRING_PROFILES_ACTIVE=local,evaluation`. The response diagnostics are omitted unless `evaluation` is active and are always suppressed whenever `deployed` is active. Only chunk IDs, ranks, and similarity scores are returned; authorization and request filters apply before the response is built.

`similarityScore` is `1 - cosine distance`; the backend orders candidates by ascending cosine distance, so higher scores appear at earlier ranks. The normal answer path still applies the existing top-K and evidence threshold. The evaluation response window is bounded by the configured diagnostic candidate count.

Required environment variables are `EKA_EVALUATION_DATASET`, `EKA_LOCAL_USER`, `EKA_LOCAL_USER_PASSWORD`, `EKA_EVAL_MODEL_VERSION`, and `EKA_EVAL_CONFIG_VERSION`. The dataset path may also be the first CLI argument. Supply safe corpus/index identifiers for valid baseline comparisons with `EKA_EVAL_CORPUS_VERSION` and `EKA_EVAL_INDEX_ID`. Optional run controls are `EKA_EVAL_TOP_K` (default 5), `EKA_EVAL_TIMEOUT_MS` (default 30000), `EKA_EVAL_CASE_LIMIT`, `EKA_EVAL_FAILURE_BUDGET` (default 0), `EKA_EVAL_SIMILARITY_THRESHOLD`, `EKA_EVAL_DATASET_VERSION`, `EKA_EVAL_CHUNKING_CONFIG`, `EKA_EVAL_EMBEDDING_CONFIG`, `EKA_EVAL_EMBEDDING_VERSION`, `EKA_EVAL_PROMPT_VERSION`, `EKA_EVAL_BASELINE`, `EKA_API_BASE_URL`, `EKA_EVAL_REPORT_DIR`, `EKA_EVAL_MODE`, and `EKA_EVAL_CRAG_BASE_URL`.

### Runner authentication

The current Node runner authenticates with HTTP Basic using `EKA_LOCAL_USER` and `EKA_LOCAL_USER_PASSWORD`; it does not currently support OIDC bearer tokens. These must match the backend local-profile credentials. The same username must also exist in the backend database with `READ` or `MANAGE` access to the collection used by the dataset. The backend reads its ignored `.env`; the runner process does not, so export both variables in the runner shell. OIDC runner authentication is tracked separately (EV19/B04).

### One-command local retrieval run

`bash eka_eval_suite/run-local.sh` checks the local toolchain and reviewed dataset sidecar, starts the Compose PostgreSQL service, builds and launches the backend with `local,evaluation`, verifies the local Ollama models and the user's readable collection scope, runs the retrieval CLI, and stops the backend process afterward. PostgreSQL is left running. It supports ollama or openai providers (set via backend `.env`; openai needs `OPENAI_API_KEY` and sends evaluation text to OpenAI, so use only authorized content). CRAG is configurable via `EKA_CRAG_ENABLED` (default false). It refuses enabled web search, non-loopback Ollama URLs, mismatched thresholds/configuration, and datasets not marked `reviewed-gold`.

Before running it, create the backend `.env` from `.env.example`, start Ollama locally and pull the configured chat/embedding models. Export these values in the shell that launches the script:

```bash
export EKA_EVALUATION_DATASET=/secure/path/retrieval.jsonl
export EKA_LOCAL_USER=eval-reader
export EKA_LOCAL_USER_PASSWORD='<set-locally>'
export EKA_EVAL_MODEL_VERSION=ollama-model-version
export EKA_EVAL_CONFIG_VERSION=retrieval-config-1
export EKA_EVAL_CORPUS_VERSION=corpus-version
export EKA_EVAL_INDEX_ID=vector-index-id
export EKA_EVAL_COLLECTION_IDS='<collection-uuid>'
export EKA_EVAL_CHUNKING_CONFIG=chunker-version
export EKA_EVAL_EMBEDDING_CONFIG=embedding-config
export EKA_EVAL_SIMILARITY_THRESHOLD=0.30
bash eka_eval_suite/run-local.sh
```

The dataset sidecar must have `status: "reviewed-gold"`; its `authorizedScope` must be a comma-separated list matching `EKA_EVAL_COLLECTION_IDS`, and its corpus/index/chunking/embedding identities must match the exported values. The effective backend threshold is read from the environment or `.env` and must match `EKA_EVAL_SIMILARITY_THRESHOLD`. The script is for a real local API evaluation; it does not run JUnit or the Node unit tests. Run those separately with `node --test eka_eval_suite/test/*.test.mjs`.

### Run the retrieval stage

For a single-command local-only run, use `bash eka_eval_suite/run-local.sh` from the backend repository root. It requires JDK 21+, Node.js 20+, Docker Compose, a backend `.env`, local Ollama chat and embedding models, an authorized local Basic-auth user, and a reviewed-gold dataset with a provenance sidecar. It starts PostgreSQL and the backend, runs the API preflight and retrieval evaluation, then stops the backend while leaving PostgreSQL running. CRAG is configurable via `EKA_CRAG_ENABLED` (default false). It refuses enabled web search, non-reviewed datasets, scope/configuration mismatches, and non-loopback endpoints.

Export the evaluation-specific values before invoking it; credentials are read from the shell environment, never printed by the script:

```bash
export EKA_EVALUATION_DATASET=/secure/path/retrieval.jsonl
export EKA_LOCAL_USER=eval-reader
export EKA_LOCAL_USER_PASSWORD='<set-locally>'
export EKA_EVAL_MODEL_VERSION=ollama-model-version
export EKA_EVAL_CONFIG_VERSION=retrieval-config-1
export EKA_EVAL_CORPUS_VERSION=corpus-version
export EKA_EVAL_INDEX_ID=vector-index-id
export EKA_EVAL_COLLECTION_IDS='<collection-uuid>'
export EKA_EVAL_CHUNKING_CONFIG=chunker-version
export EKA_EVAL_EMBEDDING_CONFIG=embedding-config
export EKA_EVAL_SIMILARITY_THRESHOLD=0.30
bash eka_eval_suite/run-local.sh
```

`EKA_EVAL_COLLECTION_IDS` must exactly match the comma-separated collection UUIDs in the sidecar's `authorizedScope`, and the local user must have read access to each one. The script runs a `/api/v1/me` preflight and fails before evaluation if scope is missing. It does not run JUnit; unit/mock-API tests remain `node --test eka_eval_suite/test/*.test.mjs`.

For manual startup or non-local configurations, use the steps below.

1. Prepare the prerequisites: JDK 21, Node.js, Docker/Compose, PostgreSQL with pgvector, and the selected local model provider. Follow the backend's [local setup](../README.md#local-setup); configure the backend's ignored `.env` with the local credentials, and create or seed a real local user with read access to the authorized evaluation collection.
2. Prepare a reviewed, access-controlled JSONL dataset whose `relevantChunkIds` are actual indexed chunk IDs. Add `<dataset>.metadata.json` with the chunking/embedding label configuration, reviewer, and review date. The checked-in `datasets/retrieval-fixture.jsonl` is only a schema fixture and is not suitable for a quality run.
3. Configure and start the backend in one terminal. The `evaluation` profile exposes raw metadata only when `deployed` is not active:

   ```bash
   export SPRING_PROFILES_ACTIVE=local,evaluation
   export EKA_RETRIEVAL_TOP_K=10
   export EKA_RETRIEVAL_DIAGNOSTIC_CANDIDATE_COUNT=10
   export EKA_RETRIEVAL_SIMILARITY_THRESHOLD=0.30  # set to the effective value being evaluated
   ./gradlew bootRun
   ```

   `EKA_RETRIEVAL_DIAGNOSTIC_CANDIDATE_COUNT` bounds the response window. Keep it at least as large as evaluation K; using a value greater than K allows the report to identify gold chunks ranked below K. The checked-in backend default threshold is `0.30`, while DEC-007 specifies `0.70`; this discrepancy is tracked separately. Do not change the default as part of evaluation setup. Set the value you intend to measure and record it in the config version.

4. In a second terminal, set the dataset, safe run identities, and matching evaluation threshold. The Node runner does not load the backend `.env`; export the same local username/password into this shell or provide them through a secret manager. Do not paste passwords into chat or put them in the dataset/report.

   ```bash
   export EKA_EVALUATION_DATASET=/secure/path/retrieval.jsonl
   export EKA_LOCAL_USER=eval-reader
   export EKA_LOCAL_USER_PASSWORD='<replace-with-your-local-password>'
   export EKA_EVAL_MODEL_VERSION=ollama-model-version
   export EKA_EVAL_CONFIG_VERSION=retrieval-config-1
   export EKA_EVAL_CORPUS_VERSION=corpus-version
   export EKA_EVAL_INDEX_ID=vector-index-id
   export EKA_EVAL_TOP_K=5
   export EKA_EVAL_SIMILARITY_THRESHOLD=0.30
   curl -fsS -u "$EKA_LOCAL_USER:$EKA_LOCAL_USER_PASSWORD" http://localhost:8080/api/v1/me
   node eka_eval_suite/evaluate.mjs
   ```

   The `/api/v1/me` preflight should succeed and list the evaluation collection in `readableCollectionIds`. `EKA_EVAL_SIMILARITY_THRESHOLD` must match the backend value. If omitted, threshold-specific miss causes are marked unavailable. JSON and Markdown reports are written under `eka_eval_suite/reports/` unless `EKA_EVAL_REPORT_DIR` is set.

5. Inspect `<runId>.md` for the summary, coverage, category slices, worst cases, and miss causes; inspect `<runId>.json` for per-case gold/retrieved/found IDs, scores, timing, configuration identity, and baseline-comparison status. The report contains no question or answer text. A report-only run is not approval; use the stage-approval process below only after the product owner reviews the exact run.

To run the local unit and mock-API tests without starting the backend, use `node --test eka_eval_suite/test/*.test.mjs`.

Reports are JSON and Markdown under `eka_eval_suite/reports/` by default. They contain identifiers and metrics only, not questions, answers, passages, or judge reasons. The optional `EKA_EVAL_REVIEW_SHEET` export contains question and answer text and is only for explicitly non-confidential datasets; keep its destination access-controlled.

## Stage approvals

The runner never approves a stage. After reviewers inspect an exact report, create a redacted approval JSON record with the fields `schemaVersion`, `stageId`, `stageName`, `decision`, `approverIdentity`, `decidedAt`, `runId`, `reportReference`, `reportSha256`, `artifactId`, `commitId`, `datasetIdentity`, `corpusIndexIdentity`, `effectiveConfigurationIdentity`, `coverage` (`measured`, `unavailable`, `notRun`), `acceptedLimitations`, `requiredRemediation`, and `nextPermittedStage`. Use `approved`, `rejected`, or `approved-with-recorded-limitations` for `decision`. Then run:

```bash
node eka_eval_suite/record-approval.mjs eka_eval_suite/reports/<run>.json /access-controlled/<run>.approval.json
```

The writer checks that the report hash and run ID match and uses exclusive creation, so an existing record cannot be overwritten. Keep approval files beside the access-controlled report store; never put question, answer, passage, credential, or provider text in the record.

Without raw-candidate diagnostics, retrieval scores are labeled citation-based. The runner reports raw-candidate Recall@K, MRR, and hit rate when the evaluation profile returns the diagnostics field. Misses outside the returned candidate window are classified as absent from that window; this does not assert that a chunk is absent from the entire index.

## Reading the per-case retrieval report

Each entry in the report JSON's per-case table describes one dataset case. Example (chunk IDs shortened):

```json
{
  "id": "VS-031", "category": "enumset", "status": "measured", "method": "retrieval-based",
  "answerable": true, "eligibleForRecall": true,
  "goldIds": ["8d45"],
  "retrievedIds": ["30ea", "8d45", "ed63", "d641", "44d7"],
  "foundIds": ["8d45"],
  "recallAtK": 1, "recallAt5": 1, "reciprocalRank": 0.5, "hitRate": 1,
  "misses": [], "diagnosticCandidateCount": 10,
  "citationFieldCompleteness": 1, "evidenceClassificationCorrect": true, "insufficientEvidence": false,
  "latencyMs": 17818.09, "retrievalMs": 632,
  "groundedness": null, "usefulness": null, "error": null
}
```

| Field | Meaning |
| --- | --- |
| `status` | `measured` (response received), `unavailable` (call errored), or `not-run`. |
| `method` | `retrieval-based` when raw candidates came from the `evaluation` profile; `citation-based` when only returned citations were available. |
| `eligibleForRecall` | `true` only for answerable cases with at least one gold chunk. Other cases are excluded from Recall, MRR, and hit rate. |
| `goldIds` / `retrievedIds` / `foundIds` | Dataset `relevantChunkIds`; the top-K chunk IDs returned in rank order; the gold IDs that appear in the retrieved list. |
| `recallAtK` / `recallAt5` | `foundIds` ÷ `goldIds` within the evaluated K window / within ranks 1–5. Example: 2 gold chunks with 1 in the top 5 gives 0.5. |
| `reciprocalRank` | 1 ÷ rank of the first gold chunk, or 0 if none. The mean over eligible cases is MRR. In the example, gold is rank 2, so 0.5. |
| `hitRate` | 1 if any gold chunk was retrieved, otherwise 0. |
| `misses` | One entry per gold chunk not found, with a cause (for example below threshold, or ranked outside K). Empty here. |
| `diagnosticCandidateCount` | Raw candidates exposed by the backend (`EKA_RETRIEVAL_DIAGNOSTIC_CANDIDATE_COUNT`); `null` when citation-based. |
| `citationFieldCompleteness` | Share of returned citations with all required fields. This is field presence, not whether the citation supports the claim. |
| `evidenceClassificationCorrect` | Whether the backend's `insufficientEvidence` matches the expected behavior (answer vs. abstain). |
| `latencyMs` / `retrievalMs` | End-to-end chat query time / retrieval-stage time. A large gap means time is spent outside retrieval, mostly generation (and CRAG calls when enabled). |
| `groundedness` / `usefulness` | Human review scores; `null` until a reviewer provides them. |

In the example, the gold chunk was retrieved at rank 2: recall is full, ranking is imperfect (MRR 0.5), and nearly all latency is generation rather than retrieval. Summary Recall@K, MRR, and hit rate are means over eligible, measured cases only; the report states how many cases were used.

## Tests

Run the unit tests without a backend or model provider:

```bash
node --test eka_eval_suite/test/*.test.mjs
```

# Evaluation datasets

`retrieval-fixture.jsonl` is non-confidential schema/test material only. Its chunk IDs are placeholders, its answers are illustrative, and it is not reviewed gold or representative EKA quality evidence. Do not use it to claim retrieval quality or release readiness.

Confidential or access-controlled datasets belong outside source control. Add a companion `<dataset>.metadata.json` sidecar with `version`, `status`, `source`, `sourceDatasetId`, `corpusVersion`, `indexId`, `authorizedScope`, `chunkingConfig`, `embeddingConfig`, `reviewer`, `reviewDate`, and `split` as applicable. Allowed statuses are `fixture-only`, `draft_pending_human_review`, `candidate`, and `reviewed-gold`. The sidecar hash and provenance are recorded in reports; missing or changed label-configuration metadata is reported as a limitation or makes baseline comparisons incomparable. Re-label chunk IDs after relevant index changes.

## Spec 011 candidate datasets

The candidate files in `enterprise-knowledge-assistant-spec/specs/011-evaluation-pipeline/datasets/` are the source-of-truth review artifacts. They are all marked `draft_pending_human_review`; none is a runner-ready dataset or approved gold. Keep them there rather than copying and forking them here.

- `java-textbook-evaluation-candidates.json` is a retrieval/answerability candidate set. Its `evidence_pages` are reviewer hints, not exhaustive relevance labels. After indexing the exact authorized source, reviewers must verify answerability and map every relevant item to actual indexed chunk IDs.
- `java-textbook-generator-evaluation-candidates.json` is for fixed-context generation evaluation. Its `evidence_fixture` is a paraphrase, not indexed passage text or an EKA citation. Verify it against the authorized source and replace it with observed authorized context and actual citation IDs before a scored run. Do not use it to score retrieval.
- `leakage-evaluation-candidates.json` and `scope-adherence-evaluation-candidates.json` describe isolated synthetic identities, collections, fixtures, boundaries, and expected checks. Their logical IDs are not database, chunk, or citation IDs. Resolve test-environment identities and observe each required boundary; unavailable boundaries are not passes.
- `toxicity-evaluation-candidates.json` contains synthetic harmful-request and benign-control pairs. Its expected behaviors require policy review; do not turn pending policy into an automatic pass/fail label.

## Promotion to runner JSONL

1. Select the candidate family and evaluation target; retain its `dataset_id`, `status`, source, instructions, and case IDs in the review record.
2. Verify source authorization and applicable license terms. Keep confidential source material and derived datasets in access-controlled storage.
3. Have reviewers verify each case, answerability, reference answer, expected behavior, complete relevant evidence, negative labels, and any pending-policy state. Record reviewer provenance and disagreements.
4. For retrieval cases, resolve actual indexed chunk IDs from the authorized corpus. `evidence_pages`, fixture IDs, and guessed IDs must never be copied into `relevantChunkIds`.
5. Convert only reviewed cases to the runner JSONL fields (`id`, `question`, `answerable`, `relevantChunkIds`, and approved optional fields). The runner's `build-dataset.mjs` helper consumes JSONL seed rows with evidence phrases; it does not directly convert the spec's JSON candidate documents or prove those candidates gold.
6. Store the JSONL and metadata sidecar under controlled access, freeze/hash both, record corpus/index and label configuration, and protect any held-out split from tuning. When a case has distinct provenance or reviewer state, retain it as case-level metadata in the JSONL rather than flattening it into a global label.

Candidate artifacts can be useful for planning and review before implementation, but they must not be used as evidence for retrieval, generation, or safety quality until promoted through this review process.

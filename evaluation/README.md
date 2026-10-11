# Retrieval and answer evaluation

The API does not ship with a representative, human-labelled enterprise corpus. Keep evaluation data in a separately access-controlled JSONL file and pass its path to the runner; do not add confidential source text or production questions to this repository.

Each row has this shape:

```json
{"id":"case-001","question":"A question grounded in the private evaluation corpus","answerable":true,"relevantChunkIds":["trusted-chunk-uuid"],"humanReview":{"groundedness":4,"usefulness":5}}
```

Use an empty `relevantChunkIds` array for unanswerable questions. Human review scores are optional 1–5 ratings. Keep a dataset version in `EKA_EVAL_CONFIG_VERSION`; the runner also records the dataset SHA-256. It reports Recall@5, MRR, citation-field completeness, evidence classification accuracy, retrieval and end-to-end latency, and averages of available human scores. Citation IDs are produced from trusted application metadata; API integration/security tests cover access and source resolution. Provider cost is reported as unavailable because the MVP response does not expose usage/cost.

With the backend in the local profile, set `EKA_EVALUATION_DATASET`, `EKA_LOCAL_USER`, `EKA_LOCAL_USER_PASSWORD`, `EKA_EVAL_MODEL_VERSION`, and `EKA_EVAL_CONFIG_VERSION`, then run:

```bash
node evaluation/evaluate.mjs
```

The runner uses `/api/v1/chat/query`, aborts on API failures, and does not print question or answer text. Evaluation targets in the specification remain proposals and are not release gates. T18 remains incomplete until a representative labelled dataset, human review, adversarial checks, and a reproducible run/report are supplied.

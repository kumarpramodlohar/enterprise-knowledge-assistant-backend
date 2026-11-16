import assert from "node:assert/strict";
import test from "node:test";
import { createReport, renderMarkdown } from "../lib/report.mjs";

test("report includes slices and limitations without question or answer text", () => {
  const report = createReport({
    metadata: {
      runId: "run-1",
      datasetVersion: "v1",
      datasetSha256: "abc",
      failureBudget: 0,
    },
    metrics: {
      k: 5,
      retrievalMethod: "citation-based",
      sampleSize: 1,
      meanRecallAtK: 0,
      mrr: 0,
      hitRate: 0,
      citationFieldCompleteness: null,
      evidenceClassificationAccuracy: 1,
      meanEndToEndLatencyMs: 12,
      meanRetrievalLatencyMs: 3,
      datasetSha256: "abc",
      rows: [
        {
          id: "case-1",
          category: "factual",
          status: "measured",
          method: "citation-based",
          eligibleForRecall: true,
          recallAtK: 0,
          reciprocalRank: 0,
          hitRate: 0,
          misses: [{ chunkId: "gold-1", cause: "unavailable-citation-based" }],
        },
      ],
    },
  });
  const text = `${JSON.stringify(report)}\n${renderMarkdown(report)}`;

  assert.equal(report.metrics.categorySlices.factual.caseCount, 1);
  assert.equal(report.baselineComparison.status, "unavailable");
  assert.match(text, /citation-based/);
  assert.doesNotMatch(text, /not included in report|actual answer content/);
});

test("baseline comparison is refused when dataset hashes differ", () => {
  const report = createReport({
    metadata: {
      runId: "run-2",
      datasetVersion: "v1",
      datasetSha256: "current",
      failureBudget: 0,
    },
    metrics: {
      k: 5,
      retrievalMethod: "retrieval-based",
      sampleSize: 0,
      rows: [],
      datasetSha256: "current",
    },
    baseline: { datasetSha256: "old", metrics: { meanRecallAtK: 0.5 } },
  });

  assert.equal(report.baselineComparison.status, "incomparable");
  assert.equal(report.baselineComparison.deltas, undefined);
});

test("baseline deltas are emitted only for matching corpus, index, versions, and configuration", () => {
  const metadata = {
    runId: "run-3",
    datasetSha256: "same-dataset",
    datasetMetadataSha256: "same-sidecar",
    datasetVersion: "gold-1",
    modelVersion: "model-1",
    configVersion: "config-1",
    corpusVersion: "corpus-1",
    indexId: "index-1",
    embeddingVersion: "embed-1",
    promptVersion: "prompt-1",
    effectiveConfiguration: { topK: 5 },
    failureBudget: 0,
  };
  const report = createReport({
    metadata,
    metrics: {
      k: 5,
      retrievalMethod: "retrieval-based",
      sampleSize: 1,
      meanRecallAtK: 0.75,
      mrr: 0.75,
      hitRate: 1,
      rows: [],
    },
    baseline: {
      datasetSha256: "same-dataset",
      metadata: { ...metadata, runId: "run-previous" },
      metrics: { meanRecallAtK: 0.5, mrr: 0.5, hitRate: 1 },
    },
  });

  assert.equal(report.baselineComparison.status, "comparable");
  assert.equal(report.baselineComparison.deltas.meanRecallAtK, 0.25);
});

test("baseline comparison is refused when dataset provenance sidecar hashes differ", () => {
  const metadata = {
    runId: "run-sidecar",
    datasetSha256: "same-dataset",
    datasetMetadataSha256: "current-sidecar",
    datasetVersion: "gold-1",
    modelVersion: "model-1",
    configVersion: "config-1",
    corpusVersion: "corpus-1",
    indexId: "index-1",
    embeddingVersion: "embed-1",
    promptVersion: "prompt-1",
    effectiveConfiguration: { topK: 5 },
    failureBudget: 0,
  };
  const report = createReport({
    metadata,
    metrics: {
      k: 5,
      retrievalMethod: "retrieval-based",
      sampleSize: 0,
      rows: [],
    },
    baseline: {
      datasetSha256: "same-dataset",
      metadata: {
        ...metadata,
        runId: "run-old",
        datasetMetadataSha256: "old-sidecar",
      },
      metrics: {},
    },
  });

  assert.equal(report.baselineComparison.status, "incomparable");
  assert.match(report.baselineComparison.reason, /sidecar hashes differ/);
});

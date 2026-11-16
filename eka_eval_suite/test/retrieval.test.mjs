import assert from "node:assert/strict";
import test from "node:test";
import { computeRetrievalMetrics } from "../lib/metrics/retrieval.mjs";

const cases = [
  {
    id: "case-1",
    question: "not included in report",
    answerable: true,
    relevantChunkIds: ["a", "b"],
    category: "multi-hop",
  },
];

test("computes raw-candidate Recall@K, MRR, and hit rate separately from citations", () => {
  const metrics = computeRetrievalMetrics(
    cases,
    [
      {
        id: "case-1",
        latencyMs: 21,
        response: {
          citations: [
            { chunkId: "a", documentId: "doc", fileName: "f", snippet: "s" },
          ],
          retrievalDiagnostics: {
            candidates: [
              { chunkId: "x", rank: 1, similarityScore: 0.9 },
              { chunkId: "a", rank: 2, similarityScore: 0.8 },
              { chunkId: "b", rank: 6, similarityScore: 0.7 },
            ],
          },
          insufficientEvidence: false,
          retrievalMs: 8,
        },
      },
    ],
    { k: 5, similarityThreshold: 0.75 },
  );

  assert.equal(metrics.retrievalMethod, "retrieval-based");
  assert.equal(metrics.sampleSize, 1);
  assert.equal(metrics.meanRecallAtK, 0.5);
  assert.equal(metrics.mrr, 0.5);
  assert.equal(metrics.hitRate, 1);
  assert.deepEqual(metrics.rows[0].misses, [
    { chunkId: "b", cause: "ranked-below-k" },
  ]);
});

test("labels the legacy citation fallback and excludes unanswerable cases from recall", () => {
  const metrics = computeRetrievalMetrics(
    [
      { id: "case-1", answerable: true, relevantChunkIds: ["a"] },
      { id: "case-2", answerable: false, relevantChunkIds: [] },
    ],
    [
      {
        id: "case-1",
        response: {
          citations: [{ chunkId: "a" }],
          insufficientEvidence: false,
        },
      },
      { id: "case-2", response: { citations: [], insufficientEvidence: true } },
    ],
  );

  assert.equal(metrics.retrievalMethod, "citation-based");
  assert.equal(metrics.sampleSize, 1);
  assert.equal(metrics.meanRecallAtK, 1);
  assert.equal(metrics.rows[1].recallAtK, null);
});

test("classifies a top-K threshold miss separately from a candidate absent from the returned window", () => {
  const metrics = computeRetrievalMetrics(
    [
      {
        id: "case-3",
        answerable: true,
        relevantChunkIds: ["below-threshold", "absent"],
      },
    ],
    [
      {
        id: "case-3",
        response: {
          citations: [],
          insufficientEvidence: true,
          retrievalDiagnostics: {
            candidates: [
              { chunkId: "below-threshold", rank: 1, similarityScore: 0.2 },
            ],
          },
        },
      },
    ],
    { k: 5, similarityThreshold: 0.7 },
  );

  assert.deepEqual(metrics.rows[0].misses, [
    { chunkId: "below-threshold", cause: "below-threshold" },
    { chunkId: "absent", cause: "absent" },
  ]);
});

test("does not infer threshold misses when the effective threshold is unknown", () => {
  const metrics = computeRetrievalMetrics(
    [
      {
        id: "case-4",
        answerable: true,
        relevantChunkIds: ["gold"],
      },
    ],
    [
      {
        id: "case-4",
        response: {
          citations: [],
          insufficientEvidence: true,
          retrievalDiagnostics: {
            candidates: [{ chunkId: "gold", rank: 1, similarityScore: 0.4 }],
          },
        },
      },
    ],
  );

  assert.deepEqual(metrics.rows[0].misses, [
    { chunkId: "gold", cause: "threshold-unavailable" },
  ]);
});

import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { loadDatasetFile, parseDataset } from "../lib/dataset.mjs";

test("accepts the existing v1 JSONL row and hashes the exact dataset", () => {
  const raw =
    '{"id":"case-1","question":"Where is the policy?","answerable":true,"relevantChunkIds":["chunk-1"]}\n';
  const dataset = parseDataset(raw);

  assert.equal(dataset.schemaVersion, "v1");
  assert.equal(dataset.cases.length, 1);
  assert.match(dataset.datasetSha256, /^[a-f0-9]{64}$/);
});

test("accepts the optional v2 fields", () => {
  const raw =
    '{"id":"case-2","question":"Where is the policy?","answerable":true,"relevantChunkIds":["chunk-1"],"referenceAnswer":"The policy is in HR.","category":"factual","source":"curated","expectedBehavior":"answer","tags":["hr"],"difficulty":"easy"}\n';

  assert.equal(parseDataset(raw).schemaVersion, "v2");
});

test("rejects an invalid row with its row number and without its question text", () => {
  const raw = [
    '{"id":"case-1","question":"safe first question","answerable":true,"relevantChunkIds":[]}',
    '{"id":"case-2","question":"sensitive question text","answerable":"yes","relevantChunkIds":[]}',
  ].join("\n");

  assert.throws(
    () => parseDataset(raw),
    (error) => {
      assert.match(error.message, /row 2/);
      assert.doesNotMatch(error.message, /sensitive question text/);
      return true;
    },
  );
});

test("rejects an empty dataset safely", () => {
  assert.throws(() => parseDataset("\n  \n"), /no rows/);
});

test("rejects duplicate case IDs", () => {
  const row =
    '{"id":"duplicate","question":"q","answerable":true,"relevantChunkIds":[]}';
  assert.throws(() => parseDataset(`${row}\n${row}\n`), /duplicates a case ID/);
});

test("checked-in fixture covers retrieval categories and matches its sidecar", async () => {
  const path = fileURLToPath(
    new URL("../datasets/retrieval-fixture.jsonl", import.meta.url),
  );
  const dataset = await loadDatasetFile(path, {
    chunkingConfig: "fixture-only",
    embeddingConfig: "fixture-only",
  });

  assert.equal(dataset.cases.length, 5);
  assert.equal(dataset.datasetMetadata.status, "fixture-only");
  assert.match(dataset.datasetMetadataSha256, /^[a-f0-9]{64}$/);
  assert.deepEqual(dataset.configurationWarnings, [
    "Dataset status is 'fixture-only', not reviewed gold.",
  ]);
  assert.deepEqual(
    new Set(dataset.cases.map((item) => item.category)),
    new Set([
      "factual",
      "multi-hop",
      "unanswerable",
      "adversarial",
      "latency-probe",
    ]),
  );
});

test("warns when stored chunk-label configuration differs from the current run", async () => {
  const directory = await mkdtemp(join(tmpdir(), "eka-dataset-"));
  const path = join(directory, "cases.jsonl");
  try {
    await writeFile(
      path,
      '{"id":"c","question":"q","answerable":true,"relevantChunkIds":["id"]}\n',
    );
    await writeFile(
      `${path}.metadata.json`,
      JSON.stringify({
        version: "gold-1",
        status: "reviewed-gold",
        chunkingConfig: "chunker-1",
        embeddingConfig: "embed-1",
        reviewer: "reviewer-1",
        reviewDate: "2026-11-16",
      }),
    );

    const dataset = await loadDatasetFile(path, {
      chunkingConfig: "chunker-2",
      embeddingConfig: "embed-1",
    });

    assert.deepEqual(dataset.configurationWarnings, [
      "Dataset label-ingestion configuration differs from the current evaluation configuration.",
    ]);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("keeps spec-style draft candidate status explicit and unscored as reviewed gold", async () => {
  const directory = await mkdtemp(join(tmpdir(), "eka-dataset-"));
  const path = join(directory, "candidate.jsonl");
  try {
    await writeFile(
      path,
      '{"id":"c","question":"q","answerable":true,"relevantChunkIds":["candidate-id"]}\n',
    );
    await writeFile(
      `${path}.metadata.json`,
      JSON.stringify({
        version: "candidate-v1",
        status: "draft_pending_human_review",
        source: "synthetic",
        sourceDatasetId: "spec-candidate-v1",
        corpusVersion: "source-corpus-version",
        indexId: "source-index-id",
        authorizedScope: "isolated-review-scope",
        split: "tuning-candidate",
        chunkingConfig: "chunker-1",
        embeddingConfig: "embed-1",
        reviewer: "pending",
        reviewDate: "pending",
      }),
    );

    const dataset = await loadDatasetFile(path, {
      chunkingConfig: "chunker-1",
      embeddingConfig: "embed-1",
    });

    assert.equal(dataset.datasetMetadata.status, "draft_pending_human_review");
    assert.equal(dataset.datasetMetadata.sourceDatasetId, "spec-candidate-v1");
    assert.ok(
      dataset.configurationWarnings.some((warning) =>
        warning.includes("not reviewed gold"),
      ),
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("rejects an unknown dataset review status", async () => {
  const directory = await mkdtemp(join(tmpdir(), "eka-dataset-"));
  const path = join(directory, "invalid-status.jsonl");
  try {
    await writeFile(
      path,
      '{"id":"c","question":"q","answerable":true,"relevantChunkIds":[]}\n',
    );
    await writeFile(
      `${path}.metadata.json`,
      JSON.stringify({
        status: "approved-by-runner",
        chunkingConfig: "chunker-1",
        embeddingConfig: "embed-1",
      }),
    );

    await assert.rejects(
      () => loadDatasetFile(path),
      /metadata sidecar is invalid/,
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("warns rather than guessing when a v1 dataset has no label-configuration sidecar", async () => {
  const directory = await mkdtemp(join(tmpdir(), "eka-dataset-"));
  const path = join(directory, "legacy.jsonl");
  try {
    await writeFile(
      path,
      '{"id":"c","question":"q","answerable":true,"relevantChunkIds":[]}\n',
    );

    const dataset = await loadDatasetFile(path);

    assert.equal(dataset.schemaVersion, "v1");
    assert.deepEqual(dataset.configurationWarnings, [
      "Dataset review status is unspecified; reviewed-gold status is not established.",
      "Dataset label-ingestion configuration metadata is missing.",
    ]);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("rejects unknown sidecar review status", async () => {
  const directory = await mkdtemp(join(tmpdir(), "eka-dataset-"));
  const path = join(directory, "candidate.jsonl");
  try {
    await writeFile(
      path,
      '{"id":"c","question":"q","answerable":true,"relevantChunkIds":[]}\n',
    );
    await writeFile(
      `${path}.metadata.json`,
      JSON.stringify({
        status: "approved-by-tool",
        chunkingConfig: "chunker-1",
        embeddingConfig: "embed-1",
      }),
    );

    await assert.rejects(
      () => loadDatasetFile(path),
      /metadata sidecar is invalid/,
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { writeApprovalRecord } from "../lib/approval-record.mjs";

function record(runId, reportSha256) {
  return {
    schemaVersion: "1",
    stageId: "retrieval-component",
    stageName: "Retrieval component",
    decision: "approved",
    approverIdentity: "reviewer-id",
    decidedAt: "2026-11-16T12:00:00Z",
    runId,
    reportReference: `${runId}.json`,
    reportSha256,
    artifactId: "backend-build-id",
    commitId: "commit-id",
    datasetIdentity: "dataset-sha256",
    corpusIndexIdentity: "index-id",
    effectiveConfigurationIdentity: "config-id",
    coverage: { measured: ["recall@5"], unavailable: [], notRun: [] },
    acceptedLimitations: [],
    requiredRemediation: [],
    nextPermittedStage: "generation-component",
  };
}

test("binds an approval record to report bytes and refuses overwrites", async () => {
  const directory = await mkdtemp(join(tmpdir(), "eka-approval-"));
  const reportPath = join(directory, "run-1.json");
  const bytes = Buffer.from('{"runId":"run-1","status":"report-only"}\n');
  try {
    await writeFile(reportPath, bytes);
    const approval = record(
      "run-1",
      createHash("sha256").update(bytes).digest("hex"),
    );
    const outputPath = await writeApprovalRecord(reportPath, approval);
    assert.deepEqual(JSON.parse(await readFile(outputPath, "utf8")), approval);
    await assert.rejects(
      () => writeApprovalRecord(reportPath, approval),
      /EEXIST/,
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("rejects a record with a mismatched report hash", async () => {
  const directory = await mkdtemp(join(tmpdir(), "eka-approval-"));
  const reportPath = join(directory, "run-2.json");
  try {
    await writeFile(reportPath, '{"runId":"run-2"}\n');
    await assert.rejects(
      () => writeApprovalRecord(reportPath, record("run-2", "0".repeat(64))),
      /hash/,
    );
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";

const decisions = new Set([
  "approved",
  "rejected",
  "approved-with-recorded-limitations",
]);
const fields = new Set([
  "schemaVersion",
  "stageId",
  "stageName",
  "decision",
  "approverIdentity",
  "decidedAt",
  "runId",
  "reportReference",
  "reportSha256",
  "artifactId",
  "commitId",
  "datasetIdentity",
  "corpusIndexIdentity",
  "effectiveConfigurationIdentity",
  "coverage",
  "acceptedLimitations",
  "requiredRemediation",
  "nextPermittedStage",
]);

function requiredString(record, key) {
  return typeof record[key] === "string" && record[key].trim().length > 0;
}

export function validateApprovalRecord(record) {
  if (!record || typeof record !== "object" || Array.isArray(record)) {
    throw new Error("Approval record must be an object.");
  }
  if (Object.keys(record).some((key) => !fields.has(key))) {
    throw new Error("Approval record contains an unsupported field.");
  }
  for (const key of [
    "stageId",
    "stageName",
    "approverIdentity",
    "decidedAt",
    "runId",
    "reportReference",
    "reportSha256",
    "artifactId",
    "commitId",
    "datasetIdentity",
    "corpusIndexIdentity",
    "effectiveConfigurationIdentity",
  ]) {
    if (!requiredString(record, key))
      throw new Error(`Approval record is missing ${key}.`);
  }
  if (record.schemaVersion !== "1" || !decisions.has(record.decision)) {
    throw new Error("Approval record has an unsupported version or decision.");
  }
  if (!Number.isFinite(Date.parse(record.decidedAt)))
    throw new Error("Approval timestamp is invalid.");
  if (!/^[a-f0-9]{64}$/.test(record.reportSha256))
    throw new Error("Report hash is invalid.");
  if (
    !record.coverage ||
    typeof record.coverage !== "object" ||
    Array.isArray(record.coverage)
  ) {
    throw new Error("Approval record coverage is invalid.");
  }
  for (const key of ["measured", "unavailable", "notRun"]) {
    if (
      !Array.isArray(record.coverage[key]) ||
      !record.coverage[key].every((item) => typeof item === "string")
    ) {
      throw new Error(`Approval record ${key} coverage is invalid.`);
    }
  }
  for (const key of ["acceptedLimitations", "requiredRemediation"]) {
    if (
      !Array.isArray(record[key]) ||
      !record[key].every((item) => typeof item === "string")
    ) {
      throw new Error(`Approval record ${key} is invalid.`);
    }
  }
  if (
    record.nextPermittedStage !== null &&
    typeof record.nextPermittedStage !== "string"
  ) {
    throw new Error("Approval record next stage is invalid.");
  }
  return record;
}

export async function writeApprovalRecord(reportPath, record) {
  const reportBytes = await readFile(reportPath);
  const reportSha256 = createHash("sha256").update(reportBytes).digest("hex");
  if (record.reportSha256 !== reportSha256)
    throw new Error("Approval record report hash does not match the report.");

  let report;
  try {
    report = JSON.parse(reportBytes.toString("utf8"));
  } catch {
    throw new Error("Approval record report is invalid JSON.");
  }
  if (report.runId !== record.runId)
    throw new Error("Approval record run ID does not match the report.");
  validateApprovalRecord(record);

  const outputPath = join(dirname(reportPath), `${record.runId}.approval.json`);
  await writeFile(outputPath, `${JSON.stringify(record, null, 2)}\n`, {
    flag: "wx",
    mode: 0o600,
  });
  return outputPath;
}

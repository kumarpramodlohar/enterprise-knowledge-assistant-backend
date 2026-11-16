import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";

const v2Fields = [
  "referenceAnswer",
  "category",
  "source",
  "expectedBehavior",
  "tags",
  "difficulty",
];

export function parseDataset(raw) {
  const rows = raw.split(/\r?\n/).filter((line) => line.trim());
  if (rows.length === 0) {
    throw new Error("The evaluation dataset has no rows.");
  }

  const seenIds = new Set();
  const cases = rows.map((line, index) => {
    let item;
    try {
      item = JSON.parse(line);
    } catch {
      throw new Error(`Dataset row ${index + 1} is not valid JSON.`);
    }

    if (
      !item ||
      typeof item.id !== "string" ||
      typeof item.question !== "string" ||
      item.question.trim() === "" ||
      typeof item.answerable !== "boolean" ||
      !Array.isArray(item.relevantChunkIds) ||
      !item.relevantChunkIds.every((id) => typeof id === "string") ||
      new Set(item.relevantChunkIds).size !== item.relevantChunkIds.length
    ) {
      throw new Error(
        `Dataset row ${index + 1} does not match the evaluation schema.`,
      );
    }

    if (seenIds.has(item.id)) {
      throw new Error(`Dataset row ${index + 1} duplicates a case ID.`);
    }
    seenIds.add(item.id);

    if (
      (item.referenceAnswer !== undefined &&
        typeof item.referenceAnswer !== "string") ||
      (item.category !== undefined && typeof item.category !== "string") ||
      (item.source !== undefined && typeof item.source !== "string") ||
      (item.expectedBehavior !== undefined &&
        !["answer", "abstain"].includes(item.expectedBehavior)) ||
      (item.tags !== undefined &&
        (!Array.isArray(item.tags) ||
          !item.tags.every((tag) => typeof tag === "string"))) ||
      (item.difficulty !== undefined &&
        !["string", "number"].includes(typeof item.difficulty)) ||
      (item.humanReview !== undefined &&
        (!item.humanReview ||
          typeof item.humanReview !== "object" ||
          Array.isArray(item.humanReview)))
    ) {
      throw new Error(
        `Dataset row ${index + 1} contains an invalid optional field.`,
      );
    }

    return item;
  });

  return {
    cases,
    datasetSha256: createHash("sha256").update(raw).digest("hex"),
    schemaVersion: cases.some((item) =>
      v2Fields.some((field) => item[field] !== undefined),
    )
      ? "v2"
      : "v1",
  };
}

export async function loadDatasetFile(path, currentConfig = {}) {
  let raw;
  try {
    raw = await readFile(path, "utf8");
  } catch {
    throw new Error("The configured evaluation dataset could not be read.");
  }

  const parsed = parseDataset(raw);
  let sidecar;
  let datasetMetadataSha256 = null;
  try {
    const sidecarRaw = await readFile(`${path}.metadata.json`, "utf8");
    datasetMetadataSha256 = createHash("sha256")
      .update(sidecarRaw)
      .digest("hex");
    sidecar = JSON.parse(sidecarRaw);
    if (!sidecar || typeof sidecar !== "object" || Array.isArray(sidecar))
      throw new Error();
    for (const field of [
      "version",
      "status",
      "source",
      "sourceDatasetId",
      "authorizedScope",
      "corpusVersion",
      "indexId",
      "chunkingConfig",
      "embeddingConfig",
      "reviewer",
      "reviewDate",
      "split",
    ]) {
      if (sidecar[field] !== undefined && typeof sidecar[field] !== "string")
        throw new Error();
    }
    if (
      sidecar.status !== undefined &&
      ![
        "fixture-only",
        "draft_pending_human_review",
        "candidate",
        "reviewed-gold",
      ].includes(sidecar.status)
    ) {
      throw new Error();
    }
    if (
      sidecar.status !== undefined &&
      !["fixture-only", "draft_pending_human_review", "reviewed-gold"].includes(
        sidecar.status,
      )
    ) {
      throw new Error();
    }
  } catch (error) {
    if (error?.code !== "ENOENT")
      throw new Error("The evaluation dataset metadata sidecar is invalid.");
    sidecar = null;
  }

  const configurationWarnings = [];
  if (!sidecar?.status) {
    configurationWarnings.push(
      "Dataset review status is unspecified; reviewed-gold status is not established.",
    );
  } else if (sidecar.status !== "reviewed-gold") {
    configurationWarnings.push(
      `Dataset status is '${sidecar.status}', not reviewed gold.`,
    );
  }
  if (!sidecar?.chunkingConfig || !sidecar?.embeddingConfig) {
    configurationWarnings.push(
      "Dataset label-ingestion configuration metadata is missing.",
    );
  } else if (!currentConfig.chunkingConfig || !currentConfig.embeddingConfig) {
    configurationWarnings.push(
      "Current chunking or embedding configuration is unavailable; label compatibility was not verified.",
    );
  } else if (
    (currentConfig.chunkingConfig &&
      currentConfig.chunkingConfig !== sidecar.chunkingConfig) ||
    (currentConfig.embeddingConfig &&
      currentConfig.embeddingConfig !== sidecar.embeddingConfig)
  ) {
    configurationWarnings.push(
      "Dataset label-ingestion configuration differs from the current evaluation configuration.",
    );
  }

  return {
    ...parsed,
    datasetMetadata: sidecar,
    datasetMetadataSha256,
    configurationWarnings,
  };
}

import { readFile, writeFile } from "node:fs/promises";
import { basename, dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createEvaluationClient } from "./lib/client.mjs";
import { loadDatasetFile } from "./lib/dataset.mjs";
import { computeRetrievalMetrics } from "./lib/metrics/retrieval.mjs";
import { createReport, writeReport } from "./lib/report.mjs";

const moduleDirectory = dirname(fileURLToPath(import.meta.url));

function positiveInteger(name, value, fallback) {
  if (value === undefined || value === "") return fallback;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 1)
    throw new Error(`Invalid ${name}.`);
  return parsed;
}

function nonNegativeInteger(name, value, fallback) {
  if (value === undefined || value === "") return fallback;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0)
    throw new Error(`Invalid ${name}.`);
  return parsed;
}

function optionalNumber(name, value) {
  if (value === undefined || value === "") return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) throw new Error(`Invalid ${name}.`);
  return parsed;
}

function legacyComparison(baseline, candidate) {
  const metricNames = [
    "recallAt5",
    "mrr",
    "citationFieldCompleteness",
    "evidenceClassificationAccuracy",
    "meanEndToEndLatencyMs",
    "meanRetrievalLatencyMs",
    "meanHumanGroundedness",
    "meanHumanUsefulness",
  ];
  return Object.fromEntries(
    metricNames.map((name) => [
      name,
      Number.isFinite(baseline?.[name]) && Number.isFinite(candidate?.[name])
        ? candidate[name] - baseline[name]
        : null,
    ]),
  );
}

async function main() {
  const datasetPath = process.argv[2] ?? process.env.EKA_EVALUATION_DATASET;
  const username = process.env.EKA_LOCAL_USER;
  const password = process.env.EKA_LOCAL_USER_PASSWORD;
  const modelVersion = process.env.EKA_EVAL_MODEL_VERSION;
  const configVersion = process.env.EKA_EVAL_CONFIG_VERSION;
  if (
    !datasetPath ||
    !username ||
    !password ||
    !modelVersion ||
    !configVersion
  ) {
    throw new Error(
      "Set EKA_EVALUATION_DATASET, EKA_LOCAL_USER, EKA_LOCAL_USER_PASSWORD, EKA_EVAL_MODEL_VERSION, and EKA_EVAL_CONFIG_VERSION.",
    );
  }

  const dataset = await loadDatasetFile(datasetPath, {
    chunkingConfig: process.env.EKA_EVAL_CHUNKING_CONFIG,
    embeddingConfig: process.env.EKA_EVAL_EMBEDDING_CONFIG,
  });
  const k = positiveInteger("EKA_EVAL_TOP_K", process.env.EKA_EVAL_TOP_K, 5);
  const timeoutMs = positiveInteger(
    "EKA_EVAL_TIMEOUT_MS",
    process.env.EKA_EVAL_TIMEOUT_MS,
    30000,
  );
  const failureBudget = nonNegativeInteger(
    "EKA_EVAL_FAILURE_BUDGET",
    process.env.EKA_EVAL_FAILURE_BUDGET,
    0,
  );
  const caseLimit = positiveInteger(
    "EKA_EVAL_CASE_LIMIT",
    process.env.EKA_EVAL_CASE_LIMIT,
    dataset.cases.length,
  );
  const similarityThreshold = optionalNumber(
    "EKA_EVAL_SIMILARITY_THRESHOLD",
    process.env.EKA_EVAL_SIMILARITY_THRESHOLD,
  );
  const collectionIds =
    process.env.EKA_EVAL_COLLECTION_IDS?.split(",")
      .map((id) => id.trim())
      .filter(Boolean) ?? [];
  if (
    collectionIds.length > 50 ||
    collectionIds.some(
      (id) =>
        !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
          id,
        ),
    )
  ) {
    throw new Error(
      "EKA_EVAL_COLLECTION_IDS must contain up to 50 collection UUIDs.",
    );
  }
  const baseUrl = process.env.EKA_API_BASE_URL ?? "http://localhost:8080";
  const cragBaseUrl = process.env.EKA_EVAL_CRAG_BASE_URL;
  const singleMode = process.env.EKA_EVAL_MODE ?? "baseline";
  const reportDirectory = process.env.EKA_EVAL_REPORT_DIR
    ? isAbsolute(process.env.EKA_EVAL_REPORT_DIR)
      ? process.env.EKA_EVAL_REPORT_DIR
      : resolve(process.cwd(), process.env.EKA_EVAL_REPORT_DIR)
    : resolve(moduleDirectory, "reports");
  const baselinePath = process.env.EKA_EVAL_BASELINE;
  let baseline = null;
  if (baselinePath) {
    try {
      baseline = JSON.parse(await readFile(baselinePath, "utf8"));
    } catch {
      baseline = { invalid: true };
    }
  }

  const client = createEvaluationClient({
    baseUrl,
    username,
    password,
    timeoutMs,
  });
  const endpoints = cragBaseUrl
    ? [
        { mode: "baseline", url: baseUrl },
        { mode: "crag", url: cragBaseUrl },
      ]
    : [{ mode: singleMode, url: baseUrl }];
  const reportResults = [];
  const reviewRows = [];
  let anyFailed = false;

  for (const endpoint of endpoints) {
    const results = [];
    let failures = 0;
    for (const item of dataset.cases.slice(0, caseLimit)) {
      const result = await client.query(
        item.question,
        collectionIds.length ? { collectionIds } : null,
      );
      results.push({ id: item.id, ...result });
      if (result.error) failures++;
      if (process.env.EKA_EVAL_REVIEW_SHEET && result.response) {
        reviewRows.push({
          mode: endpoint.mode,
          id: item.id,
          question: item.question,
          answerable: item.answerable,
          answer: result.response.answer ?? null,
          insufficientEvidence: result.response.insufficientEvidence === true,
          citations: Array.isArray(result.response.citations)
            ? result.response.citations.map((citation) => ({
                chunkId: citation.chunkId,
                fileName: citation.fileName,
                snippet: citation.snippet,
              }))
            : [],
          humanReview: { groundedness: null, usefulness: null },
        });
      }
      if (failures > failureBudget) break;
    }

    const metrics = computeRetrievalMetrics(dataset.cases, results, {
      k,
      similarityThreshold,
    });
    const runId = `run-${new Date().toISOString().replaceAll(/[:.]/g, "-")}-${endpoint.mode}-${dataset.datasetSha256.slice(0, 8)}`;
    const metadata = {
      runId,
      mode: endpoint.mode,
      target: "retrieval-component",
      dataset: basename(datasetPath),
      datasetSha256: dataset.datasetSha256,
      datasetMetadataSha256: dataset.datasetMetadataSha256,
      datasetVersion:
        process.env.EKA_EVAL_DATASET_VERSION ??
        dataset.datasetMetadata?.version ??
        dataset.schemaVersion,
      datasetLabelConfiguration: dataset.datasetMetadata
        ? {
            chunkingConfig: dataset.datasetMetadata.chunkingConfig ?? null,
            embeddingConfig: dataset.datasetMetadata.embeddingConfig ?? null,
            reviewer: dataset.datasetMetadata.reviewer ?? null,
            reviewDate: dataset.datasetMetadata.reviewDate ?? null,
          }
        : null,
      datasetMetadata: dataset.datasetMetadata
        ? {
            status: dataset.datasetMetadata.status ?? "unspecified",
            source: dataset.datasetMetadata.source ?? null,
            sourceDatasetId: dataset.datasetMetadata.sourceDatasetId ?? null,
            corpusVersion: dataset.datasetMetadata.corpusVersion ?? null,
            indexId: dataset.datasetMetadata.indexId ?? null,
            authorizedScope: dataset.datasetMetadata.authorizedScope ?? null,
            split: dataset.datasetMetadata.split ?? null,
            reviewer: dataset.datasetMetadata.reviewer ?? null,
            reviewDate: dataset.datasetMetadata.reviewDate ?? null,
          }
        : null,
      configurationWarnings: dataset.configurationWarnings,
      caseCount: dataset.cases.length,
      modelVersion,
      configVersion,
      corpusVersion: process.env.EKA_EVAL_CORPUS_VERSION ?? null,
      indexId: process.env.EKA_EVAL_INDEX_ID ?? null,
      embeddingVersion: process.env.EKA_EVAL_EMBEDDING_VERSION ?? null,
      promptVersion: process.env.EKA_EVAL_PROMPT_VERSION ?? null,
      runnerVersion: "1",
      startedAt: runId.slice(4, 28),
      nodeVersion: process.version,
      effectiveConfiguration: {
        topK: k,
        similarityThreshold,
        chunkingConfig: process.env.EKA_EVAL_CHUNKING_CONFIG ?? null,
        embeddingConfig: process.env.EKA_EVAL_EMBEDDING_CONFIG ?? null,
        timeoutMs,
        caseLimit,
        concurrency: 1,
        retryCount: 0,
        failureBudget,
        collectionCount: collectionIds.length,
      },
      failureBudget,
    };
    const report = createReport({ metadata, metrics, baseline });
    const paths = await writeReport(report, reportDirectory);
    reportResults.push({ report, paths });
    anyFailed ||= report.status === "failed";
  }

  if (process.env.EKA_EVAL_REVIEW_SHEET) {
    await writeFile(
      process.env.EKA_EVAL_REVIEW_SHEET,
      `${reviewRows.map((row) => JSON.stringify(row)).join("\n")}\n`,
    );
  }

  const output =
    reportResults.length === 1
      ? { ...reportResults[0].report, reportFiles: reportResults[0].paths }
      : {
          reports: reportResults.map(({ report, paths }) => ({
            ...report,
            reportFiles: paths,
          })),
          comparison: legacyComparison(
            reportResults.find(({ report }) => report.mode === "baseline")
              ?.report,
            reportResults.find(({ report }) => report.mode === "crag")?.report,
          ),
        };
  console.log(JSON.stringify(output, null, 2));
  if (anyFailed) process.exitCode = 1;
}

main().catch(() => {
  console.error("Evaluation failed before a complete report was produced.");
  process.exitCode = 1;
});

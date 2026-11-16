import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";

const metricKeys = [
  "meanRecallAtK",
  "mrr",
  "hitRate",
  "citationFieldCompleteness",
  "evidenceClassificationAccuracy",
];

function mean(values) {
  return values.length
    ? values.reduce((sum, value) => sum + value, 0) / values.length
    : null;
}

function summarizeCategories(rows) {
  const categories = new Map();
  for (const row of rows) {
    const group = categories.get(row.category) ?? [];
    group.push(row);
    categories.set(row.category, group);
  }
  return Object.fromEntries(
    [...categories].map(([category, items]) => {
      const eligible = items.filter(
        (item) => item.eligibleForRecall && item.status === "measured",
      );
      return [
        category,
        {
          caseCount: items.length,
          recallSampleSize: eligible.length,
          meanRecallAtK: mean(eligible.map((item) => item.recallAtK)),
          mrr: mean(eligible.map((item) => item.reciprocalRank)),
          hitRate: mean(eligible.map((item) => item.hitRate)),
        },
      ];
    }),
  );
}

function summarizeFailures(rows) {
  const counts = {};
  for (const row of rows) {
    for (const miss of row.misses) {
      counts[miss.cause] = (counts[miss.cause] ?? 0) + 1;
    }
  }
  return counts;
}

function compareBaseline(metadata, metrics, baseline) {
  if (!baseline)
    return { status: "unavailable", reason: "No baseline report supplied." };
  const baselineMetadata = baseline.metadata ?? baseline;
  if (
    (baselineMetadata.datasetSha256 ?? baseline.datasetSha256) !==
    metadata.datasetSha256
  ) {
    return { status: "incomparable", reason: "Dataset hashes differ." };
  }
  if (
    metadata.datasetMetadataSha256 !== baselineMetadata.datasetMetadataSha256
  ) {
    return {
      status: "incomparable",
      reason: "Dataset provenance sidecar hashes differ or are missing.",
    };
  }
  const comparableFields = [
    "datasetVersion",
    "modelVersion",
    "configVersion",
    "corpusVersion",
    "indexId",
    "embeddingVersion",
    "promptVersion",
  ];
  const missingFields = comparableFields.filter(
    (field) => metadata[field] == null || baselineMetadata[field] == null,
  );
  if (missingFields.length) {
    return {
      status: "incomparable",
      reason: `Run or baseline is missing comparison metadata: ${missingFields.join(", ")}.`,
    };
  }
  const changedFields = comparableFields.filter(
    (field) =>
      metadata[field] != null &&
      baselineMetadata[field] != null &&
      metadata[field] !== baselineMetadata[field],
  );
  if (changedFields.length) {
    return {
      status: "incomparable",
      reason: `Run configuration differs: ${changedFields.join(", ")}.`,
    };
  }
  if (
    JSON.stringify(metadata.effectiveConfiguration ?? null) !==
    JSON.stringify(baselineMetadata.effectiveConfiguration ?? null)
  ) {
    return {
      status: "incomparable",
      reason: "Effective evaluation configurations differ.",
    };
  }
  const baselineMetrics = baseline.metrics ?? baseline;
  return {
    status: "comparable",
    deltas: Object.fromEntries(
      metricKeys.map((key) => {
        const current = metrics[key];
        const previous = baselineMetrics[key];
        return [
          key,
          Number.isFinite(current) && Number.isFinite(previous)
            ? current - previous
            : null,
        ];
      }),
    ),
  };
}

export function createReport({ metadata, metrics, baseline = null }) {
  const measured = metrics.rows.filter(
    (row) => row.status === "measured",
  ).length;
  const unavailable = metrics.rows.filter(
    (row) => row.status === "unavailable",
  ).length;
  const notRun = metrics.rows.filter((row) => row.status === "not-run").length;
  const measuredRows = metrics.rows.filter((row) => row.status === "measured");
  const metricSampleSizes = {
    recallAtK: metrics.sampleSize,
    recallAt5: metrics.rows.filter(
      (row) => row.eligibleForRecall && row.status === "measured",
    ).length,
    mrr: metrics.sampleSize,
    hitRate: metrics.sampleSize,
    citationFieldCompleteness: measuredRows.filter((row) =>
      Number.isFinite(row.citationFieldCompleteness),
    ).length,
    evidenceClassificationAccuracy: measuredRows.filter((row) =>
      Number.isFinite(row.evidenceClassificationCorrect),
    ).length,
    endToEndLatencyMs: measuredRows.filter((row) =>
      Number.isFinite(row.latencyMs),
    ).length,
    retrievalLatencyMs: measuredRows.filter((row) =>
      Number.isFinite(row.retrievalMs),
    ).length,
    humanGroundedness: measuredRows.filter((row) =>
      Number.isFinite(row.groundedness),
    ).length,
    humanUsefulness: measuredRows.filter((row) =>
      Number.isFinite(row.usefulness),
    ).length,
  };
  const worstCases = metrics.rows
    .filter((row) => row.eligibleForRecall && row.status === "measured")
    .toSorted((left, right) => left.recallAtK - right.recallAtK)
    .slice(0, 10)
    .map(({ id, category, method, recallAtK, misses }) => ({
      id,
      category,
      method,
      recallAtK,
      misses,
    }));

  return {
    schemaVersion: "1",
    runId: metadata.runId,
    metadata,
    mode: metadata.mode,
    dataset: metadata.dataset,
    datasetSha256: metadata.datasetSha256,
    datasetMetadataSha256: metadata.datasetMetadataSha256,
    datasetStatus: metadata.datasetMetadata?.status ?? "unspecified",
    datasetVersion: metadata.datasetVersion,
    modelVersion: metadata.modelVersion,
    configVersion: metadata.configVersion,
    cases: metadata.caseCount,
    recallAt5: metrics.recallAt5,
    mrr: metrics.mrr,
    citationFieldCompleteness: metrics.citationFieldCompleteness,
    evidenceClassificationAccuracy: metrics.evidenceClassificationAccuracy,
    meanEndToEndLatencyMs: metrics.meanEndToEndLatencyMs,
    meanRetrievalLatencyMs: metrics.meanRetrievalLatencyMs,
    meanHumanGroundedness: metrics.meanHumanGroundedness,
    meanHumanUsefulness: metrics.meanHumanUsefulness,
    providerCost: null,
    rows: metrics.rows.map((row) => ({
      id: row.id,
      answerable: row.answerable,
      insufficientEvidence: row.insufficientEvidence,
      recallAt5: row.recallAt5,
      reciprocalRank: row.reciprocalRank,
      citationFieldCompleteness: row.citationFieldCompleteness,
      latencyMs: Number.isFinite(row.latencyMs)
        ? Math.round(row.latencyMs)
        : null,
      retrievalMs: row.retrievalMs,
      groundedness: row.groundedness,
      usefulness: row.usefulness,
    })),
    note: "Provider cost is unavailable through the MVP API; report rows omit question and answer text.",
    status: unavailable > metadata.failureBudget ? "failed" : "report-only",
    coverage: { expected: metrics.rows.length, measured, unavailable, notRun },
    metrics: {
      k: metrics.k,
      retrievalMethod: metrics.retrievalMethod,
      sampleSize: metrics.sampleSize,
      metricSampleSizes,
      meanRecallAtK: metrics.meanRecallAtK,
      mrr: metrics.mrr,
      hitRate: metrics.hitRate,
      citationFieldCompleteness: metrics.citationFieldCompleteness,
      evidenceClassificationAccuracy: metrics.evidenceClassificationAccuracy,
      meanEndToEndLatencyMs: metrics.meanEndToEndLatencyMs,
      meanRetrievalLatencyMs: metrics.meanRetrievalLatencyMs,
      rows: metrics.rows,
      categorySlices: summarizeCategories(metrics.rows),
      failureClusters: summarizeFailures(metrics.rows),
      worstCases,
    },
    baselineComparison: compareBaseline(metadata, metrics, baseline),
    limitations: [
      "Reports contain identifiers and metrics only; question, answer, passage, and judge-reason text are omitted.",
      ...(metadata.configurationWarnings ?? []),
      ...(metrics.retrievalMethod === "citation-based"
        ? [
            "Raw retrieval candidates were unavailable; retrieval metrics are citation-based.",
          ]
        : []),
    ],
  };
}

export function renderMarkdown(report) {
  const lines = [
    "# EKA Retrieval Evaluation",
    "",
    `- Run: ${report.runId}`,
    `- Status: ${report.status}`,
    `- Dataset: ${report.metadata.datasetVersion} (${report.metadata.datasetSha256})`,
    `- Retrieval method: ${report.metrics.retrievalMethod}`,
    `- Coverage: ${report.coverage.measured} measured, ${report.coverage.unavailable} unavailable, ${report.coverage.notRun} not run of ${report.coverage.expected}`,
    "",
    "## Metrics",
    "",
    `- Recall@${report.metrics.k}: ${report.metrics.meanRecallAtK ?? "unavailable"} (n=${report.metrics.metricSampleSizes.recallAtK})`,
    `- Recall@5: ${report.metrics.recallAt5 ?? "unavailable"} (n=${report.metrics.metricSampleSizes.recallAt5})`,
    `- MRR: ${report.metrics.mrr ?? "unavailable"} (n=${report.metrics.metricSampleSizes.mrr})`,
    `- Hit rate@${report.metrics.k}: ${report.metrics.hitRate ?? "unavailable"} (n=${report.metrics.metricSampleSizes.hitRate})`,
    `- Citation field completeness: ${report.metrics.citationFieldCompleteness ?? "unavailable"} (n=${report.metrics.metricSampleSizes.citationFieldCompleteness})`,
    `- Evidence classification accuracy: ${report.metrics.evidenceClassificationAccuracy ?? "unavailable"} (n=${report.metrics.metricSampleSizes.evidenceClassificationAccuracy})`,
    `- Mean end-to-end latency: ${report.metrics.meanEndToEndLatencyMs ?? "unavailable"} ms (n=${report.metrics.metricSampleSizes.endToEndLatencyMs})`,
    `- Mean retrieval latency: ${report.metrics.meanRetrievalLatencyMs ?? "unavailable"} ms (n=${report.metrics.metricSampleSizes.retrievalLatencyMs})`,
    "",
    "## Category slices",
    "",
    "| Category | Cases | Recall n | Mean Recall@K | MRR | Hit rate |",
    "|---|---:|---:|---:|---:|---:|",
  ];
  for (const [category, slice] of Object.entries(
    report.metrics.categorySlices,
  )) {
    lines.push(
      `| ${category} | ${slice.caseCount} | ${slice.recallSampleSize} | ${slice.meanRecallAtK ?? "unavailable"} | ${slice.mrr ?? "unavailable"} | ${slice.hitRate ?? "unavailable"} |`,
    );
  }
  lines.push(
    "",
    "## Worst cases",
    "",
    "| Case | Category | Method | Recall@K | Miss causes |",
    "|---|---|---|---:|---|",
  );
  for (const row of report.metrics.worstCases) {
    lines.push(
      `| ${row.id} | ${row.category} | ${row.method} | ${row.recallAtK} | ${row.misses.map((miss) => miss.cause).join(", ") || "none"} |`,
    );
  }
  lines.push("", "## Failure clusters", "");
  for (const [cause, count] of Object.entries(report.metrics.failureClusters)) {
    lines.push(`- ${cause}: ${count}`);
  }
  lines.push(
    "",
    "## Baseline",
    "",
    `- Status: ${report.baselineComparison.status}`,
  );
  if (report.baselineComparison.reason)
    lines.push(`- Reason: ${report.baselineComparison.reason}`);
  for (const [metric, delta] of Object.entries(
    report.baselineComparison.deltas ?? {},
  )) {
    lines.push(`- ${metric} delta: ${delta ?? "unavailable"}`);
  }
  lines.push(
    "",
    "## Limitations",
    "",
    ...report.limitations.map((item) => `- ${item}`),
    "",
  );
  return lines.join("\n");
}

export async function writeReport(report, directory) {
  await mkdir(directory, { recursive: true });
  const stem = `${report.runId}`;
  const jsonPath = join(directory, `${stem}.json`);
  const markdownPath = join(directory, `${stem}.md`);
  await writeFile(jsonPath, `${JSON.stringify(report, null, 2)}\n`, {
    flag: "wx",
  });
  await writeFile(markdownPath, renderMarkdown(report), { flag: "wx" });
  return { jsonPath, markdownPath };
}

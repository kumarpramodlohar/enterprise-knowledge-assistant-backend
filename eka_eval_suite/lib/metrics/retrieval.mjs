function mean(values) {
  return values.length
    ? values.reduce((sum, value) => sum + value, 0) / values.length
    : null;
}

function validCandidates(response) {
  const candidates = response?.retrievalDiagnostics?.candidates;
  return (
    Array.isArray(candidates) &&
    candidates.every(
      (candidate) =>
        candidate &&
        typeof candidate.chunkId === "string" &&
        Number.isInteger(candidate.rank) &&
        Number.isFinite(candidate.similarityScore),
    )
  );
}

function citationIsComplete(citation) {
  return (
    citation &&
    typeof citation.documentId === "string" &&
    typeof citation.chunkId === "string" &&
    typeof citation.fileName === "string" &&
    typeof citation.snippet === "string"
  );
}

function missCause(chunkId, candidates, k, similarityThreshold) {
  const candidate = candidates.find((item) => item.chunkId === chunkId);
  if (!candidate) return "absent";
  if (candidate.rank > k) return "ranked-below-k";
  if (similarityThreshold === null) return "threshold-unavailable";
  if (candidate.similarityScore < similarityThreshold) return "below-threshold";
  return null;
}

export function computeRetrievalMetrics(
  cases,
  results,
  { k = 5, similarityThreshold = null } = {},
) {
  const resultById = new Map(results.map((result) => [result.id, result]));
  const rows = cases.map((item) => {
    const result = resultById.get(item.id);
    const response = result?.response;
    const hasRawCandidates = validCandidates(response);
    const allCandidates = hasRawCandidates
      ? response.retrievalDiagnostics.candidates
      : [];
    const retrievedItems = hasRawCandidates
      ? allCandidates.filter((candidate) => candidate.rank <= k)
      : (Array.isArray(response?.citations) ? response.citations : []).slice(
          0,
          k,
        );
    const topFiveItems = hasRawCandidates
      ? allCandidates.filter((candidate) => candidate.rank <= 5)
      : (Array.isArray(response?.citations) ? response.citations : []).slice(
          0,
          5,
        );
    const retrievedIds = retrievedItems
      .map((candidate) => candidate.chunkId)
      .filter((id) => typeof id === "string");
    const goldIds = item.relevantChunkIds;
    const foundIds = [
      ...new Set(retrievedIds.filter((id) => goldIds.includes(id))),
    ];
    const foundAtFive = [
      ...new Set(
        topFiveItems
          .map((candidate) => candidate.chunkId)
          .filter((id) => typeof id === "string" && goldIds.includes(id)),
      ),
    ];
    const eligible = item.answerable && goldIds.length > 0;
    const firstRelevantIndex = retrievedItems.findIndex((candidate) =>
      goldIds.includes(candidate.chunkId),
    );
    const firstRelevantRank =
      firstRelevantIndex < 0
        ? null
        : hasRawCandidates
          ? retrievedItems[firstRelevantIndex].rank
          : firstRelevantIndex + 1;
    const misses = eligible
      ? goldIds
          .map((chunkId) => ({
            chunkId,
            cause: hasRawCandidates
              ? missCause(chunkId, allCandidates, k, similarityThreshold)
              : foundIds.includes(chunkId)
                ? null
                : "unavailable-citation-based",
          }))
          .filter((miss) => miss.cause !== null)
      : [];
    const citations = Array.isArray(response?.citations)
      ? response.citations
      : [];
    const expectedAbstain =
      item.expectedBehavior === undefined
        ? !item.answerable
        : item.expectedBehavior === "abstain";

    return {
      id: item.id,
      category: item.category ?? "uncategorized",
      status: result?.error ? "unavailable" : response ? "measured" : "not-run",
      method: hasRawCandidates ? "retrieval-based" : "citation-based",
      answerable: item.answerable,
      eligibleForRecall: eligible,
      goldIds,
      retrievedIds,
      foundIds,
      recallAtK: eligible ? foundIds.length / goldIds.length : null,
      recallAt5: eligible ? foundAtFive.length / goldIds.length : null,
      reciprocalRank: eligible
        ? firstRelevantRank === null
          ? 0
          : 1 / firstRelevantRank
        : null,
      hitRate: eligible ? Number(foundIds.length > 0) : null,
      misses,
      diagnosticCandidateCount: hasRawCandidates ? allCandidates.length : null,
      citationFieldCompleteness: citations.length
        ? citations.filter(citationIsComplete).length / citations.length
        : null,
      evidenceClassificationCorrect: response
        ? response.insufficientEvidence === expectedAbstain
        : null,
      insufficientEvidence:
        typeof response?.insufficientEvidence === "boolean"
          ? response.insufficientEvidence
          : null,
      latencyMs: Number.isFinite(result?.latencyMs) ? result.latencyMs : null,
      retrievalMs: Number.isFinite(response?.retrievalMs)
        ? response.retrievalMs
        : null,
      groundedness: Number.isFinite(item.humanReview?.groundedness)
        ? item.humanReview.groundedness
        : null,
      usefulness: Number.isFinite(item.humanReview?.usefulness)
        ? item.humanReview.usefulness
        : null,
      error: result?.error ?? null,
    };
  });

  const eligibleRows = rows.filter(
    (row) => row.eligibleForRecall && row.status === "measured",
  );
  const measuredRows = rows.filter((row) => row.status === "measured");
  const methods = new Set(
    rows.filter((row) => row.status === "measured").map((row) => row.method),
  );
  return {
    k,
    retrievalMethod:
      methods.size === 1
        ? [...methods][0]
        : methods.size
          ? "mixed"
          : "unavailable",
    sampleSize: eligibleRows.length,
    meanRecallAtK: mean(eligibleRows.map((row) => row.recallAtK)),
    recallAt5: mean(eligibleRows.map((row) => row.recallAt5)),
    mrr: mean(eligibleRows.map((row) => row.reciprocalRank)),
    hitRate: mean(eligibleRows.map((row) => row.hitRate)),
    citationFieldCompleteness: mean(
      measuredRows
        .map((row) => row.citationFieldCompleteness)
        .filter(Number.isFinite),
    ),
    evidenceClassificationAccuracy: mean(
      measuredRows
        .map((row) => row.evidenceClassificationCorrect)
        .filter(Number.isFinite),
    ),
    meanEndToEndLatencyMs: mean(
      measuredRows.map((row) => row.latencyMs).filter(Number.isFinite),
    ),
    meanRetrievalLatencyMs: mean(
      measuredRows.map((row) => row.retrievalMs).filter(Number.isFinite),
    ),
    meanHumanGroundedness: mean(
      measuredRows.map((row) => row.groundedness).filter(Number.isFinite),
    ),
    meanHumanUsefulness: mean(
      measuredRows.map((row) => row.usefulness).filter(Number.isFinite),
    ),
    rows,
  };
}

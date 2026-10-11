import { readFile } from 'node:fs/promises'
import { basename } from 'node:path'
import { createHash } from 'node:crypto'

const datasetPath = process.argv[2] ?? process.env.EKA_EVALUATION_DATASET
const baseUrl = process.env.EKA_API_BASE_URL ?? 'http://localhost:8080'
const username = process.env.EKA_LOCAL_USER
const password = process.env.EKA_LOCAL_USER_PASSWORD
const modelVersion = process.env.EKA_EVAL_MODEL_VERSION
const configVersion = process.env.EKA_EVAL_CONFIG_VERSION

if (!datasetPath || !username || !password || !modelVersion || !configVersion) {
  throw new Error(
    'Set EKA_EVALUATION_DATASET, EKA_LOCAL_USER, EKA_LOCAL_USER_PASSWORD, EKA_EVAL_MODEL_VERSION, and EKA_EVAL_CONFIG_VERSION.',
  )
}

const raw = await readFile(datasetPath, 'utf8')
const datasetSha256 = createHash('sha256').update(raw).digest('hex')
const cases = raw.split(/\r?\n/).filter((line) => line.trim()).map((line, index) => {
  const item = JSON.parse(line)
  if (
    typeof item.id !== 'string' ||
    typeof item.question !== 'string' ||
    typeof item.answerable !== 'boolean' ||
    !Array.isArray(item.relevantChunkIds) ||
    !item.relevantChunkIds.every((id) => typeof id === 'string')
  ) {
    throw new Error(`Dataset row ${index + 1} does not match the evaluation schema.`)
  }
  return item
})
if (!cases.length) throw new Error('The evaluation dataset has no labelled questions.')

const authorization = `Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`
const results = []
for (const item of cases) {
  const started = performance.now()
  const response = await fetch(`${baseUrl.replace(/\/$/, '')}/api/v1/chat/query`, {
    method: 'POST',
    headers: { Authorization: authorization, 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: item.question }),
  })
  const latencyMs = performance.now() - started
  if (!response.ok) {
    throw new Error(`Evaluation request ${item.id} returned HTTP ${response.status}.`)
  }
  const result = await response.json()
  const citations = Array.isArray(result.citations) ? result.citations : []
  const expected = new Set(item.relevantChunkIds)
  const ranks = citations.map((citation) => citation.chunkId)
  const matched = ranks.filter((id) => expected.has(id))
  const human = item.humanReview ?? {}
  results.push({
    id: item.id,
    answerable: item.answerable,
    insufficientEvidence: result.insufficientEvidence === true,
    recallAt5: expected.size ? new Set(matched).size / expected.size : null,
    reciprocalRank: expected.size
      ? (ranks.findIndex((id) => expected.has(id)) < 0
        ? 0
        : 1 / (ranks.findIndex((id) => expected.has(id)) + 1))
      : null,
    citationFieldCompleteness: citations.length
      ? citations.filter((citation) =>
          typeof citation.documentId === 'string' &&
          typeof citation.chunkId === 'string' &&
          typeof citation.fileName === 'string' &&
          typeof citation.snippet === 'string',
        ).length / citations.length
      : null,
    latencyMs: Math.round(latencyMs),
    retrievalMs: Number.isFinite(result.retrievalMs) ? result.retrievalMs : null,
    groundedness: human.groundedness ?? null,
    usefulness: human.usefulness ?? null,
  })
}

const average = (key) => {
  const values = results.map((row) => row[key]).filter((value) => value !== null)
  return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null
}
const evidenceAccuracy = results.filter((row) => row.insufficientEvidence === !row.answerable).length / results.length
console.log(JSON.stringify({
  dataset: basename(datasetPath),
  datasetSha256,
  modelVersion,
  configVersion,
  cases: results.length,
  recallAt5: average('recallAt5'),
  mrr: average('reciprocalRank'),
  citationFieldCompleteness: average('citationFieldCompleteness'),
  evidenceClassificationAccuracy: evidenceAccuracy,
  meanEndToEndLatencyMs: average('latencyMs'),
  meanRetrievalLatencyMs: average('retrievalMs'),
  meanHumanGroundedness: average('groundedness'),
  meanHumanUsefulness: average('usefulness'),
  providerCost: null,
  note: 'Questions and answers are not written to the report. Enter human review scores in the versioned dataset; provider cost is unavailable through the MVP API.',
  rows: results,
}, null, 2))

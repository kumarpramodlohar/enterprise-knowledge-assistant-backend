import assert from "node:assert/strict";
import { execFile as execFileCallback } from "node:child_process";
import { createServer } from "node:http";
import { mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { promisify } from "node:util";
import test from "node:test";
import { fileURLToPath } from "node:url";

const execFile = promisify(execFileCallback);
const suiteDirectory = dirname(dirname(fileURLToPath(import.meta.url)));

test("CLI writes redacted raw-retrieval report through its configured API", async () => {
  const directory = await mkdtemp(join(tmpdir(), "eka-eval-runner-"));
  const server = createServer(async (request, response) => {
    let body = "";
    for await (const chunk of request) body += chunk;
    assert.equal(JSON.parse(body).question, "secret question not for reports");
    assert.deepEqual(JSON.parse(body).filters, {
      collectionIds: ["11111111-1111-4111-8111-111111111111"],
    });
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(
      JSON.stringify({
        answer: "answer text not for reports",
        insufficientEvidence: false,
        citations: [
          {
            chunkId: "gold-1",
            documentId: "doc-1",
            fileName: "policy.txt",
            snippet: "private passage",
          },
        ],
        retrievalMs: 4,
        retrievalDiagnostics: {
          candidates: [
            { chunkId: "gold-1", rank: 1, similarityScore: 0.9 },
            { chunkId: "other-1", rank: 2, similarityScore: 0.8 },
          ],
        },
      }),
    );
  });

  try {
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    const address = server.address();
    const datasetPath = join(directory, "dataset.jsonl");
    await writeFile(
      datasetPath,
      `${JSON.stringify({
        id: "case-1",
        question: "secret question not for reports",
        answerable: true,
        relevantChunkIds: ["gold-1"],
      })}\n`,
    );

    const reportDirectory = join(directory, "reports");
    const { stdout } = await execFile(
      process.execPath,
      [join(suiteDirectory, "evaluate.mjs"), datasetPath],
      {
        env: {
          ...process.env,
          EKA_LOCAL_USER: "eval-user",
          EKA_LOCAL_USER_PASSWORD: "eval-password",
          EKA_EVAL_MODEL_VERSION: "mock-model",
          EKA_EVAL_CONFIG_VERSION: "mock-config",
          EKA_API_BASE_URL: `http://127.0.0.1:${address.port}`,
          EKA_EVAL_REPORT_DIR: reportDirectory,
          EKA_EVAL_SIMILARITY_THRESHOLD: "0.7",
          EKA_EVAL_COLLECTION_IDS: "11111111-1111-4111-8111-111111111111",
        },
      },
    );
    const output = JSON.parse(stdout);
    const reportFile = (await readdir(reportDirectory)).find((name) =>
      name.endsWith(".json"),
    );
    const reportText = await readFile(
      join(reportDirectory, reportFile),
      "utf8",
    );

    assert.equal(output.metrics.retrievalMethod, "retrieval-based");
    assert.equal(output.metrics.meanRecallAtK, 1);
    assert.equal(output.metrics.mrr, 1);
    assert.equal(output.metrics.rows[0].id, "case-1");
    assert.doesNotMatch(
      reportText,
      /secret question not for reports|answer text not for reports|private passage/,
    );
  } finally {
    await new Promise((resolve) => server.close(resolve));
    await rm(directory, { recursive: true, force: true });
  }
});

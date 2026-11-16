import assert from "node:assert/strict";
import { createServer } from "node:http";
import test from "node:test";
import { createEvaluationClient } from "../lib/client.mjs";

async function withServer(handler, callback) {
  const server = createServer(handler);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  try {
    await callback(`http://127.0.0.1:${server.address().port}`);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

test("sends an authenticated chat request and returns response timing", async () => {
  await withServer(
    (request, response) => {
      assert.equal(
        request.headers.authorization,
        `Basic ${Buffer.from("eval:secret").toString("base64")}`,
      );
      response.writeHead(200, { "Content-Type": "application/json" });
      response.end('{"insufficientEvidence":true,"citations":[]}');
    },
    async (baseUrl) => {
      const client = createEvaluationClient({
        baseUrl,
        username: "eval",
        password: "secret",
      });
      const result = await client.query("question");
      assert.equal(result.status, 200);
      assert.equal(result.response.insufficientEvidence, true);
      assert.ok(result.latencyMs >= 0);
    },
  );
});

test("returns a safe status code for HTTP errors without reading response bodies", async () => {
  await withServer(
    (_request, response) => {
      response.writeHead(503);
      response.end("confidential provider response");
    },
    async (baseUrl) => {
      const client = createEvaluationClient({
        baseUrl,
        username: "eval",
        password: "secret",
      });
      const result = await client.query("question");
      assert.equal(result.error, "http_503");
      assert.doesNotMatch(
        JSON.stringify(result),
        /confidential|secret|question/,
      );
    },
  );
});

test("bounds API calls with a timeout", async () => {
  await withServer(
    () => {},
    async (baseUrl) => {
      const client = createEvaluationClient({
        baseUrl,
        username: "eval",
        password: "secret",
        timeoutMs: 10,
      });
      const result = await client.query("question");
      assert.equal(result.error, "timeout");
      assert.doesNotMatch(JSON.stringify(result), /secret|question/);
    },
  );
});

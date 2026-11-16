export function createEvaluationClient({
  baseUrl,
  username,
  password,
  timeoutMs = 30000,
}) {
  const authorization = `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`;

  return {
    async query(question, filters = null) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), timeoutMs);
      const started = performance.now();

      try {
        const response = await fetch(
          `${baseUrl.replace(/\/$/, "")}/api/v1/chat/query`,
          {
            method: "POST",
            headers: {
              Authorization: authorization,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({ question, ...(filters ? { filters } : {}) }),
            signal: controller.signal,
          },
        );
        const latencyMs = performance.now() - started;
        if (!response.ok) {
          return {
            error: `http_${response.status}`,
            latencyMs,
            status: response.status,
          };
        }

        try {
          return {
            response: await response.json(),
            latencyMs,
            status: response.status,
          };
        } catch {
          return { error: "invalid_json", latencyMs, status: response.status };
        }
      } catch (error) {
        return {
          error: error?.name === "AbortError" ? "timeout" : "request_failed",
          latencyMs: performance.now() - started,
          status: null,
        };
      } finally {
        clearTimeout(timeout);
      }
    },
  };
}

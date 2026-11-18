#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

suite_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
backend_root="$(cd "$suite_root/.." && pwd)"
cd "$backend_root"

fail() {
  printf 'Local evaluation setup error: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

dotenv_value() {
  local key="$1"
  local value=""
  if [[ -f "$backend_root/.env" ]]; then
    value="$(awk -v key="$key" 'index($0, key "=") == 1 { sub(/^[^=]*=/, ""); value=$0 } END { print value }' "$backend_root/.env")"
  fi
  value="${value%$'\r'}"
  if [[ "$value" == \"*\" ]]; then value="${value:1:${#value}-2}"; fi
  if [[ "$value" == \'*\' ]]; then value="${value:1:${#value}-2}"; fi
  printf '%s' "$value"
}

configured_value() {
  local key="$1"
  local default_value="$2"
  local value="${!key:-}"
  if [[ -z "$value" ]]; then value="$(dotenv_value "$key")"; fi
  if [[ -z "$value" ]]; then value="$default_value"; fi
  printf '%s' "$value"
}

require_environment() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "Export $name before running this script."
}

require_command java
require_command node
require_command docker
require_command curl
require_command awk
require_command sed

node_major="$(node -p 'Number(process.versions.node.split(".")[0])')"
[[ "$node_major" -ge 20 ]] || fail "Node.js 20 or newer is required."
java_output="$(java -version 2>&1)"
java_major="$(printf '%s\n' "$java_output" | grep -Eo '[0-9]+' | head -n 1)"
[[ -n "$java_major" && "$java_major" -ge 21 ]] || fail "JDK 21 or newer is required."
[[ -f "$backend_root/.env" ]] || fail "Create the backend's ignored .env file from .env.example first."
[[ -n "$(dotenv_value POSTGRES_PASSWORD)" ]] || fail "Set POSTGRES_PASSWORD in the backend .env file."

docker compose version >/dev/null 2>&1 || fail "Docker Compose is required."
docker info >/dev/null 2>&1 || fail "Start Docker Desktop before running local retrieval evaluation."

for name in \
  EKA_EVALUATION_DATASET EKA_LOCAL_USER EKA_LOCAL_USER_PASSWORD \
  EKA_EVAL_MODEL_VERSION EKA_EVAL_CONFIG_VERSION EKA_EVAL_CORPUS_VERSION \
  EKA_EVAL_INDEX_ID EKA_EVAL_COLLECTION_IDS EKA_EVAL_CHUNKING_CONFIG \
  EKA_EVAL_EMBEDDING_CONFIG; do
  require_environment "$name"
done
[[ -r "$EKA_EVALUATION_DATASET" ]] || fail "The evaluation dataset is not readable."
[[ -r "${EKA_EVALUATION_DATASET}.metadata.json" ]] || fail "The dataset metadata sidecar is missing."

backend_local_user="$(dotenv_value EKA_LOCAL_USER)"
backend_local_password="$(dotenv_value EKA_LOCAL_USER_PASSWORD)"
[[ -n "$backend_local_user" && -n "$backend_local_password" ]] || fail "Set EKA_LOCAL_USER and EKA_LOCAL_USER_PASSWORD in backend .env."
[[ "$EKA_LOCAL_USER" == "$backend_local_user" && "$EKA_LOCAL_USER_PASSWORD" == "$backend_local_password" ]] || fail "Exported eval credentials must match the backend local credentials."

chat_provider="$(configured_value EKA_CHAT_PROVIDER ollama)"
embedding_provider="$(configured_value EKA_EMBEDDING_PROVIDER ollama)"
for provider in "$chat_provider" "$embedding_provider"; do
  [[ "$provider" == "ollama" || "$provider" == "openai" ]] || fail "Unsupported provider '$provider'; use ollama or openai."
done
if [[ "$chat_provider" == "openai" || "$embedding_provider" == "openai" ]]; then
  [[ -n "$(configured_value OPENAI_API_KEY '')" ]] || fail "Set OPENAI_API_KEY when using the openai provider."
fi
export EKA_CRAG_ENABLED="$(configured_value EKA_CRAG_ENABLED false)"
[[ "$EKA_CRAG_ENABLED" == "true" || "$EKA_CRAG_ENABLED" == "false" ]] || fail "EKA_CRAG_ENABLED must be true or false."
[[ "$(configured_value EKA_WEB_SEARCH_ENABLED false)" != "true" ]] || fail "Disable web search for the retrieval baseline run."
export EKA_WEB_SEARCH_ENABLED=false

export SPRING_PROFILES_ACTIVE=local,evaluation
export EKA_CHAT_PROVIDER="$chat_provider"
export EKA_EMBEDDING_PROVIDER="$embedding_provider"
if [[ "$chat_provider" == "openai" ]]; then default_chat_model="gpt-4o-mini"; else default_chat_model="llama3.2:latest"; fi
if [[ "$embedding_provider" == "openai" ]]; then
  default_embedding_model="text-embedding-3-small"; default_dimensions="1536"; default_vector_table="vector_store_openai"
else
  default_embedding_model="nomic-embed-text"; default_dimensions="768"; default_vector_table="vector_store_ollama"
fi
export EKA_CHAT_MODEL="$(configured_value EKA_CHAT_MODEL "$default_chat_model")"
export EKA_EMBEDDING_MODEL="$(configured_value EKA_EMBEDDING_MODEL "$default_embedding_model")"
export EKA_EMBEDDING_DIMENSIONS="$(configured_value EKA_EMBEDDING_DIMENSIONS "$default_dimensions")"
export EKA_VECTOR_TABLE="$(configured_value EKA_VECTOR_TABLE "$default_vector_table")"
[[ "$EKA_EMBEDDING_DIMENSIONS" =~ ^[0-9]+$ ]] || fail "EKA_EMBEDDING_DIMENSIONS must be a positive integer."

if [[ "$chat_provider" == "ollama" || "$embedding_provider" == "ollama" ]]; then
  export OLLAMA_BASE_URL="$(configured_value OLLAMA_BASE_URL http://localhost:11434)"
  OLLAMA_BASE_URL="$OLLAMA_BASE_URL" node --input-type=module -e '
    const host = new URL(process.env.OLLAMA_BASE_URL).hostname;
    if (!["localhost", "127.0.0.1", "[::1]", "::1"].includes(host)) {
      console.error("OLLAMA_BASE_URL must point to a loopback host for this local-only script.");
      process.exit(1);
    }
  ' || fail "Ollama must be configured on loopback only."
  ollama_tags="$(curl -fsS --max-time 5 "$OLLAMA_BASE_URL/api/tags")" || fail "Ollama is unavailable at OLLAMA_BASE_URL."
  ollama_models=()
  [[ "$chat_provider" == "ollama" ]] && ollama_models+=("$EKA_CHAT_MODEL")
  [[ "$embedding_provider" == "ollama" ]] && ollama_models+=("$EKA_EMBEDDING_MODEL")
  OLLAMA_TAGS_JSON="$ollama_tags" OLLAMA_REQUIRED_MODELS="$(IFS=,; printf '%s' "${ollama_models[*]}")" node --input-type=module -e '
    const models = JSON.parse(process.env.OLLAMA_TAGS_JSON).models ?? [];
    const names = new Set(models.map((model) => model.name));
    for (const name of process.env.OLLAMA_REQUIRED_MODELS.split(",")) {
      if (!names.has(name)) {
        console.error("A configured Ollama model is not present locally.");
        process.exit(1);
      }
    }
  ' || fail "Pull the configured Ollama models first."
fi

backend_threshold="$(configured_value EKA_RETRIEVAL_SIMILARITY_THRESHOLD 0.30)"
eval_threshold="${EKA_EVAL_SIMILARITY_THRESHOLD:-$backend_threshold}"
if ! awk -v backend="$backend_threshold" -v eval="$eval_threshold" 'BEGIN { if (backend < 0 || backend > 1 || eval < 0 || eval > 1 || backend != eval) exit 1 }'; then
  fail "EKA_EVAL_SIMILARITY_THRESHOLD must equal the backend's configured retrieval threshold (0..1)."
fi
export EKA_RETRIEVAL_SIMILARITY_THRESHOLD="$backend_threshold"
export EKA_EVAL_SIMILARITY_THRESHOLD="$eval_threshold"

backend_candidate_count="$(configured_value EKA_RETRIEVAL_DIAGNOSTIC_CANDIDATE_COUNT 10)"
eval_top_k="${EKA_EVAL_TOP_K:-5}"
[[ "$backend_candidate_count" =~ ^[0-9]+$ && "$eval_top_k" =~ ^[0-9]+$ ]] || fail "Candidate count and evaluation K must be positive integers."
(( backend_candidate_count >= eval_top_k && eval_top_k > 0 )) || fail "Diagnostic candidate count must be at least EKA_EVAL_TOP_K."
export EKA_RETRIEVAL_DIAGNOSTIC_CANDIDATE_COUNT="$backend_candidate_count"
export EKA_EVAL_TOP_K="$eval_top_k"
export EKA_EVAL_FAILURE_BUDGET="${EKA_EVAL_FAILURE_BUDGET:-0}"

node --input-type=module <<'NODE'
import { readFile } from "node:fs/promises";

const fail = (message) => {
  console.error(message);
  process.exit(1);
};
const metadata = JSON.parse(await readFile(`${process.env.EKA_EVALUATION_DATASET}.metadata.json`, "utf8"));
const required = ["version", "status", "source", "sourceDatasetId", "corpusVersion", "indexId", "authorizedScope", "chunkingConfig", "embeddingConfig", "reviewer", "reviewDate", "split"];
for (const field of required) {
  if (typeof metadata[field] !== "string" || metadata[field].trim() === "") fail(`Dataset sidecar is missing ${field}.`);
}
if (metadata.status !== "reviewed-gold") fail("Local eval runner requires a dataset sidecar marked reviewed-gold.");
const compare = [
  ["corpusVersion", process.env.EKA_EVAL_CORPUS_VERSION],
  ["indexId", process.env.EKA_EVAL_INDEX_ID],
  ["chunkingConfig", process.env.EKA_EVAL_CHUNKING_CONFIG],
  ["embeddingConfig", process.env.EKA_EVAL_EMBEDDING_CONFIG],
];
for (const [field, value] of compare) {
  if (metadata[field] !== value) fail(`Dataset sidecar ${field} does not match the exported eval configuration.`);
}
const collectionIds = process.env.EKA_EVAL_COLLECTION_IDS.split(",").map((id) => id.trim()).filter(Boolean);
const authorizedScope = metadata.authorizedScope.split(",").map((id) => id.trim()).filter(Boolean);
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
if (!collectionIds.length || collectionIds.length > 50 || collectionIds.some((id) => !uuid.test(id))) fail("EKA_EVAL_COLLECTION_IDS must contain 1 to 50 collection UUIDs.");
if (JSON.stringify([...collectionIds].sort()) !== JSON.stringify([...authorizedScope].sort())) fail("EKA_EVAL_COLLECTION_IDS must match the dataset's authorizedScope sidecar.");
NODE

export EKA_API_BASE_URL="http://127.0.0.1:${SERVER_PORT:-8080}"
export EKA_EVAL_REPORT_DIR="${EKA_EVAL_REPORT_DIR:-$suite_root/reports}"
if [[ "$EKA_CRAG_ENABLED" == "true" ]]; then export EKA_EVAL_MODE=crag; else export EKA_EVAL_MODE=baseline; fi

if curl -fsS --max-time 2 "$EKA_API_BASE_URL/actuator/health" >/dev/null 2>&1; then
  fail "A backend is already responding on SERVER_PORT=${SERVER_PORT:-8080}; stop it or choose another port."
fi

docker compose up -d --wait postgres
./gradlew --no-daemon bootJar

backend_jar=""
for jar_candidate in "$backend_root"/build/libs/*.jar; do
  [[ -f "$jar_candidate" ]] || continue
  [[ "$jar_candidate" == *-plain.jar ]] && continue
  backend_jar="$jar_candidate"
  break
done
[[ -n "$backend_jar" ]] || fail "Gradle did not produce the backend boot JAR."

backend_log="$(mktemp "${TMPDIR:-/tmp}/eka-eval-backend.XXXXXX")"
backend_pid=""
cleanup() {
  local exit_status=$?
  trap - EXIT INT TERM
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
  if [[ $exit_status -eq 0 ]]; then
    rm -f "$backend_log"
  else
    printf 'Backend startup log retained at %s\n' "$backend_log" >&2
  fi
  exit "$exit_status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

SPRING_PROFILES_ACTIVE=local,evaluation SERVER_PORT="${SERVER_PORT:-8080}" java -jar "$backend_jar" >"$backend_log" 2>&1 &
backend_pid=$!
if ! curl --fail --silent --retry 40 --retry-connrefused --retry-delay 1 --retry-max-time 60 --max-time 2 "$EKA_API_BASE_URL/actuator/health" >/dev/null; then
  fail "Backend did not become healthy on the local port."
fi

node --input-type=module <<'NODE'
const auth = `Basic ${Buffer.from(`${process.env.EKA_LOCAL_USER}:${process.env.EKA_LOCAL_USER_PASSWORD}`).toString("base64")}`;
const response = await fetch(`${process.env.EKA_API_BASE_URL}/api/v1/me`, { headers: { Authorization: auth } });
if (!response.ok) {
  console.error(`Local API authentication preflight failed (HTTP ${response.status}).`);
  process.exit(1);
}
const me = await response.json();
const readable = new Set(me.readableCollectionIds ?? []);
const requested = process.env.EKA_EVAL_COLLECTION_IDS.split(",").map((id) => id.trim());
if (requested.some((id) => !readable.has(id))) {
  console.error("The evaluation user lacks read access to one or more dataset collections.");
  process.exit(1);
}
NODE

printf 'Running local retrieval evaluation against %s\n' "$EKA_API_BASE_URL"
node "$suite_root/evaluate.mjs"
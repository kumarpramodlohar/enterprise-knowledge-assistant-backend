#!/usr/bin/env bash
# Starts everything needed to run the app locally: Docker, Postgres (pgvector), env vars, then the app.
# Usage: ./scripts/start-local.sh
set -euo pipefail
cd "$(dirname "$0")/.."

PROPS="src/main/resources/application.properties src/main/resources/application-local.properties"

# Raw value of a property key from application*.properties (last one wins)
prop() { cat $PROPS 2>/dev/null | grep -E "^$1=" | tail -1 | cut -d= -f2- || true; }

# Resolves "${ENV_VAR:default}" -> env value if set, otherwise the default
resolve() {
  local raw="$1"
  if [[ "$raw" =~ ^\$\{([A-Za-z0-9_]+):(.*)\}$ ]]; then
    local var="${BASH_REMATCH[1]}"
    echo "${!var:-${BASH_REMATCH[2]}}"
  else
    echo "$raw"
  fi
}

# Order of precedence: environment variable > application.properties > hard fallback
DB_URL_RAW="$(prop spring.datasource.url)"
DB_PORT_DEFAULT="$(echo "$DB_URL_RAW" | sed -nE 's/.*EKA_DB_PORT:([0-9]+).*/\1/p')"
export EKA_DB_PORT="${EKA_DB_PORT:-${DB_PORT_DEFAULT:-5433}}"
export EKA_DB_USER="${EKA_DB_USER:-$(echo "$(prop spring.datasource.username)" | sed -nE 's/.*EKA_DB_USER:([^}]*)\}.*/\1/p')}"
export EKA_DB_USER="${EKA_DB_USER:-postgres}"
# Provider/model settings can come from the process environment or the local .env file.
dotenv_value() { [[ -f .env ]] && sed -n "s/^$1=//p" .env | tail -1 || true; }
CHAT_PROVIDER="${EKA_CHAT_PROVIDER:-$(dotenv_value EKA_CHAT_PROVIDER)}"
CHAT_PROVIDER="${CHAT_PROVIDER:-ollama}"
EMBEDDING_PROVIDER="${EKA_EMBEDDING_PROVIDER:-$(dotenv_value EKA_EMBEDDING_PROVIDER)}"
EMBEDDING_PROVIDER="${EMBEDDING_PROVIDER:-ollama}"
OLLAMA_URL="${OLLAMA_BASE_URL:-$(dotenv_value OLLAMA_BASE_URL)}"
OLLAMA_URL="${OLLAMA_URL:-$(resolve "$(prop spring.ai.ollama.base-url)")}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
export OLLAMA_BASE_URL="$OLLAMA_URL"
APP_PORT="${SERVER_PORT:-$(prop server.port)}"
APP_PORT="${APP_PORT:-8080}"
LOCAL_USER="${EKA_LOCAL_USER:-$(resolve "$(prop 'eka\.local-auth\.users\[0\]\.username')")}"
LOCAL_USER="${LOCAL_USER:-dev}"

# Secrets have no defaults in application.properties; local-only fallbacks, forced over ~/.zshrc values
export POSTGRES_PASSWORD="${EKA_LOCAL_DB_PASSWORD:-root}"
export EKA_LOCAL_USER_PASSWORD="${EKA_LOCAL_USER_PASSWORD:-devpass123}"
export SERVER_PORT="$APP_PORT"

# Keycloak sign-in with local Ollama; override SPRING_PROFILES_ACTIVE=local for HTTP Basic only
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local,oidc}"
export EKA_OIDC_ISSUER_URI="${EKA_OIDC_ISSUER_URI:-http://127.0.0.1:8180/realms/eka}"

pids=$(lsof -ti tcp:"${APP_PORT}" -sTCP:LISTEN || true)
if [ -n "$pids" ]; then
  echo "Port ${APP_PORT} is in use by PID(s): ${pids}. Killing..."
  kill $pids || true
  sleep 2
  pids=$(lsof -ti tcp:"${APP_PORT}" -sTCP:LISTEN || true)
  [ -z "$pids" ] || kill -9 $pids
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker is not running, starting Docker Desktop..."
  open -a Docker
  until docker info >/dev/null 2>&1; do sleep 2; done
fi

echo "Starting Postgres on port ${EKA_DB_PORT}..."
docker compose up -d --wait postgres

if [[ "$CHAT_PROVIDER" == "ollama" || "$EMBEDDING_PROVIDER" == "ollama" ]]; then
  if ! curl -s -m 2 "$OLLAMA_URL" >/dev/null; then
    echo "WARNING: Ollama is selected but is not reachable at ${OLLAMA_URL}. Start it with: ollama serve"
  fi
fi

if [[ ",${SPRING_PROFILES_ACTIVE}," == *",oidc,"* ]]; then
  if ! curl -s -m 2 -o /dev/null "${EKA_OIDC_ISSUER_URI}/.well-known/openid-configuration"; then
    echo "WARNING: Keycloak issuer is not reachable at ${EKA_OIDC_ISSUER_URI}; token validation will fail."
  fi
  echo "Starting app on port ${APP_PORT} (profiles: ${SPRING_PROFILES_ACTIVE}; chat: ${CHAT_PROVIDER}; embeddings: ${EMBEDDING_PROVIDER}; Keycloak issuer: ${EKA_OIDC_ISSUER_URI}; HTTP Basic is disabled)..."
else
  echo "Starting app on port ${APP_PORT} (profiles: ${SPRING_PROFILES_ACTIVE}; chat: ${CHAT_PROVIDER}; embeddings: ${EMBEDDING_PROVIDER}; login user: ${LOCAL_USER} / ${EKA_LOCAL_USER_PASSWORD})..."
fi
exec ./gradlew bootRun

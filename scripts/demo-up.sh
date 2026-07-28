#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly PROJECT_NAME="equity-demo"

provider="${MARKET_DATA_PROVIDER:-mock}"
provider="${provider,,}"
if [[ "$provider" != "mock" && "$provider" != "finnhub" ]]; then
  echo "MARKET_DATA_PROVIDER must be mock or finnhub." >&2
  exit 2
fi
if [[ "$provider" == "finnhub" && -z "${FINNHUB_API_KEY:-}" ]]; then
  echo "FINNHUB_API_KEY is required for the Finnhub demo." >&2
  exit 2
fi

export MARKET_DATA_PROVIDER="$provider"
export MARKET_DATA_DEMO_CONTROLS_ENABLED="$(
  if [[ "$provider" == "finnhub" ]]; then
    printf '%s' "${MARKET_DATA_DEMO_CONTROLS_ENABLED:-true}"
  else
    printf 'false'
  fi
)"
export BACKEND_PORT="${DEMO_BACKEND_PORT:-8180}"
export FRONTEND_PORT="${DEMO_FRONTEND_PORT:-3100}"
export MYSQL_HOST_PORT="${DEMO_MYSQL_PORT:-3308}"
export REDIS_HOST_PORT="${DEMO_REDIS_PORT:-6380}"
export BACKEND_IMAGE="equity-demo-backend:local"
export FRONTEND_IMAGE="equity-demo-frontend:local"

cd "$PROJECT_ROOT"

wait_for_health() {
  local url=$1
  local deadline=$((SECONDS + 120))
  until curl --fail --silent --show-error "$url" |
      grep -q '"status":"UP"'; do
    if ((SECONDS >= deadline)); then
      echo "Timed out waiting for $url" >&2
      docker compose -p "$PROJECT_NAME" ps -a >&2 || true
      docker compose -p "$PROJECT_NAME" logs --tail=100 backend >&2 || true
      return 1
    fi
    sleep 2
  done
}

echo "Starting isolated demo project: $PROJECT_NAME"
echo "Provider: ${provider^^}"
echo "Volumes: ${PROJECT_NAME}_mysql_data and ${PROJECT_NAME}_redis_data"
docker compose -p "$PROJECT_NAME" config >/dev/null
docker compose -p "$PROJECT_NAME" up -d --build db redis backend frontend

wait_for_health "http://127.0.0.1:${BACKEND_PORT}/api/health"
wait_for_health "http://127.0.0.1:${FRONTEND_PORT}/api/health"

echo "Demo UI: http://localhost:${FRONTEND_PORT}"
echo "Swagger UI: http://localhost:${BACKEND_PORT}/swagger-ui.html"
echo "Run scripts/demo-seed.sh to create idempotent demonstration data."

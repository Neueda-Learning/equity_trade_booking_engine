#!/usr/bin/env bash

set -Eeuo pipefail

if [[ "${CI:-}" != "true" ]]; then
  echo "Refusing to run: CI must be exactly true." >&2
  exit 2
fi

: "${GITHUB_RUN_ID:?GITHUB_RUN_ID is required}"
: "${GITHUB_RUN_ATTEMPT:?GITHUB_RUN_ATTEMPT is required}"

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly SAFE_RUN_ID="$(printf '%s' "$GITHUB_RUN_ID" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')"
readonly SAFE_ATTEMPT="$(printf '%s' "$GITHUB_RUN_ATTEMPT" | tr -cd '0-9')"
readonly PROJECT_NAME="equity-e2e-${SAFE_RUN_ID}-${SAFE_ATTEMPT}"
readonly LOG_DIR="${RUNNER_TEMP:-/tmp}/equity-playwright-e2e"

if [[ -z "$SAFE_RUN_ID" || -z "$SAFE_ATTEMPT" ]]; then
  echo "Run id and attempt must contain safe characters." >&2
  exit 2
fi
if [[ "$PROJECT_NAME" == "equity_trade_booking_engine" ||
      "$PROJECT_NAME" == "equity-demo" ]]; then
  echo "Refusing unsafe Compose project name." >&2
  exit 2
fi

export BACKEND_PORT=0
export FRONTEND_PORT=0
export MYSQL_HOST_PORT=0
export REDIS_HOST_PORT=0
export FINNHUB_STUB_HOST_PORT=0
export MARKET_DATA_PROVIDER=finnhub
export FINNHUB_BASE_URL=http://finnhub-stub:8080
export FINNHUB_API_KEY=ci-finnhub-dummy-token
export FINNHUB_STUB_TOKEN=ci-finnhub-dummy-token
export MARKET_DATA_CONNECT_TIMEOUT_MS=500
export MARKET_DATA_READ_TIMEOUT_MS=500
export MARKET_DATA_MAX_ATTEMPTS=2
export MARKET_DATA_DEMO_CONTROLS_ENABLED=true
export DASHBOARD_SNAPSHOT_SCHEDULING_ENABLED=false
export BACKEND_IMAGE="${PROJECT_NAME}-backend:e2e"
export FRONTEND_IMAGE="${PROJECT_NAME}-frontend:e2e"
export FINNHUB_STUB_IMAGE="${PROJECT_NAME}-finnhub-stub:e2e"

mkdir -p "$LOG_DIR"
cd "$PROJECT_ROOT"

compose() {
  docker compose --profile ci-finnhub -p "$PROJECT_NAME" "$@"
}

cleanup() {
  local exit_code=$?
  trap - EXIT
  if ((exit_code != 0)); then
    compose ps -a | tee "$LOG_DIR/compose-ps.txt" || true
    compose logs --no-color db redis finnhub-stub backend frontend \
      >"$LOG_DIR/compose.log" 2>&1 || true
    cat "$LOG_DIR/compose.log" 2>/dev/null || true
  fi
  compose down -v --remove-orphans || true
  exit "$exit_code"
}
trap cleanup EXIT

published_port() {
  local service=$1
  local container_port=$2
  local mapping
  mapping="$(compose port "$service" "$container_port" | head -n 1)"
  printf '%s\n' "${mapping##*:}"
}

wait_for_health() {
  local url=$1
  local deadline=$((SECONDS + 90))
  until curl --fail --silent --show-error "$url" |
      grep -q '"status":"UP"'; do
    if ((SECONDS >= deadline)); then
      echo "Timed out waiting for $url" >&2
      return 1
    fi
    sleep 2
  done
}

echo "Playwright Compose project: $PROJECT_NAME"
echo "Isolated volumes: ${PROJECT_NAME}_mysql_data and ${PROJECT_NAME}_redis_data"
compose config >/dev/null
compose up -d --build db redis finnhub-stub backend frontend

readonly BACKEND_HOST_PORT="$(published_port backend 8080)"
readonly FRONTEND_HOST_PORT="$(published_port frontend 80)"
wait_for_health "http://127.0.0.1:${BACKEND_HOST_PORT}/api/health"
wait_for_health "http://127.0.0.1:${FRONTEND_HOST_PORT}/api/health"
curl --fail --silent --show-error \
  "http://127.0.0.1:${FRONTEND_HOST_PORT}/" >/dev/null

export PLAYWRIGHT_BASE_URL="http://127.0.0.1:${FRONTEND_HOST_PORT}"
(cd frontend && npm run test:e2e)

echo "Playwright desktop and mobile journeys passed."

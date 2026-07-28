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
readonly PROJECT_NAME="equity-ci-${SAFE_RUN_ID}-${SAFE_ATTEMPT}"
readonly LOG_DIR="${RUNNER_TEMP:-/tmp}/equity-compose-smoke"
readonly JQ_IMAGE="ghcr.io/jqlang/jq:1.7.1"

if [[ -z "$SAFE_RUN_ID" || -z "$SAFE_ATTEMPT" ]]; then
  echo "Run id and attempt must contain safe characters." >&2
  exit 2
fi

if [[ "$PROJECT_NAME" == "equity_trade_booking_engine" ]]; then
  echo "Refusing to use the default development Compose project." >&2
  exit 2
fi

export BACKEND_PORT=0
export FRONTEND_PORT=0
export MYSQL_HOST_PORT=0
export BACKEND_IMAGE="${PROJECT_NAME}-backend:smoke"
export FRONTEND_IMAGE="${PROJECT_NAME}-frontend:smoke"

mkdir -p "$LOG_DIR"
cd "$PROJECT_ROOT"

compose() {
  docker compose -p "$PROJECT_NAME" "$@"
}

collect_diagnostics() {
  compose ps -a | tee "$LOG_DIR/compose-ps.txt" || true
  compose logs --no-color db backend frontend \
    >"$LOG_DIR/compose.log" 2>&1 || true
  if [[ -f "$LOG_DIR/compose.log" ]]; then
    cat "$LOG_DIR/compose.log"
  fi
}

cleanup() {
  local exit_code=$?
  trap - EXIT
  if ((exit_code != 0)); then
    echo "Smoke test failed; collecting diagnostics." >&2
    collect_diagnostics
  fi
  compose down -v --remove-orphans || true
  exit "$exit_code"
}
trap cleanup EXIT

wait_for_health() {
  local url=$1
  local deadline=$((SECONDS + 90))
  until curl --fail --silent --show-error "$url" |
      jq --exit-status '.status == "UP"' >/dev/null; do
    if ((SECONDS >= deadline)); then
      echo "Timed out waiting for $url" >&2
      return 1
    fi
    sleep 2
  done
}

wait_for_db_initialization() {
  local deadline=$((SECONDS + 90))
  local consecutive_pings=0
  local logs

  while ((SECONDS < deadline)); do
    logs="$(compose logs --no-color db 2>&1 || true)"
    if [[ "$logs" == *"MySQL init process done. Ready for start up."* ]] &&
        compose exec -T db sh -c \
          'mysqladmin ping -h 127.0.0.1 -u root -p"$MYSQL_ROOT_PASSWORD" --silent' \
          >/dev/null 2>&1; then
      ((consecutive_pings += 1))
      if ((consecutive_pings >= 3)); then
        return 0
      fi
    else
      consecutive_pings=0
    fi
    sleep 2
  done

  echo "Timed out waiting for final MySQL initialization." >&2
  return 1
}

published_port() {
  local service=$1
  local container_port=$2
  local mapping
  mapping="$(compose port "$service" "$container_port" | head -n 1)"
  printf '%s\n' "${mapping##*:}"
}

assert_response() {
  local expected_status=$1
  local expected_type=$2
  local metadata=$3
  local actual_status=${metadata%%|*}
  local actual_type=${metadata#*|}
  [[ "$actual_status" == "$expected_status" ]]
  [[ "$actual_type" == "$expected_type"* ]]
}

if ! command -v jq >/dev/null; then
  echo "jq is not installed; using $JQ_IMAGE."
  jq() {
    docker run --rm --interactive \
      --volume "$LOG_DIR:$LOG_DIR:ro" \
      "$JQ_IMAGE" "$@"
  }
fi

echo "Compose smoke project: $PROJECT_NAME"
echo "Compose smoke volume: ${PROJECT_NAME}_mysql_data"
compose config >/dev/null
compose up -d --build db
wait_for_db_initialization
compose up -d --build backend frontend

BACKEND_HOST_PORT="$(published_port backend 8080)"
readonly FRONTEND_HOST_PORT="$(published_port frontend 80)"
BACKEND_URL="http://127.0.0.1:${BACKEND_HOST_PORT}"
readonly FRONTEND_URL="http://127.0.0.1:${FRONTEND_HOST_PORT}"

wait_for_health "$BACKEND_URL/api/health"
wait_for_health "$FRONTEND_URL/api/health"
curl --fail --silent --show-error --output /dev/null "$FRONTEND_URL/"

curl --fail --silent --show-error \
  "$BACKEND_URL/api/accounts" \
  >"$LOG_DIR/accounts.json"
readonly ACCOUNT_ID="$(jq --raw-output \
  'map(select(.status == "ACTIVE")) | first | .id // empty' \
  "$LOG_DIR/accounts.json")"
if [[ -z "$ACCOUNT_ID" ]]; then
  echo "No ACTIVE account is available for the smoke trade." >&2
  exit 1
fi
jq --exit-status --arg accountId "$ACCOUNT_ID" \
  'any(.[]; .id == $accountId and .baseCurrency == "USD")' \
  "$LOG_DIR/accounts.json" >/dev/null

readonly BUY_EXECUTED_AT="$(date -u -d '10 seconds ago' +%Y-%m-%dT%H:%M:%SZ)"
readonly SELL_EXECUTED_AT="$(date -u -d '5 seconds ago' +%Y-%m-%dT%H:%M:%SZ)"
jq --null-input \
  --arg accountId "$ACCOUNT_ID" \
  --arg executedAt "$BUY_EXECUTED_AT" \
  '{
    accountId: $accountId,
    ticker: " audit ",
    side: "BUY",
    quantity: 10.000000,
    tradePrice: 42.125000,
    executedAt: $executedAt
  }' >"$LOG_DIR/buy-request.json"

buy_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/buy-response.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$LOG_DIR/buy-request.json" \
  "$BACKEND_URL/api/trades")"
assert_response 201 application/json "$buy_metadata"
jq --exit-status --arg accountId "$ACCOUNT_ID" '
  (.id | test("^[0-9a-f-]{36}$")) and
  .accountId == $accountId and
  .ticker == "AUDIT" and
  .side == "BUY" and
  .status == "BOOKED" and
  .quantity == 10 and
  .tradePrice == 42.125
' "$LOG_DIR/buy-response.json" >/dev/null
readonly BUY_ID="$(jq --raw-output '.id' "$LOG_DIR/buy-response.json")"

jq --null-input \
  --arg accountId "$ACCOUNT_ID" \
  --arg executedAt "$SELL_EXECUTED_AT" \
  '{
    accountId: $accountId,
    ticker: "AUDIT",
    side: "SELL",
    quantity: 4,
    tradePrice: 45,
    executedAt: $executedAt
  }' >"$LOG_DIR/sell-request.json"
sell_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/sell-response.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$LOG_DIR/sell-request.json" \
  "$BACKEND_URL/api/trades")"
assert_response 201 application/json "$sell_metadata"
jq --exit-status --arg accountId "$ACCOUNT_ID" '
  (.id | test("^[0-9a-f-]{36}$")) and
  .accountId == $accountId and
  .ticker == "AUDIT" and
  .side == "SELL" and
  .status == "BOOKED" and
  .quantity == 4
' "$LOG_DIR/sell-response.json" >/dev/null
readonly SELL_ID="$(jq --raw-output '.id' "$LOG_DIR/sell-response.json")"

curl --fail --silent --show-error \
  "$BACKEND_URL/api/positions?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/positions-after-sell.json"
jq --exit-status \
  'any(.[]; .ticker == "AUDIT" and .quantity == 6)' \
  "$LOG_DIR/positions-after-sell.json" >/dev/null

cancel_buy_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/cancel-buy-conflict.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/trades/$BUY_ID/cancel")"
assert_response 409 application/problem+json "$cancel_buy_metadata"
jq --exit-status \
  '.errors.quantity | startswith("insufficient position; available at execution time:")' \
  "$LOG_DIR/cancel-buy-conflict.json" >/dev/null

cancel_sell_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/cancel-sell.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/trades/$SELL_ID/cancel")"
assert_response 200 application/json "$cancel_sell_metadata"
jq --exit-status --arg id "$SELL_ID" \
  '.id == $id and .status == "CANCELLED" and (.cancelledAt | length > 0)' \
  "$LOG_DIR/cancel-sell.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/positions?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/positions-after-cancel-sell.json"
jq --exit-status \
  'any(.[]; .ticker == "AUDIT" and .quantity == 10)' \
  "$LOG_DIR/positions-after-cancel-sell.json" >/dev/null

cancel_buy_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/cancel-buy.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/trades/$BUY_ID/cancel")"
assert_response 200 application/json "$cancel_buy_metadata"
jq --exit-status --arg id "$BUY_ID" \
  '.id == $id and .status == "CANCELLED" and (.cancelledAt | length > 0)' \
  "$LOG_DIR/cancel-buy.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/positions?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/positions-empty.json"
jq --exit-status \
  'all(.[]; .ticker != "AUDIT")' \
  "$LOG_DIR/positions-empty.json" >/dev/null

compose restart backend
BACKEND_HOST_PORT="$(published_port backend 8080)"
BACKEND_URL="http://127.0.0.1:${BACKEND_HOST_PORT}"
wait_for_health "$BACKEND_URL/api/health"
curl --fail --silent --show-error \
  "$BACKEND_URL/api/positions?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/positions-after-restart.json"
jq --exit-status \
  'all(.[]; .ticker != "AUDIT")' \
  "$LOG_DIR/positions-after-restart.json" >/dev/null
curl --fail --silent --show-error \
  "$BACKEND_URL/api/trades?accountId=$ACCOUNT_ID&page=0&size=10" \
  >"$LOG_DIR/trades-after-restart.json"
jq --exit-status --arg buyId "$BUY_ID" --arg sellId "$SELL_ID" '
  any(.items[]; .id == $buyId and .status == "CANCELLED") and
  any(.items[]; .id == $sellId and .status == "CANCELLED")
' "$LOG_DIR/trades-after-restart.json" >/dev/null
readonly VALID_TOTAL="$(jq '.totalElements' "$LOG_DIR/trades-after-restart.json")"

jq --null-input \
  --arg accountId "$ACCOUNT_ID" \
  --arg executedAt "$SELL_EXECUTED_AT" \
  '{
    accountId: $accountId,
    ticker: "AAPL",
    side: "BUY",
    quantity: 1.0000001,
    tradePrice: 10,
    executedAt: $executedAt
  }' >"$LOG_DIR/precision-request.json"
precision_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/precision-response.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$LOG_DIR/precision-request.json" \
  "$BACKEND_URL/api/trades")"
assert_response 400 application/problem+json "$precision_metadata"
jq --exit-status \
  '.errors.quantity == "must have at most 6 decimal places"' \
  "$LOG_DIR/precision-response.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/trades?accountId=$ACCOUNT_ID&page=0&size=10" \
  >"$LOG_DIR/trades-after-invalid.json"
jq --exit-status --argjson expected "$VALID_TOTAL" \
  '.totalElements == $expected' \
  "$LOG_DIR/trades-after-invalid.json" >/dev/null

echo "Compose smoke passed for account $ACCOUNT_ID, BUY $BUY_ID and SELL $SELL_ID."

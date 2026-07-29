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
export REDIS_HOST_PORT=0
export FINNHUB_STUB_HOST_PORT=0
export MARKET_DATA_PROVIDER=finnhub
export FINNHUB_BASE_URL=http://finnhub-stub:8080
export FINNHUB_API_KEY=ci-finnhub-dummy-token
export FINNHUB_STUB_TOKEN=ci-finnhub-dummy-token
export MARKET_DATA_CONNECT_TIMEOUT_MS=500
export MARKET_DATA_READ_TIMEOUT_MS=500
export MARKET_DATA_MAX_ATTEMPTS=2
export MARKET_DATA_DEMO_CONTROLS_ENABLED=false
export DASHBOARD_SNAPSHOT_SCHEDULING_ENABLED=false
export BACKEND_IMAGE="${PROJECT_NAME}-backend:smoke"
export FRONTEND_IMAGE="${PROJECT_NAME}-frontend:smoke"
export FINNHUB_STUB_IMAGE="${PROJECT_NAME}-finnhub-stub:smoke"

mkdir -p "$LOG_DIR"
cd "$PROJECT_ROOT"

compose() {
  docker compose --profile ci-finnhub -p "$PROJECT_NAME" "$@"
}

collect_diagnostics() {
  compose ps -a | tee "$LOG_DIR/compose-ps.txt" || true
  compose logs --no-color db redis finnhub-stub backend frontend \
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

wait_for_redis() {
  local deadline=$((SECONDS + 60))
  until [[ "$(compose exec -T redis redis-cli ping 2>/dev/null)" == "PONG" ]]; do
    if ((SECONDS >= deadline)); then
      echo "Timed out waiting for Redis." >&2
      return 1
    fi
    sleep 2
  done
}

wait_for_stub() {
  local url=$1
  local deadline=$((SECONDS + 60))
  until curl --fail --silent --show-error "$url/health" |
      jq --exit-status '.status == "UP"' >/dev/null; do
    if ((SECONDS >= deadline)); then
      echo "Timed out waiting for Finnhub stub." >&2
      return 1
    fi
    sleep 2
  done
}

set_stub_mode() {
  local mode=$1
  curl --fail --silent --show-error \
    --request POST \
    "$FINNHUB_STUB_URL/__control?mode=$mode" |
    jq --exit-status --arg mode "$mode" '.mode == $mode' >/dev/null
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
echo "Compose smoke volumes: ${PROJECT_NAME}_mysql_data and ${PROJECT_NAME}_redis_data"
compose config >/dev/null
compose up -d --build db redis finnhub-stub
wait_for_db_initialization
wait_for_redis
readonly FINNHUB_STUB_HOST_PORT="$(published_port finnhub-stub 8080)"
readonly FINNHUB_STUB_URL="http://127.0.0.1:${FINNHUB_STUB_HOST_PORT}"
wait_for_stub "$FINNHUB_STUB_URL"
set_stub_mode normal
compose up -d --build backend frontend

BACKEND_HOST_PORT="$(published_port backend 8080)"
readonly FRONTEND_HOST_PORT="$(published_port frontend 80)"
BACKEND_URL="http://127.0.0.1:${BACKEND_HOST_PORT}"
readonly FRONTEND_URL="http://127.0.0.1:${FRONTEND_HOST_PORT}"

wait_for_health "$BACKEND_URL/api/health"
wait_for_health "$FRONTEND_URL/api/health"
curl --fail --silent --show-error --output /dev/null "$FRONTEND_URL/"

jq --null-input \
  --arg name "CI $PROJECT_NAME" \
  '{
    name: $name,
    broker: "CI Broker",
    accountNumberLast4: "4242"
  }' >"$LOG_DIR/account-request.json"
account_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/account-response.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$LOG_DIR/account-request.json" \
  "$BACKEND_URL/api/accounts")"
assert_response 201 application/json "$account_metadata"
readonly ACCOUNT_ID="$(jq --raw-output '.id // empty' \
  "$LOG_DIR/account-response.json")"
jq --exit-status --arg accountId "$ACCOUNT_ID" \
  '.id == $accountId and .status == "ACTIVE" and .baseCurrency == "USD"' \
  "$LOG_DIR/account-response.json" >/dev/null

readonly BUY_EXECUTED_AT="$(date -u -d '10 seconds ago' +%Y-%m-%dT%H:%M:%SZ)"
readonly SELL_EXECUTED_AT="$(date -u -d '5 seconds ago' +%Y-%m-%dT%H:%M:%SZ)"

curl --fail --silent --show-error \
  "$BACKEND_URL/api/market-data/instruments/search?q=apple&limit=10" \
  >"$LOG_DIR/instrument-search.json"
jq --exit-status '
  any(.items[];
    .ticker == "AAPL" and
    .exchange == "US" and
    .type == "Common Stock"
  )
' "$LOG_DIR/instrument-search.json" >/dev/null

jq --null-input \
  --arg accountId "$ACCOUNT_ID" \
  --arg executedAt "$BUY_EXECUTED_AT" \
  '{
    accountId: $accountId,
    ticker: "AAPL",
    side: "BUY",
    quantity: 1,
    tradePrice: 10,
    executedAt: $executedAt
  }' >"$LOG_DIR/amend-original-request.json"
amend_original_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/amend-original-response.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$LOG_DIR/amend-original-request.json" \
  "$BACKEND_URL/api/trades")"
assert_response 201 application/json "$amend_original_metadata"
readonly AMEND_ORIGINAL_ID="$(jq --raw-output '.id' \
  "$LOG_DIR/amend-original-response.json")"

jq --null-input \
  --arg accountId "$ACCOUNT_ID" \
  --arg executedAt "$BUY_EXECUTED_AT" \
  '{
    accountId: $accountId,
    ticker: "AAPL",
    side: "BUY",
    quantity: 2,
    tradePrice: 11,
    executedAt: $executedAt
  }' >"$LOG_DIR/amend-request.json"
amend_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/amend-response.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --data-binary "@$LOG_DIR/amend-request.json" \
  "$BACKEND_URL/api/trades/$AMEND_ORIGINAL_ID/amend")"
assert_response 200 application/json "$amend_metadata"
jq --exit-status --arg originalId "$AMEND_ORIGINAL_ID" '
  .cancelledTrade.id == $originalId and
  .cancelledTrade.status == "CANCELLED" and
  .cancelledTrade.cancellationReason == "AMENDED" and
  .replacementTrade.status == "BOOKED" and
  .replacementTrade.supersedesTradeId == $originalId and
  .replacementTrade.quantity == 2
' "$LOG_DIR/amend-response.json" >/dev/null
readonly AMEND_REPLACEMENT_ID="$(jq --raw-output \
  '.replacementTrade.id' "$LOG_DIR/amend-response.json")"

delete_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/delete-response.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request DELETE \
  "$BACKEND_URL/api/trades/$AMEND_REPLACEMENT_ID")"
assert_response 200 application/json "$delete_metadata"
jq --exit-status --arg replacementId "$AMEND_REPLACEMENT_ID" '
  .id == $replacementId and
  .status == "CANCELLED" and
  .cancellationReason == "DELETED" and
  (.cancelledAt | length > 0)
' "$LOG_DIR/delete-response.json" >/dev/null
curl --fail --silent --show-error \
  "$BACKEND_URL/api/positions?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/positions-after-delete.json"
jq --exit-status \
  'all(.[]; .ticker != "AAPL")' \
  "$LOG_DIR/positions-after-delete.json" >/dev/null

jq --null-input \
  --arg accountId "$ACCOUNT_ID" \
  --arg executedAt "$BUY_EXECUTED_AT" \
  '{
    accountId: $accountId,
    ticker: " audit ",
    side: "BUY",
    quantity: 10.000000,
    tradePrice: 100.000000,
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
  .tradePrice == 100
' "$LOG_DIR/buy-response.json" >/dev/null
readonly BUY_ID="$(jq --raw-output '.id' "$LOG_DIR/buy-response.json")"

first_quote_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/quote-first.json" \
  --write-out '%{http_code}|%{content_type}' \
  "$BACKEND_URL/api/market-data/quotes/AUDIT")"
assert_response 200 application/json "$first_quote_metadata"
jq --exit-status '
  .ticker == "AUDIT" and
  .price > 0 and
  .previousClose > 0 and
  .source == "FINNHUB" and
  .mock == false and
  .cached == false and
  .stale == false
' "$LOG_DIR/quote-first.json" >/dev/null

second_quote_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/quote-second.json" \
  --write-out '%{http_code}|%{content_type}' \
  "$BACKEND_URL/api/market-data/quotes/AUDIT")"
assert_response 200 application/json "$second_quote_metadata"
jq --exit-status \
  '.ticker == "AUDIT" and .cached == true and .stale == false' \
  "$LOG_DIR/quote-second.json" >/dev/null

compose exec -T redis redis-cli --raw GET market:quote:AUDIT \
  >"$LOG_DIR/redis-quote.json"
jq --exit-status '
  .ticker == "AUDIT" and
  .source == "FINNHUB" and
  .mock == false
' "$LOG_DIR/redis-quote.json" >/dev/null

refresh_quote_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/quote-refresh.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/market-data/quotes/AUDIT/refresh")"
assert_response 200 application/json "$refresh_quote_metadata"
jq --exit-status \
  '.ticker == "AUDIT" and .source == "FINNHUB" and
   .mock == false and .cached == false and .stale == false' \
  "$LOG_DIR/quote-refresh.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/market-data/quotes?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/account-quotes.json"
jq --exit-status '
  .items | length == 1 and
  .[0].ticker == "AUDIT" and
  .[0].source == "FINNHUB" and
  .[0].mock == false
' "$LOG_DIR/account-quotes.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/pnl?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/pnl.json"
readonly QUOTE_PRICE="$(jq '.price' "$LOG_DIR/quote-first.json")"
jq --exit-status --argjson price "$QUOTE_PRICE" '
  def close($actual; $expected):
    (($actual - $expected) | fabs) < 0.00001;
  (.items | length) == 1 and
  .items[0].ticker == "AUDIT" and
  .items[0].quantity == 10 and
  .items[0].costBasis == 1000 and
  close(.items[0].marketValue; 10 * $price) and
  close(.items[0].unrealizedPnl; (10 * $price) - 1000) and
  close(
    .items[0].pnlPercent;
    (((10 * $price) - 1000) / 1000) * 100
  ) and
  .totals.complete == true and
  .totals.unpricedPositionCount == 0
' "$LOG_DIR/pnl.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/market-data/provider/status" \
  >"$LOG_DIR/provider-status-success.json"
jq --exit-status '
  .provider == "FINNHUB" and
  .configured == true and
  .demoControlsEnabled == false and
  .demoOutageEnabled == false and
  (.lastSuccessAt | length > 0)
' "$LOG_DIR/provider-status-success.json" >/dev/null

demo_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/demo-disabled.json" \
  --write-out '%{http_code}|%{content_type}' \
  "$BACKEND_URL/api/demo/market-data/outage")"
assert_response 404 application/problem+json "$demo_metadata"

set_stub_mode server_error
outage_quote_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/quote-stale.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/market-data/quotes/AUDIT/refresh")"
assert_response 200 application/json "$outage_quote_metadata"
jq --exit-status '
  .ticker == "AUDIT" and
  .source == "FINNHUB" and
  .mock == false and
  .cached == true and
  .stale == true
' "$LOG_DIR/quote-stale.json" >/dev/null

dashboard_stale_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/dashboard-stale.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/dashboard/refresh?accountId=$ACCOUNT_ID")"
assert_response 200 application/json "$dashboard_stale_metadata"
jq --exit-status '
  .totals.complete == true and
  .totals.stale == true and
  (.positions | any(
    .ticker == "AUDIT" and
    .source == "FINNHUB" and
    .cached == true and
    .stale == true and
    .available == true
  ))
' "$LOG_DIR/dashboard-stale.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/pnl?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/pnl-stale.json"
jq --exit-status '
  .totals.complete == true and
  .totals.stale == true and
  (.items | any(.ticker == "AUDIT" and .available == true and .stale == true))
' "$LOG_DIR/pnl-stale.json" >/dev/null

compose exec -T redis redis-cli FLUSHDB >/dev/null
no_cache_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/quote-no-cache.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/market-data/quotes/AUDIT/refresh")"
assert_response 503 application/problem+json "$no_cache_metadata"
jq --exit-status '
  .status == 503 and
  .errors.provider == "provider server error"
' "$LOG_DIR/quote-no-cache.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/accounts" >"$LOG_DIR/accounts-during-outage.json"
curl --fail --silent --show-error \
  "$BACKEND_URL/api/trades?accountId=$ACCOUNT_ID&page=0&size=20" \
  >"$LOG_DIR/trades-during-outage.json"
curl --fail --silent --show-error \
  "$BACKEND_URL/api/positions?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/positions-during-outage.json"
jq --exit-status --arg accountId "$ACCOUNT_ID" \
  'any(.[]; .id == $accountId)' "$LOG_DIR/accounts-during-outage.json" >/dev/null
jq --exit-status --arg buyId "$BUY_ID" \
  'any(.items[]; .id == $buyId)' "$LOG_DIR/trades-during-outage.json" >/dev/null
jq --exit-status \
  'any(.[]; .ticker == "AUDIT" and .quantity == 10)' \
  "$LOG_DIR/positions-during-outage.json" >/dev/null

set_stub_mode normal
recovered_quote_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/quote-recovered.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/market-data/quotes/AUDIT/refresh")"
assert_response 200 application/json "$recovered_quote_metadata"
jq --exit-status '
  .source == "FINNHUB" and
  .mock == false and
  .cached == false and
  .stale == false
' "$LOG_DIR/quote-recovered.json" >/dev/null

for refresh_number in 1 2; do
  dashboard_metadata="$(curl --silent --show-error \
    --output "$LOG_DIR/dashboard-refresh-${refresh_number}.json" \
    --write-out '%{http_code}|%{content_type}' \
    --request POST \
    "$BACKEND_URL/api/dashboard/refresh")"
  assert_response 200 application/json "$dashboard_metadata"
  jq --exit-status '
    .totals.positionCount >= 1 and
    .totals.pricedPositionCount >= 1 and
    (.positions | any(.ticker == "AUDIT" and .available == true))
  ' "$LOG_DIR/dashboard-refresh-${refresh_number}.json" >/dev/null
done

curl --fail --silent --show-error \
  "$BACKEND_URL/api/dashboard/history?range=ALL" \
  >"$LOG_DIR/history-all.json"
jq --exit-status '
  (.items | length) >= 2 and
  ([.items[].capturedAt] == ([.items[].capturedAt] | sort)) and
  all(.items[]; .scopeType == "ALL" and .accountId == null)
' "$LOG_DIR/history-all.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/dashboard/history?accountId=$ACCOUNT_ID&range=ALL" \
  >"$LOG_DIR/history-account.json"
jq --exit-status --arg accountId "$ACCOUNT_ID" '
  (.items | length) >= 2 and
  ([.items[].capturedAt] == ([.items[].capturedAt] | sort)) and
  all(.items[];
    .scopeType == "ACCOUNT" and .accountId == $accountId
  )
' "$LOG_DIR/history-account.json" >/dev/null

curl --fail --silent --show-error \
  "$BACKEND_URL/api/dashboard?accountId=$ACCOUNT_ID" \
  >"$LOG_DIR/dashboard-account.json"
jq --exit-status --arg accountId "$ACCOUNT_ID" '
  .accountCount == 1 and
  (.positions | all(.accountId == $accountId))
' "$LOG_DIR/dashboard-account.json" >/dev/null

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
restart_quote_metadata="$(curl --silent --show-error \
  --output "$LOG_DIR/quote-after-restart.json" \
  --write-out '%{http_code}|%{content_type}' \
  --request POST \
  "$BACKEND_URL/api/market-data/quotes/AUDIT/refresh")"
assert_response 200 application/json "$restart_quote_metadata"
jq --exit-status '
  .ticker == "AUDIT" and
  .source == "FINNHUB" and
  .mock == false and
  .cached == false and
  .stale == false
' "$LOG_DIR/quote-after-restart.json" >/dev/null
curl --fail --silent --show-error \
  "$BACKEND_URL/api/market-data/quotes/AUDIT" \
  >"$LOG_DIR/quote-cached-after-restart.json"
jq --exit-status \
  '.ticker == "AUDIT" and .cached == true and
   .source == "FINNHUB" and .mock == false and .stale == false' \
  "$LOG_DIR/quote-cached-after-restart.json" >/dev/null
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

curl --fail --silent --show-error \
  "$BACKEND_URL/api/dashboard/history?range=ALL" \
  >"$LOG_DIR/history-after-restart.json"
jq --exit-status '
  (.items | length) >= 2 and
  ([.items[].capturedAt] == ([.items[].capturedAt] | sort))
' "$LOG_DIR/history-after-restart.json" >/dev/null

compose exec -T redis redis-cli FLUSHDB >/dev/null
[[ "$(compose exec -T redis redis-cli DBSIZE)" == "0" ]]
curl --fail --silent --show-error \
  "$BACKEND_URL/api/dashboard/history?accountId=$ACCOUNT_ID&range=ALL" \
  >"$LOG_DIR/history-after-redis-flush.json"
jq --exit-status --arg accountId "$ACCOUNT_ID" '
  (.items | length) >= 2 and
  all(.items[]; .accountId == $accountId)
' "$LOG_DIR/history-after-redis-flush.json" >/dev/null

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

echo "Compose smoke passed for instrument search, audit amendment/deletion, Finnhub stub resilience, Redis stale fallback, P&L, MySQL history, and trade lifecycle."

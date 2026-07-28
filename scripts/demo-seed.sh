#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly PROJECT_NAME="equity-demo"
readonly API_BASE_URL="${DEMO_API_URL:-http://127.0.0.1:${DEMO_BACKEND_PORT:-8180}}"
readonly TEMP_DIR="$(mktemp -d)"
readonly JQ_IMAGE="ghcr.io/jqlang/jq:1.7.1"
trap 'rm -rf -- "$TEMP_DIR"' EXIT

for command in curl docker; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "$command is required." >&2
    exit 2
  fi
done

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is not installed; using $JQ_IMAGE." >&2
  jq() {
    docker run --rm --interactive \
      --volume "$TEMP_DIR:$TEMP_DIR" \
      "$JQ_IMAGE" "$@"
  }
fi

cd "$PROJECT_ROOT"
backend_container="$(docker compose -p "$PROJECT_NAME" ps -q backend)"
if [[ -z "$backend_container" ]]; then
  echo "The equity-demo backend is not running; run scripts/demo-up.sh first." >&2
  exit 2
fi
actual_project="$(docker inspect --format \
  '{{ index .Config.Labels "com.docker.compose.project" }}' \
  "$backend_container")"
if [[ "$actual_project" != "$PROJECT_NAME" ]]; then
  echo "Refusing to seed unexpected Compose project: $actual_project" >&2
  exit 2
fi

api_get() {
  curl --fail --silent --show-error "$API_BASE_URL$1"
}

api_post_file() {
  local path=$1
  local file=$2
  curl --fail --silent --show-error \
    --request POST \
    --header 'Content-Type: application/json' \
    --data-binary "@$file" \
    "$API_BASE_URL$path"
}

ensure_account() {
  local name=$1
  local broker=$2
  local last4=$3
  local account_id
  account_id="$(api_get /api/accounts |
    jq --raw-output --arg name "$name" \
      '.[] | select(.name == $name) | .id' |
    head -n 1)"
  if [[ -z "$account_id" ]]; then
    jq --null-input \
      --arg name "$name" \
      --arg broker "$broker" \
      --arg last4 "$last4" \
      '{name: $name, broker: $broker, accountNumberLast4: $last4}' \
      >"$TEMP_DIR/account.json"
    account_id="$(api_post_file /api/accounts "$TEMP_DIR/account.json" |
      jq --raw-output '.id')"
    echo "Created account: $name" >&2
  else
    echo "Reused account: $name" >&2
  fi
  printf '%s\n' "$account_id"
}

trade_exists() {
  local account_id=$1
  local ticker=$2
  local side=$3
  local quantity=$4
  local price=$5
  api_get "/api/trades?accountId=${account_id}&page=0&size=100" |
    jq --exit-status \
      --arg ticker "$ticker" \
      --arg side "$side" \
      --argjson quantity "$quantity" \
      --argjson price "$price" \
      'any(.items[];
        .ticker == $ticker and
        .side == $side and
        .quantity == $quantity and
        .tradePrice == $price
      )' >/dev/null
}

ensure_trade() {
  local account_id=$1
  local ticker=$2
  local side=$3
  local quantity=$4
  local price=$5
  local executed_at=$6
  if trade_exists "$account_id" "$ticker" "$side" "$quantity" "$price"; then
    echo "Reused trade: $ticker $side $quantity @ $price" >&2
    return
  fi
  jq --null-input \
    --arg accountId "$account_id" \
    --arg ticker "$ticker" \
    --arg side "$side" \
    --argjson quantity "$quantity" \
    --argjson price "$price" \
    --arg executedAt "$executed_at" \
    '{
      accountId: $accountId,
      ticker: $ticker,
      side: $side,
      quantity: $quantity,
      tradePrice: $price,
      executedAt: $executedAt
    }' >"$TEMP_DIR/trade.json"
  api_post_file /api/trades "$TEMP_DIR/trade.json" >/dev/null
  echo "Created trade: $ticker $side $quantity @ $price" >&2
}

ensure_cancelled_trade() {
  local account_id=$1
  local ticker=$2
  local quantity=$3
  local price=$4
  local executed_at=$5
  local trade
  trade="$(api_get "/api/trades?accountId=${account_id}&page=0&size=100" |
    jq --compact-output \
      --arg ticker "$ticker" \
      --argjson quantity "$quantity" \
      --argjson price "$price" \
      '.items[] |
       select(
         .ticker == $ticker and .side == "BUY" and
         .quantity == $quantity and .tradePrice == $price
       )' |
    head -n 1)"
  if [[ -z "$trade" ]]; then
    jq --null-input \
      --arg accountId "$account_id" \
      --arg ticker "$ticker" \
      --argjson quantity "$quantity" \
      --argjson price "$price" \
      --arg executedAt "$executed_at" \
      '{
        accountId: $accountId,
        ticker: $ticker,
        side: "BUY",
        quantity: $quantity,
        tradePrice: $price,
        executedAt: $executedAt
      }' >"$TEMP_DIR/cancelled-trade.json"
    trade="$(api_post_file /api/trades "$TEMP_DIR/cancelled-trade.json")"
  fi
  if [[ "$(jq --raw-output '.status' <<<"$trade")" == "BOOKED" ]]; then
    trade_id="$(jq --raw-output '.id' <<<"$trade")"
    curl --fail --silent --show-error \
      --request POST \
      "$API_BASE_URL/api/trades/${trade_id}/cancel" >/dev/null
    echo "Created CANCELLED example: $ticker" >&2
  else
    echo "Reused CANCELLED example: $ticker" >&2
  fi
}

growth_id="$(ensure_account "Demo Growth" "Demo Brokerage" "1001")"
income_id="$(ensure_account "Demo Income" "Demo Brokerage" "2002")"

base_epoch="$(date -u +%s)"
at() {
  date -u -d "@$((base_epoch - $1))" +%Y-%m-%dT%H:%M:%SZ
}

ensure_trade "$growth_id" AAPL BUY 10 100 "$(at 600)"
ensure_trade "$growth_id" AAPL SELL 2 110 "$(at 540)"
ensure_trade "$growth_id" MSFT BUY 3 500 "$(at 480)"
ensure_trade "$income_id" NVDA BUY 6 100 "$(at 420)"
ensure_trade "$income_id" AMZN BUY 4 250 "$(at 360)"
ensure_trade "$income_id" AMZN SELL 1 260 "$(at 300)"
ensure_cancelled_trade "$income_id" GOOGL 1 180 "$(at 240)"

curl --fail --silent --show-error \
  --request POST \
  "$API_BASE_URL/api/dashboard/refresh" \
  >"$TEMP_DIR/dashboard.json"

jq --exit-status '
  (.positions | any(.available and .unrealizedPnl > 0)) and
  (.positions | any(.available and .unrealizedPnl < 0)) and
  .totals.positionCount >= 4
' "$TEMP_DIR/dashboard.json" >/dev/null
api_get "/api/trades?page=0&size=100" |
  jq --exit-status 'any(.items[]; .status == "CANCELLED")' >/dev/null

echo "Demo seed is ready: two accounts, multiple tickers, BUY/SELL,"
echo "a CANCELLED trade, and positive/negative unrealized P&L."

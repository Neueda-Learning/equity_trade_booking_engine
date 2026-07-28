#!/usr/bin/env bash

set -Eeuo pipefail

: "${FINNHUB_API_KEY:?Set FINNHUB_API_KEY in your local shell. It will not be printed.}"

if [[ "${MARKET_DATA_PROVIDER:-}" != "finnhub" ]]; then
  echo "MARKET_DATA_PROVIDER must be finnhub." >&2
  exit 2
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required for safe response validation." >&2
  exit 2
fi

readonly API_BASE_URL="${BACKEND_API_URL:-http://localhost:8080}"
readonly OUTPUT_DIR="$(mktemp -d)"
trap 'rm -rf -- "$OUTPUT_DIR"' EXIT

curl --fail --silent --show-error \
  --request POST \
  "$API_BASE_URL/api/market-data/quotes/AAPL/refresh" \
  >"$OUTPUT_DIR/first.json"
jq --exit-status '
  .ticker == "AAPL" and
  .source == "FINNHUB" and
  .mock == false and
  .cached == false and
  .stale == false and
  .price > 0 and
  .previousClose > 0
' "$OUTPUT_DIR/first.json" >/dev/null

curl --fail --silent --show-error \
  "$API_BASE_URL/api/market-data/quotes/AAPL" \
  >"$OUTPUT_DIR/second.json"
jq --exit-status '
  .ticker == "AAPL" and
  .source == "FINNHUB" and
  .mock == false and
  .cached == true and
  .stale == false
' "$OUTPUT_DIR/second.json" >/dev/null

echo "Live Finnhub verification passed for AAPL and Redis cache reuse."

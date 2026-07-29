#!/usr/bin/env bash

set -Eeuo pipefail
set +x

readonly TICKER="${1:-AAPL}"
readonly OUTPUT_DIR="$(mktemp -d)"
trap 'rm -rf -- "$OUTPUT_DIR"' EXIT

for command in curl python3; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "$command is required." >&2
    exit 2
  fi
done

if [[ ! "$TICKER" =~ ^[A-Z][A-Z0-9.-]{0,9}$ ]]; then
  echo "Ticker must match [A-Z][A-Z0-9.-]{0,9}." >&2
  exit 2
fi

if [[ -n "${BACKEND_API_URL:-}" ]]; then
  API_BASE_URL="${BACKEND_API_URL%/}"
elif curl --fail --silent --show-error \
    "http://127.0.0.1:8180/api/health" >/dev/null 2>&1; then
  API_BASE_URL="http://127.0.0.1:8180"
elif curl --fail --silent --show-error \
    "http://127.0.0.1:8080/api/health" >/dev/null 2>&1; then
  API_BASE_URL="http://127.0.0.1:8080"
else
  echo "No healthy backend found on port 8180 or 8080." >&2
  echo "Start it with: bash scripts/demo-up.sh" >&2
  exit 2
fi
readonly API_BASE_URL

curl --fail --silent --show-error \
  "$API_BASE_URL/api/market-data/provider/status" \
  >"$OUTPUT_DIR/provider.json"
python3 - "$OUTPUT_DIR/provider.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response:
    status = json.load(response)

if status.get("provider") != "FINNHUB":
    raise SystemExit(
        f"Expected provider FINNHUB, got {status.get('provider')!r}."
    )
if status.get("configured") is not True:
    raise SystemExit("Finnhub provider is not configured.")
if status.get("demoOutageEnabled") is True:
    raise SystemExit(
        "Demo outage is enabled. Restore the provider before testing."
    )

print("Provider: FINNHUB (configured, outage disabled)")
PY

curl --fail --silent --show-error \
  --request POST \
  "$API_BASE_URL/api/market-data/quotes/$TICKER/refresh" \
  >"$OUTPUT_DIR/first.json"
python3 - "$OUTPUT_DIR/first.json" "$TICKER" <<'PY'
import json
import sys
from decimal import Decimal, InvalidOperation

with open(sys.argv[1], encoding="utf-8") as response:
    quote = json.load(response)
ticker = sys.argv[2]

try:
    price = Decimal(str(quote.get("price")))
    previous_close = Decimal(str(quote.get("previousClose")))
except (InvalidOperation, TypeError) as error:
    raise SystemExit("Finnhub returned invalid decimal values.") from error

expected = {
    "ticker": ticker,
    "source": "FINNHUB",
    "mock": False,
    "cached": False,
    "stale": False,
}
for field, value in expected.items():
    if quote.get(field) != value:
        raise SystemExit(
            f"Unexpected first response field {field}: {quote.get(field)!r}"
        )
if price <= 0 or previous_close <= 0:
    raise SystemExit("Finnhub returned a non-positive quote.")
if not quote.get("marketTimestamp") or not quote.get("fetchedAt"):
    raise SystemExit("Finnhub quote timestamps are missing.")

print(
    "Live quote: "
    f"{ticker} price={price} previousClose={previous_close} "
    "source=FINNHUB mock=false cached=false stale=false"
)
PY

curl --fail --silent --show-error \
  "$API_BASE_URL/api/market-data/quotes/$TICKER" \
  >"$OUTPUT_DIR/second.json"
python3 - "$OUTPUT_DIR/second.json" "$TICKER" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response:
    quote = json.load(response)
ticker = sys.argv[2]

expected = {
    "ticker": ticker,
    "source": "FINNHUB",
    "mock": False,
    "cached": True,
    "stale": False,
}
for field, value in expected.items():
    if quote.get(field) != value:
        raise SystemExit(
            f"Unexpected cached response field {field}: {quote.get(field)!r}"
        )

print("Redis reuse: source=FINNHUB mock=false cached=true stale=false")
PY

echo "PASS: live Finnhub API and Redis cache verified through $API_BASE_URL."

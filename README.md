# Equity Trade Booking Engine

Group No. 5 — Give me five

The Equity Trade Booking Engine is a single-user modular monolith for booking
USD equity trades across multiple securities accounts. It supports the complete
demonstration path from Account setup through BUY/SELL Activity, cancellation,
weighted-average Positions, Redis-backed market quotes, unrealized P&L, and
persistent valuation history.

The application never presents generated data as real. Mock quotes are clearly
labelled `MOCK`; Finnhub quotes and Redis fallback values are distinguished as
`LIVE`, `CACHED`, or `STALE`.

## Documentation

- [Stock trading knowledge and project implementation guide (中文)](docs/STOCK_TRADING_KNOWLEDGE.md)
- [Technical documentation](docs/TECHNICAL_DOCUMENTATION.md)

## Features

- Multiple ACTIVE or INACTIVE securities accounts with no physical deletion
- BUY and SELL booking with verified US stock, ADR, or ETF selection
- CSV bulk booking with persistent content-based duplicate detection; a repeat
  import is blocked until the user explicitly confirms creating another full
  set of trades
- Audit-preserved Activity amendments and deletion, with separate execution
  and operation timestamps
- Idempotent cancellation and chronological no-short validation
- Weighted-average Position quantity, average cost, and cost basis
- Deterministic Mock quotes or explicitly configured Finnhub quotes
- Redis quote caching with fresh and retained stale-fallback periods
- Provider status and opt-in Demo outage controls
- Browser-retained searched-ticker list with per-ticker unavailable states
- Backend-calculated unrealized P&L with partial quote availability
- Dashboard KPIs, Position P&L, recent Activity, and persistent one-minute
  valuation history captured from current provider quotes
- MySQL 8.4 Flyway migrations with Hibernate schema validation
- RFC Problem Details errors, OpenAPI documentation, and Swagger UI
- Unit, MySQL/Redis Testcontainers integration, frontend, Compose smoke, and
  Playwright desktop/mobile tests

## Scope and data model

This is a single-user, multi-securities-account application. Every account uses
USD. There is no cash balance or multi-currency ledger.

```text
Account 1 ──── * Trade
  │               │
  │               ├── side: BUY | SELL
  │               ├── status: BOOKED | CANCELLED
  │               ├── executedAt: actual market execution time
  │               ├── createdAt: system operation/recording time
  │               ├── cancellationReason: CANCELLED | DELETED | AMENDED
  │               └── supersedesTradeId: replacement audit link
  │
  ├── computed Position (account + ticker, BOOKED trades only)
  │
  └── * ValuationSnapshot (ACCOUNT scope)

All Accounts ──── computed aggregate Positions
             └── * ValuationSnapshot (ALL scope)

Redis ──── market:quote:{TICKER} only
MySQL ──── Accounts, Trades, and ValuationSnapshots (systems of record)
```

CSV import registrations are stored separately from Trades. Their stable UUID
is derived from the normalized table content, so renaming the file, reordering
rows, changing header case, or using equivalent decimal/time formatting does
not bypass the duplicate warning. The registry records import attempts and
outcomes but does not replace Trade as the source of truth.

Positions replay BOOKED trades by execution time, operation time, and UUID.
SELL and cancellation are rejected if any point in the resulting timeline
would become negative. Weighted average cost is used; FIFO/LIFO and short
selling are not supported.

Valuation history is captured to MySQL once per minute by default. Each capture
force-refreshes every distinct open-position ticker once, calculates aggregate
and per-account P&L, and persists `ALL` and `ACCOUNT` snapshots. `1D`, `7D`, and
`30D` are rolling windows; `ALL` returns all locally collected history. The API
downsamples very large responses to at most 1,440 evenly spaced points while
retaining every stored snapshot in MySQL.

Collection starts when the backend starts and cannot reconstruct time before
the first capture. It uses Finnhub `/quote`, so it does not require premium
historical-candle access. With the common 60-call/minute Finnhub limit, a
one-minute interval supports fewer than 60 distinct open tickers after leaving
capacity for searches, manual refreshes, and other API calls. Increase
`DASHBOARD_SNAPSHOT_INTERVAL` when the portfolio or API plan requires it.

P&L is unrealized P&L only:

```text
marketValue   = quantity × marketPrice
unrealizedPnl = marketValue − costBasis
pnlPercent    = unrealizedPnl ÷ costBasis × 100
```

Core calculations use backend decimal arithmetic. Financial decimal values are
encoded as JSON strings and remain strings in the browser, preventing
IEEE-754 rounding during booking and display. Missing quotes remain `null`; the
UI never substitutes a zero market price.

## Architecture

Each business module follows the same dependency direction:

```text
HTTP API ──> application ──> domain <── infrastructure
                                ^
                                │
                 ports implemented by JPA, Redis,
                 scheduling, Position, or provider adapters
```

Domain code is independent of Spring MVC, JPA, Redis, Jackson, Finnhub DTOs,
and HTTP clients. MySQL is the system of record. Redis is a disposable Market
Quote cache and never stores the only copy of Account, Trade, Position, or
valuation history data.

## Technology

- Java 21 and Spring Boot 3.5.14
- Maven Wrapper, JPA, Flyway, Actuator, and springdoc-openapi
- MySQL 8.4 LTS and Redis 7.4.2
- React 19, TypeScript, Vite, Vitest, Testing Library, and Playwright
- Docker Compose and Nginx
- GitHub Actions with backend, frontend, Compose smoke, E2E, and quality gate

## Start with Mock market data

Docker with the Compose plugin is required.

```bash
./start.sh -d
```

Default endpoints:

- UI: <http://localhost:3000>
- Backend health: <http://localhost:8080/api/health>
- Proxied health: <http://localhost:3000/api/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- MySQL host port: `3307`
- Redis host port: `6379`

The default `MARKET_DATA_PROVIDER=mock` needs no key. Mock prices are
deterministic, positive, and explicitly returned as `source=MOCK` and
`mock=true`. They are generated demonstration values, not real or delayed
external market data.

Stop the development stack without deleting named volumes:

```bash
docker compose down
```

## Start with Finnhub

Set the secret only in your local environment or deployment secret manager:

```bash
export MARKET_DATA_PROVIDER=finnhub
export FINNHUB_API_KEY='<local secret>'
docker compose up -d --build
```

The backend sends the key only in the `X-Finnhub-Token` header. It is never
placed in a URL, response, status payload, or application log. Never commit a
real key to `.env`, `.env.example`, GitHub Actions, source code, screenshots,
or issue logs.

Useful configuration:

| Variable | Default | Purpose |
| --- | --- | --- |
| `MARKET_DATA_PROVIDER` | `mock` | `mock` or `finnhub` |
| `FINNHUB_BASE_URL` | `https://finnhub.io/api/v1` | Finnhub-compatible base URL |
| `FINNHUB_API_KEY` | empty | Required only for Finnhub |
| `MARKET_DATA_CONNECT_TIMEOUT_MS` | `1000` | Connection timeout |
| `MARKET_DATA_READ_TIMEOUT_MS` | `2000` | Response timeout |
| `MARKET_DATA_MAX_ATTEMPTS` | `2` | Total attempts, constrained to 1 or 2 |
| `MARKET_DATA_FRESH_TTL` | `60s` | Redis fresh period |
| `MARKET_DATA_RETENTION_TTL` | `24h` | Retained stale-fallback period |
| `MARKET_DATA_DEMO_CONTROLS_ENABLED` | `false` | Opt-in Demo outage API |

The application default for Demo controls remains `false`. Local Compose
defaults the switch to `true`, but the backend exposes the controls only when
`MARKET_DATA_PROVIDER=finnhub`; set the variable explicitly to `false` to hide
them. Clicking **Simulate outage** or **Restore provider** refreshes the visible
quotes automatically, so the UI immediately demonstrates Redis fallback or
provider recovery.

Timeouts, connection failures, and 5xx responses have at most one retry.
400/404, 401/403, 429, and malformed responses are not retried. Finnhub never
falls back to Mock automatically. If the provider fails and a retained Redis
quote exists, the response is `cached=true` and `stale=true`; otherwise the API
returns safe Problem Details.

The dashboard history defaults to `DASHBOARD_HISTORY_SOURCE=local`, so runtime
market data uses only the Finnhub `/quote` endpoint and never calls
`/stock/candle`. Every successful provider Quote is appended to the MySQL
`market_quote_snapshots` table. The scheduled portfolio capture also stores
the resulting valuation in `valuation_snapshots`, allowing 1D, 7D, 30D, and
ALL to grow from locally collected data.

The Stock Candles implementation remains available but disabled. Set
`DASHBOARD_HISTORY_SOURCE=provider` to display provider-backed daily history,
or `hybrid` to combine daily candles with local Quote snapshots. Finnhub
currently requires a separate Stock Candles entitlement, so a key that can
fetch `/quote` may still receive HTTP 403 for `/stock/candle`.

Quote and valuation snapshots are stored in the MySQL named volume, so
`docker compose down` followed by `docker compose up` retains them.
`docker compose down -v` deletes the volume and its history.

To validate a real local key without printing it:

```bash
bash scripts/verify-finnhub-live.sh
```

The script checks AAPL, `source=FINNHUB`, `mock=false`, and Redis reuse. CI
never calls the real Finnhub service and does not require a Finnhub secret.

## Swagger and API contracts

Run the backend and open:

- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

The document covers:

- Account create/list/get/update/deactivate
- Trade create/list, cancellation, audit-preserved deletion, and amendment
- Provider-backed ticker/company search for supported US securities
- Position aggregate and account-specific queries
- Single, batch, refresh, and provider-status Market Data endpoints
- P&L, Dashboard refresh, and valuation history
- Demo-only outage endpoints
- Pagination defaults and limits
- Problem Details and `MOCK`/`LIVE`/`CACHED`/`STALE` semantics

Validation, not-found, conflict, and provider errors use
`application/problem+json` with a safe field-to-message `errors` object.
Documentation examples contain only placeholder UUIDs and no API key.

## Isolated final demo

The demo scripts always use the fixed Compose project `equity-demo`, ports
separate from development, and the volumes `equity-demo_mysql_data` and
`equity-demo_redis_data`.

```bash
bash scripts/demo-up.sh
bash scripts/demo-seed.sh
```

The default demo uses Mock, so it works without an external key. The idempotent
seed creates two accounts, several tickers, BUY/SELL activity, one CANCELLED
trade, and both positive and negative unrealized P&L examples using REST APIs
only.

Open <http://localhost:3100>. Swagger is available at
<http://localhost:8180/swagger-ui.html>.

To run the isolated demo with Finnhub:

```bash
export MARKET_DATA_PROVIDER=finnhub
export FINNHUB_API_KEY='<local secret>'
bash scripts/demo-up.sh
bash scripts/demo-seed.sh
```

Finnhub demo controls default to enabled only for this explicit demo mode.
Use the Market Data page or:

```bash
curl -X POST http://localhost:8180/api/demo/market-data/outage/enable
curl -X POST http://localhost:8180/api/demo/market-data/outage/disable
```

Remove only the demo project and its isolated volumes:

```bash
bash scripts/demo-down.sh
```

The down script validates Compose project and volume labels before deletion.
It does not touch the default development project or its volumes.

## Testing

Fast backend suite:

```bash
cd backend
./mvnw test
```

MySQL 8.4 and Redis 7.4.2 integration suite:

```bash
./mvnw verify -Pintegration
```

Frontend unit tests, lint, and production build:

```bash
cd frontend
npm ci
npm test -- --run
npm run lint
npm run build
```

Install Chromium once and run the isolated Playwright journey:

```bash
cd frontend
npx playwright install --with-deps chromium
cd ..
CI=true GITHUB_RUN_ID=local GITHUB_RUN_ATTEMPT=1 \
  bash scripts/ci-e2e.sh
```

Run the complete isolated API/Compose smoke:

```bash
CI=true GITHUB_RUN_ID=local GITHUB_RUN_ATTEMPT=1 \
  bash scripts/ci-smoke.sh
```

Both scripts use run-specific Compose projects and remove their MySQL/Redis
volumes. They do not affect default development data.

## CI quality gate

GitHub Actions runs:

1. `backend` — Java 21, Surefire, MySQL/Redis Testcontainers, and Failsafe
2. `frontend` — `npm ci`, Vitest, ESLint, and production build
3. `compose-smoke` — isolated Finnhub-compatible stub, MySQL, Redis, backend,
   frontend, lifecycle, P&L, stale fallback, restart, and cleanup
4. `e2e` — isolated Compose plus Playwright at 1440×900 and 390×844
5. `quality-gate` — succeeds only if all four quality jobs succeed

Failure artifacts contain test reports, Playwright traces/screenshots/videos,
or isolated Compose diagnostics. CI uses a local compatible stub and a dummy
token, never a real Finnhub API key.

## Other environment settings

| Variable | Default |
| --- | --- |
| `DB_USERNAME` | `equity_app` |
| `DB_PASSWORD` | `local_app_password` |
| `MYSQL_ROOT_PASSWORD` | `local_root_password` |
| `FRONTEND_PORT` | `3000` |
| `BACKEND_PORT` | `8080` |
| `MYSQL_HOST_PORT` | `3307` |
| `REDIS_HOST_PORT` | `6379` |
| `DASHBOARD_SNAPSHOT_SCHEDULING_ENABLED` | `true` |
| `DASHBOARD_SNAPSHOT_INTERVAL` | `1m` |
| `DASHBOARD_SNAPSHOT_INITIAL_DELAY` | `0s` |
| `DASHBOARD_HISTORY_SOURCE` | `local` (`local`, `provider`, or `hybrid`) |

Copy `.env.example` to an ignored `.env` only for local overrides. Defaults are
development examples and must not be reused as production credentials.

## Current limitations

- Single user; no authentication or authorization
- USD only; no cash balance or multi-currency accounting
- No short positions
- Unrealized P&L only; no realized P&L
- Weighted average cost only; no FIFO/LIFO
- Activity deletion is a soft, audit-preserved cancellation; there is no
  physical trade deletion
- Activity editing creates an audit-linked replacement instead of mutating the
  original trade
- No WebSocket quotes
- Local valuation history begins at the first successful capture and does not
  backfill earlier periods
- Mock remains the default development provider and produces clearly labelled,
  deterministic quotes

## Future work

- Authentication and user/account ownership
- Cash ledger and multi-currency support
- Realized P&L and tax-lot accounting
- Additional real market-data providers
- WebSocket streaming and additional historical-price providers
- Deployment-specific secret management and production observability

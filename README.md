# Project name:
# Group number: No.5
# Group: Give me five

# Equity Trade Booking Engine

This repository contains a modular monolith for account-scoped trade lifecycle
management. A user can manage multiple securities accounts, book BUY and SELL
trades against an active account, cancel eligible trades, review positions, and
inspect the filtered, paginated Activity ledger. Position tickers can also be
priced by an explicitly labelled deterministic mock provider backed by Redis.
The Dashboard calculates unrealized P&L and persists valuation totals for
historical charts.

## Technology stack

- Backend: Java 21, Spring Boot 3.5.14, Maven, Actuator, JPA, and Flyway
- Frontend: React, TypeScript, Vite, Vitest, React Testing Library, and ESLint
- Database: MySQL 8.4 LTS with InnoDB and `utf8mb4`
- Quote cache: Redis 7.4.2 with JSON values and bounded retention
- Local orchestration: Docker Compose and Nginx
- CI: GitHub Actions with Java 21 and Node 22

## Prerequisites

For the complete stack, install Docker with the Compose plugin. For running
components directly, install Java 21 and Node.js 22.

## Run the complete stack

```bash
./start.sh
```

The script runs from the repository root, checks that Docker Compose is
available, and then executes `docker compose up --build`. Pass `-d` to start in
detached mode:

```bash
./start.sh -d
```

Once all containers are healthy, open:

- Frontend: <http://localhost:3000>
- Backend health: <http://localhost:8080/api/health>
- Health through the frontend proxy: <http://localhost:3000/api/health>

MySQL is exposed on host port `3307` and Redis on `6379` by default. The
application containers use the private Compose addresses `db:3306` and
`redis:6379`.

Stop the services without deleting the named database volume:

```bash
docker compose down
```

## Run tests locally

Backend tests use an in-memory H2 database and do not require Docker:

```bash
cd backend
./mvnw test
```

The integration profile runs the same application against MySQL 8.4 and Redis
7.4.2 Testcontainers and requires Docker:

```bash
./mvnw verify -Pintegration
```

Frontend tests and build:

```bash
cd frontend
npm ci
npm test -- --run
npm run lint
npm run build
```

For frontend development, run `npm run dev`; Vite proxies `/api` to the backend
at `http://localhost:8080`.

## Environment variables

Copy `.env.example` to `.env` only if you want to override the local defaults.
Compose reads:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_USERNAME` | `equity_app` | Application database user |
| `DB_PASSWORD` | `local_app_password` | Application database password |
| `MYSQL_ROOT_PASSWORD` | `local_root_password` | Local MySQL root password |
| `FRONTEND_PORT` | `3000` | Frontend host port |
| `BACKEND_PORT` | `8080` | Backend host port |
| `MYSQL_HOST_PORT` | `3307` | MySQL host port |
| `REDIS_HOST_PORT` | `6379` | Redis host port |
| `MARKET_DATA_PROVIDER` | `mock` | Selected quote provider |
| `MARKET_DATA_FRESH_TTL` | `60s` | Duration before a quote is refreshed |
| `MARKET_DATA_RETENTION_TTL` | `24h` | Redis stale-fallback retention |
| `MARKET_DATA_MOCK_WINDOW` | `60s` | Deterministic mock price window |
| `DASHBOARD_SNAPSHOT_SCHEDULING_ENABLED` | `true` | Enable periodic valuation capture |
| `DASHBOARD_SNAPSHOT_INTERVAL` | `15m` | Delay between scheduled captures |
| `DASHBOARD_SNAPSHOT_INITIAL_DELAY` | `15m` | Delay before the first scheduled capture |

When the backend runs outside Compose, `DB_URL` defaults to
`jdbc:mysql://localhost:3307/equity_booking?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC`.
`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` can all be supplied as environment
variables.

The values in `.env.example` are safe examples for local development only.
They must never be reused in production; use independently generated secrets
and your deployment platform's secret management.

## Accounts and Activity API

The V3 migration creates an active `Primary Account` and assigns all existing
trades to it. Create additional accounts with:

```bash
curl -X POST http://localhost:8080/api/accounts \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Long-term Portfolio",
    "broker": "Example Broker",
    "accountNumberLast4": "1234"
  }'
```

Accounts can be listed at `GET /api/accounts`, retrieved at
`GET /api/accounts/{id}`, edited with `PATCH /api/accounts/{id}`, and
idempotently deactivated with `POST /api/accounts/{id}/deactivate`.

Create a BUY or SELL trade using an active account UUID:

```bash
curl -X POST http://localhost:8080/api/trades \
  -H 'Content-Type: application/json' \
  -d '{
    "accountId": "00000000-0000-0000-0000-000000000001",
    "ticker": "aapl",
    "side": "BUY",
    "quantity": 10.5,
    "tradePrice": 195.25,
    "executedAt": "2026-07-28T06:30:00Z"
  }'
```

List all trades, or filter by account, in descending execution-time order:

```bash
curl 'http://localhost:8080/api/trades?page=0&size=10'
curl 'http://localhost:8080/api/trades?accountId=00000000-0000-0000-0000-000000000001&page=0&size=10'
```

Ticker normalization, account state checks, and all business validation happen
on the backend. `executedAt` is submitted as UTC, while the browser form accepts
an editable local date and time. Pagination is zero-based and supports page
sizes from 1 through 100.

Cancel a trade without deleting it:

```bash
curl -X POST \
  http://localhost:8080/api/trades/00000000-0000-0000-0000-000000000002/cancel
```

List aggregate positions or positions for one account:

```bash
curl http://localhost:8080/api/positions
curl 'http://localhost:8080/api/positions?accountId=00000000-0000-0000-0000-000000000001'
curl http://localhost:8080/api/accounts/00000000-0000-0000-0000-000000000001/positions
```

SELL and cancellation validation replay BOOKED trades in chronological order,
so a trade cannot make an account/ticker position negative at any point.
Positions use weighted average cost and exclude CANCELLED trades.

## Mock Market Data API

The current provider is `mock`. It produces deterministic, positive prices
with at most six decimal places and always returns `source=MOCK` and
`mock=true`. These values are generated locally for development and testing;
they are not live, delayed, or otherwise real market prices.

Fetch or force-refresh one quote:

```bash
curl http://localhost:8080/api/market-data/quotes/AAPL
curl -X POST http://localhost:8080/api/market-data/quotes/AAPL/refresh
```

Fetch quotes for current BOOKED positions across all accounts or one account:

```bash
curl http://localhost:8080/api/market-data/quotes
curl 'http://localhost:8080/api/market-data/quotes?accountId=00000000-0000-0000-0000-000000000001'
```

Redis keys use `market:quote:{TICKER}` and contain JSON quote values only.
Quotes are fresh for 60 seconds by default and retained for 24 hours so a
future provider failure can return a clearly marked stale value. Redis is not
the system of record for accounts, trades, or positions; those remain derived
from MySQL data. A real external market-data provider still needs to be
integrated in a future increment.

## P&L Dashboard and valuation history

The P&L API prices current BOOKED positions using weighted average cost:

```text
marketValue = quantity × marketPrice
unrealizedPnl = marketValue − costBasis
pnlPercent = unrealizedPnl ÷ costBasis × 100
```

All calculations are performed by the backend with decimal arithmetic. Use
`GET /api/pnl` for all accounts or add an `accountId` query parameter. A
missing quote remains `null` and `available=false`; it is never replaced with a
zero price. Totals include priced positions only and expose completeness and
unpriced-position counts.

Dashboard endpoints are:

```text
GET  /api/dashboard
POST /api/dashboard/refresh
GET  /api/dashboard/history?range=1D|7D|30D|ALL
```

Each endpoint accepts an optional `accountId`. Refresh forces quote retrieval,
recalculates the dashboard, and saves valuation totals in MySQL. An all-account
refresh saves both the ALL scope and each account scope. The configurable
scheduler records the same scopes periodically without fabricating prior
history. V5 stores only aggregate valuation snapshots and does not copy trades.
Clearing Redis therefore removes quote cache entries but not valuation history.

All displayed prices and P&L currently rely on explicit MOCK quotes. They must
not be interpreted as live portfolio values.

## Current scope

This increment does not allow short positions. Accounts are deactivated rather
than deleted, and inactive accounts remain queryable but cannot accept new
trades. Trades can be cancelled but are never physically deleted. It
deliberately contains no trade editing, FIFO/LIFO accounting, cash balance,
realized P&L, real external Market Data integration, fabricated historical
backfill, ticker market verification, user management, Swagger, or messaging.
Redis is used only as a disposable mock quote cache. Database schema changes
are managed by Flyway; Hibernate only validates the schema.

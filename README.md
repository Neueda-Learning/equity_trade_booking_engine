# Equity Trade Booking Engine

This repository contains a modular monolith with a runnable Walking Skeleton
and its second agile increment: **BUY Trade Booking**. A user can book a BUY
trade in React, persist it through Spring Boot and MySQL, and review the
paginated booking ledger.

## Technology stack

- Backend: Java 21, Spring Boot 3.5.14, Maven, Actuator, JPA, and Flyway
- Frontend: React, TypeScript, Vite, Vitest, React Testing Library, and ESLint
- Database: MySQL 8.4 LTS with InnoDB and `utf8mb4`
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

MySQL is exposed on host port `3307` by default. The application containers use
the private Compose address `db:3306`.

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

When the backend runs outside Compose, `DB_URL` defaults to
`jdbc:mysql://localhost:3307/equity_booking?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC`.
`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` can all be supplied as environment
variables.

The values in `.env.example` are safe examples for local development only.
They must never be reused in production; use independently generated secrets
and your deployment platform's secret management.

## BUY Trade Booking API

Create a BUY trade:

```bash
curl -X POST http://localhost:8080/api/trades \
  -H 'Content-Type: application/json' \
  -d '{
    "ticker": "aapl",
    "side": "BUY",
    "quantity": 10.5,
    "tradePrice": 195.25,
    "executedAt": "2026-07-28T06:30:00Z"
  }'
```

List trades in descending execution-time order:

```bash
curl 'http://localhost:8080/api/trades?page=0&size=10'
```

Ticker normalization and all business validation happen on the backend.
`executedAt` is submitted as UTC, while the browser form accepts an editable
local date and time. Pagination is zero-based and supports page sizes from 1
through 100.

## Current scope

This increment accepts BUY trades only. SELL requests are rejected and are
never persisted. It deliberately contains no trade cancellation or deletion,
trade-details endpoint, Position, average cost, P&L, Market Data integration or
cache, ticker market verification, user management, idempotency, Swagger,
Redis, messaging, or charts. Database schema changes are managed by Flyway;
Hibernate only validates the schema.

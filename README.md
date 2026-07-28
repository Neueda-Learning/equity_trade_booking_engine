# Equity Trade Booking Engine

This repository currently contains the first agile increment: a runnable,
testable **Walking Skeleton**. It proves the path from browser to React, through
the Spring Boot health API, to MySQL. It is a modular monolith, not a collection
of microservices.

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

## Current scope

This increment deliberately contains no Trade model or CRUD, Position or P&L,
Market Data integration or cache, login, Swagger, Redis, messaging, or
placeholder business tables. Database schema changes are reserved for Flyway;
Hibernate schema mutation is disabled.

The next agile increment will implement **Trade Booking** within the modular
monolith.

# Project

**Budget Invest Bloom** — personal finance app (budget, investment portfolio, forecast).
Modular monolith in one git repository: Gradle modules `shared`, `security`, `auth`, `budget`, `investment` run as one Spring Boot process assembled by `app`; the frontend is `budget-invest-bloom/` (React). The root `docker-compose.yml` runs postgres, app, frontend and backup-service.
Code comments are written in English.

# Stack & Build

| Area | Non-standard |
|---|---|
| Gradle | Kotlin DSL multi-module build run from the repo root; only `app` applies the Spring Boot plugin and builds the jar. Module tests use Testcontainers, so `./gradlew build` needs Docker running. |
| Database | One PostgreSQL database `bib` with schemas `auth` / `budget` / `investment`. Each module has its own EntityManagerFactory, transaction manager and Liquibase changelog, wired in `app/src/main/java/pyc/lopatuxin/config/*PersistenceConfig.java`. |
| Liquibase | A new changeset is a new numbered file in `<module>/src/main/resources/db/changelog/<module>/v1.0.0/`, included from that folder's `changelog-v1.0.0.yml`. Never edit an applied changeset or its file's `logicalFilePath` — the applied history is keyed by id + author + that path, so a changed path re-runs the changeset; add a new file instead. The paths are not uniform: auth and budget files all use `db/changelog/v1.0.0/changelog-v1.0.0.yml`, most investment files use `db/changelog/v1.0.0/<own file name>` (the pre-monolith location, without the module folder) — give a new investment file that form too. |
| Module boundaries | `auth`, `budget` and `investment` depend only on `shared`, never on each other. A cross-module call goes through a port interface in `shared/port` implemented by the owning module (e.g. `InvestmentBudgetSync`). |
| JWT | Only the `security` module validates the token and fills `ApiRequest.user`; `auth`/`budget`/`investment` never parse tokens. |
| Frontend on the stand | The frontend container is a Vite dev server with bind-mounted sources, so source edits are live. After a `package.json` change recreate it with `docker compose up -d --build --force-recreate --renew-anon-volumes frontend` — otherwise the old anonymous `node_modules` volume is reused. |

# Common Mistakes

- Request/response contract: `ApiRequest` / `ResponseApi` / `UserContextDto` from `shared/dto`. Budget and investment use them; do not create new wrappers. `auth` keeps its own `ApiRequest` without `user`, because login and register run without a token.
- Make git commits from the monorepo root, not from module directories.
- Fix failing tests through test-writer, not java-dev — java-dev changes production code to make tests pass.
- Do not add `@Column` for camelCase fields — Hibernate maps them to snake_case itself.

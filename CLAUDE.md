# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Rule

**Always communicate with the user in Russian language** for all messages, explanations, and code-related discussions.

## System Overview

**Budget Invest Bloom** — a personal finance management platform built as a microservices workspace. The workspace root is NOT a git repo; each service is its own git repository.

### Services

| Service | Directory | Java Package / Stack | Port | Context Path |
|---|---|---|---|---|
| Auth | `auth/` | `pyc.lopatuxin.auth` · Spring Boot 4.0.1, Java 24 | 8081 | `/auth` |
| Budget | `budget/` | `pyc.lopatuxin.budget` · Spring Boot 4.0.0, Java 24 | 8083 | — (none currently) |
| Frontend | `budget-invest-bloom/` | React 18 + Vite (SWC) + TypeScript | 8080 (dev) | — |

All services are orchestrated via the root `docker-compose.yml`. Each backend has its own PostgreSQL instance (auth on port 5432, budget on port 5433).

### Docker Port Mapping
- Auth: `8081:8081`
- Budget: `8083:8083`
- Frontend: `8080:8082` (host 8080 → container 8082, Dockerfile overrides Vite port)
- Port `8082` is reserved for a future API Gateway (not yet implemented)

## Пользовательские команды

### *коммит
Когда пользователь пишет `*коммит`, нужно:
1. Выполнить `git diff` и `git status`, чтобы увидеть все изменения
2. Составить подробное сообщение коммита на русском языке, описывающее все изменения
3. Стиль сообщения — прошедшее время первого лица: «Добавил», «Исправил», «Удалил» и т.д.
4. Вывести готовое сообщение коммита пользователю — **без выполнения самого коммита**

## Development Commands

### Full Stack (from workspace root)
```bash
docker-compose up                    # Start all services with hot-reload
docker-compose up auth-postgres      # Start only auth database
docker-compose up budget-postgres    # Start only budget database
docker-compose down -v               # Stop all and remove volumes (clean slate)
```

### Auth Service (from `auth/`)
```bash
./gradlew build                      # Build
./gradlew bootRun                    # Run locally (needs PostgreSQL)
./gradlew bootTestRun                # Run with Testcontainers
./gradlew test                       # Run all tests
```

### Budget Service (from `budget/`)
```bash
./gradlew build                      # Build
./gradlew bootRun                    # Run locally (needs PostgreSQL)
./gradlew test                       # Run all tests
./gradlew test --tests "ClassName"   # Run specific test class
```

### Frontend (from `budget-invest-bloom/`)
```bash
npm install                          # Install dependencies
npm run dev                          # Dev server with HMR
npm run build                        # Production build
npm run lint                         # ESLint
```

## Architecture

### Inter-Service Communication

The system is designed around an **API Gateway pattern** (not yet implemented as a separate service):
- Frontend calls backend APIs via `VITE_API_BASE_URL` (default `http://localhost:8082`)
- The `user` block in API requests is meant to be populated by the API Gateway from JWT tokens
- Auth service handles JWT issuance; other services trust the user context passed in request bodies

### Unified API Contract (Mandatory for All Services)

**Request structure:**
```json
{
  "user": { "userId": "uuid", "email": "string", "role": "USER|ADMIN|MODERATOR", "sessionId": "uuid" },
  "data": { /* endpoint-specific payload */ }
}
```

**Response structure:**
```json
{
  "id": "uuid (request correlation ID)",
  "status": 200,
  "message": "Human-readable message in Russian",
  "timestamp": "ISO-8601",
  "body": { /* endpoint-specific payload */ }
}
```

All response messages must be in Russian. The `id` field is for distributed tracing via `X-Request-ID` header.

### Auth Service Architecture

- **JWT-based auth** with access tokens (15min) and refresh tokens (7 days) using JJWT library
- Refresh tokens stored in HttpOnly cookies (path `/auth`, SameSite `Lax`); access tokens in localStorage on frontend
- **Refresh Token Rotation** — old token invalidated on each refresh
- **Account lockout** after failed login attempts (`failed_login_attempts`, `locked_until` columns)
- **Security versioning** for forced logout from all devices (`security_version` column)
- Security: `SecurityConfig` + `JwtAuthenticationFilter` + `spring-boot-starter-oauth2-resource-server`
- Controllers: `RegisterController`, `LoginController`, `LogoutController`, `RefreshController`
- Endpoints: `/api/auth/register`, `/api/auth/login`, `/api/auth/logout`, `/api/refresh`
- Entities: `User`, `UserRole`, `RefreshToken`, `PasswordResetToken`, `EmailVerificationToken`
- MapStruct for entity-DTO mapping, SpringDoc OpenAPI for API docs
- `SensitiveDataMasker` utility for log masking (auth-only, not shared)
- Has custom `.claude/agents/` for `java-code-writer` and `liquibase-migration-manager` subagents

> **Note:** Auth `docs/` describe planned features (Redis caching, Kafka events, OAuth2, 2FA, SMS/Email verification) that are **not yet implemented** in code. Treat docs as roadmap, not current state.

### Budget Service Architecture

- Manages categories, expenses, incomes, and capital records
- Entities: `Category`, `Expense`, `Income`, `CapitalRecord`
- Enums: `IncomeSource`, `MetricType`, `UserRole`
- Currently in early development (entities defined, no controllers/services yet)

> **Warning:** `budget/docs/api/budget-api-specification.md` contains code examples using `@Data` and database indexes — these **violate project conventions**. Follow the Java Code Conventions section below, not the doc examples.

### Frontend Architecture

- Built with Vite + React 18 + TypeScript + shadcn/ui + Tailwind CSS
- Originally scaffolded with [Lovable](https://lovable.dev)
- State: React Context (`AuthContext`) for auth, `@tanstack/react-query` for server state
- Forms: `react-hook-form` + `zod` for validation (`@hookform/resolvers`)
- Charts: `recharts` library for data visualization
- Routing: `react-router-dom` v6 — pages in `src/pages/`, layout via `Navigation` component
- Error tracking: Sentry (`@sentry/react`) with ErrorBoundary, replay, browser tracing
- API client: `src/lib/api.ts` — handles JWT attachment, automatic token refresh on 401, and redirect to `/login` on session expiry
- UI components: `src/components/ui/` (shadcn/ui library), custom components at `src/components/`
- Path alias: `@/` maps to `src/`
- TypeScript is non-strict (`strictNullChecks: false`, `noImplicitAny: false`)

## Database Migrations (Liquibase)

Both backend services use Liquibase with PostgreSQL. Hibernate DDL mode must always be `validate`.

**Rules:**
- DDL migrations in YML format, DML migrations in SQL format (in a `sql/` subfolder)
- Never modify already-applied migrations; create new ones instead
- Semantic versioning for migration folders: `v1.0.0/`, `v1.0.1/`, etc.
- All columns must include `remarks` attribute
- No database indexes at entity level or in migrations (project-wide policy)

**Structure:**
```
src/main/resources/db/changelog/
├── db.changelog-master.yml
└── v1.0.0/
    ├── changelog-v1.0.0.yml
    ├── 001-create-xxx-table.yml
    └── sql/
        └── 002-insert-initial-data.sql
```

## Java Code Conventions (Backend Services)

### Entity Classes
- Lombok: use `@Builder`, `@Getter`, `@Setter` — **never `@Data`**
- ID: always `@GeneratedValue(strategy = GenerationType.UUID)` with `java.util.UUID`
- **No `@Column` annotations** unless field name differs from DB column name
- No custom methods in entities (Lombok-generated only)
- Every field must have multi-line JavaDoc (`/** */`) — single-line comments (`//`) are forbidden

### DTO Classes
- Lombok: `@Builder`, `@Getter`, `@Setter`, `@AllArgsConstructor`, `@NoArgsConstructor`
- Swagger: `@Schema(description = "...")` on class and every field
- `@JsonInclude(JsonInclude.Include.NON_NULL)` on class level
- Jakarta Bean Validation annotations where appropriate

### General
- No `System.out.println` — use SLF4J logging with `@Slf4j`
- Parameterized logging: `log.info("User {}", userId)` not string concatenation
- Always check `docs/` folder before writing code to ensure alignment with specifications
- Tests use Testcontainers with real PostgreSQL — Docker must be running

## Environment Variables

### Frontend (`budget-invest-bloom/.env`)
- `VITE_API_BASE_URL` — Backend API base URL (default: `http://localhost:8082`, intended for API Gateway)
- `VITE_SENTRY_DSN` — Sentry DSN for error tracking
- `VITE_SENTRY_TRACES_SAMPLE_RATE` — Sentry trace sampling rate

### Backend (via docker-compose or application-dev.yml)
- `SPRING_DATASOURCE_URL` — PostgreSQL connection (auth: `localhost:5432/auth_dev`, budget: `localhost:5433/budget_dev`)
- Auth JWT settings configured in `application-dev.yml` under `jwt.*` properties

## Key Documentation Locations

- `auth/docs/` — Auth service specs (endpoints, DB schema, API format). **Caution:** docs describe planned features (Redis, Kafka, OAuth2) not yet in code
- `budget/docs/setup/` — Budget service setup guides (Docker, Liquibase, logging)
- `budget/docs/api/` — Budget API specification. **Caution:** contains code examples that violate project conventions
- Both services share `standartRequestAndResponse.md` defining the unified API contract

## Sub-agents (Claude Code Agents)

**ЗАПРЕТ:** Никогда не пиши Java backend код и тесты самостоятельно. Это строго запрещено.
**ЗАПРЕТ:** Никогда не читай файлы проекта и не анализируй структуру кода до делегирования — сразу делегируй агенту.
**ЗАПРЕТ:** Никогда не отвечай на архитектурные вопросы и не составляй планы реализации без предварительного архитектурного решения от `backend-architect`.

Для любой backend задачи — немедленно делегируй соответствующему субагенту:

| Задача | Агент |
|---|---|
| Архитектурный вопрос: как устроить сервис, как сервисы общаются, добавить новый сервис, изменить инфраструктуру, не нравится текущая архитектура | `backend-architect` |
| Спланировать реализацию endpoint на основе фронтенда | `backend-planner` |
| Написать Java backend код (контроллеры, сервисы, DTO, репозитории, конфиги, фильтры, Gradle-модули, Docker-файлы, YAML-конфиги) | `backend-implementor` |
| Написать, добавить или отрефакторить Java тесты (unit или интеграционные) | `backend-test-writer` |

**Если задача содержит любое из этих слов или фраз** применительно к backend коду, файлам сервисов или задачам разработки:
«тест», «напиши», «реализуй», «добавь», «исправь», «выполни», «сделай», «создай», «инициализируй», «настрой», «обнови», «implement», «create», «задачу», «таск», «task»
— **СТОП. Немедленно делегируй агенту. Не читай файлы сам. Не анализируй структуру сам. Не пиши код сам.**

+**Если задача содержит слова:** «архитектура», «не нравится», «как устроить», «как сервисы общаются»,
+«какой подход», «что выбрать» — И при этом НЕТ готового таск-файла или плана — СТОП, сначала `backend-architect`.
+
+**Если есть готовый таск-файл или план реализации** — это задача для `backend-implementor`,
+даже если файл называется «gateway», «infrastructure», «architecture» или содержит слова про новые сервисы.
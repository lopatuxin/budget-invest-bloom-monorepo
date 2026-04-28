# Roadmap реализации Investment Service

## Контекст

`docs/architecture.md` описывает новый микросервис **investment** для учёта инвестиционного портфеля на MOEX. По итогам анализа реального стека монорепо приняты следующие решения:

- **Java 24 + Lombok + MapStruct + Liquibase YAML** (как `budget/`), не Kotlin/Flyway как написано в архитектуре.
- **JWT не валидируем в investment** — `userId` приходит готовым в `ApiRequest.user` (gateway разбирает токен и пробрасывает контекст). Как в `budget/`.
- **Порт 8084** (8083 занят `budget`). В `architecture.md` исправлено.
- Реализация пошаговая: каждая фаза даёт работающий, тестируемый результат.

---

## Статус фаз

| Фаза | Название | Статус |
|---|---|---|
| 0 | Roadmap в репо + правки architecture.md | ✅ Готово |
| 1 | Скелет сервиса и инфраструктура | ✅ Готово |
| 2 | Доменная модель и миграции (без MOEX) | ✅ Готово |
| 3 | Транзакции и позиции + базовый фронт | ✅ Готово |
| 4 | MOEX ISS клиент и кэш рыночных данных | ✅ Готово |
| 5 | Аналитика и история цен | ✅ Готово |
| 6 | Прогноз сложного процента + Scheduler + дивиденды | ⬜ Ожидает |

---

## Фаза 1 — Скелет сервиса и инфраструктура

**Цель:** сервис `investment` поднимается через `docker-compose up`, отвечает `/actuator/health`, доступен через gateway по `/api/investment/**`.

### Создать `investment/`

- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` — копия `budget/`, изменить `rootProject.name = "investment"` и groupId `pyc.lopatuxin.investment`.
- `Dockerfile.dev` — копия `budget/Dockerfile.dev`, EXPOSE `8084` + `35732` (LiveReload).
- `src/main/java/pyc/lopatuxin/investment/InvestmentApplication.java` — `@SpringBootApplication`.
- `src/main/resources/application.yml` — `spring.application.name: investment`.
- `src/main/resources/application-dev.yml` — datasource на `investment-postgres`, port `8084`, Liquibase enabled.
- `src/main/resources/db/changelog/db.changelog-master.yml` + `v1.0.0/changelog-v1.0.0.yml` (пустой список).
- DTO-обёртки и ошибки — скопировать из `budget/`:
  - `dto/common/ApiRequest.java`, `UserContextDto.java`
  - `dto/response/ResponseApi.java`
  - `exception/GlobalExceptionHandler.java`
- `src/test/` — `AbstractIntegrationTest.java`, `TestcontainersConfiguration.java`, `application-test.yml` из `budget/`.

### Изменить существующие файлы

- `docker-compose.yml` (корень):
  - Добавить `investment-postgres` (volume `investment_postgres_data`, БД `investment_dev`, порт хоста `5434`).
  - Добавить `investment-service` (build `./investment`, expose `8084`+`35732`, healthcheck `/actuator/health`, depends_on `investment-postgres`).
  - Env `INVESTMENT_SERVICE_URL` в `gateway-service`.
  - Env-блок `INVESTMENT_POSTGRES_*` в `backup-service`.
  - В `volumes:` — `investment_postgres_data`, `investment_gradle_cache`.
- `gateway/src/main/resources/application-dev.yml`:
  - Route `id: investment, Path=/api/investment/**, uri: ${gateway.services.investment-url}` с фильтром `UserEnrichmentFilter`.
  - В `gateway.services` — `investment-url: ${INVESTMENT_SERVICE_URL:http://investment-service:8084}`.

### Проверка фазы 1

```
docker-compose up -d investment-service investment-postgres
curl http://localhost:8082/api/investment/actuator/health
```
Ожидается `{"status":"UP"}`. Liquibase применяет пустой changelog без ошибок.

---

## Фаза 2 — Доменная модель и MOEX-кэш (без MOEX)

**Цель:** все сущности из `architecture.md` § «Модель данных» существуют в БД, репозитории работают, интеграционные тесты проходят.

### Liquibase changesets (`v1.0.0/`)

- `001-create-securities-table.yml` — `securities` (`ticker` PK, `board_id`, `name`, `type`, `sector`, `currency`, `history_status`, `last_price_updated_at`).
- `002-create-transactions-table.yml` — `transactions` (`id` UUID PK, `user_id`, `security_ticker` FK→`securities`, `quantity`, `price`, `executed_at`, `type`, `created_at`).
- `003-create-positions-table.yml` — `positions` (`id` UUID, unique(`user_id`,`security_ticker`), `quantity`, `average_price`, `total_cost`, `updated_at`).
- `004-create-price-snapshots-table.yml` — `price_snapshots` (`ticker` PK, `last_price`, `previous_close`, `fetched_at`).
- `005-create-price-history-table.yml` — `price_history` composite PK (`ticker`,`trade_date`), `open/close/high/low`, `volume`.
- `006-create-dividends-table.yml` — `dividends` (`id`, `ticker`, `record_date`, `payment_date`, `amount_per_share`, `currency`, `status`).

### Java entities + repositories

Пакеты `entity/`, `entity/enums/`, `repository/`. Enum-ы: `SecurityType` (STOCK/BOND), `TransactionType` (BUY/SELL), `HistoryStatus` (PENDING/READY). Никаких лишних `@Column` там, где Hibernate маппит camelCase→snake_case автоматически.

### Тесты

Репозиторные интеграционные тесты на Testcontainers: CRUD + уникальный индекс позиции (`user_id`,`security_ticker`).

---

## Фаза 3 — Транзакции и позиции + базовый фронт

**Цель:** работающие эндпоинты CRUD сделок и обзор позиций, при добавлении сделки `Position` пересчитывается.

### Backend

- `TransactionService.create()` — валидация, сохранение, пересчёт `Position`:
  - BUY: средневзвешенная `averagePrice`, накопление `totalCost`.
  - SELL: уменьшение `quantity`; P&L = (`currentPrice` − `averagePrice`) × qty; удаление позиции при `quantity = 0`.
- До фазы 4: `Security` создаётся «на лету» с минимумом полей (ticker, type из запроса).
- `TransactionController` POST/GET/DELETE `/api/investment/transactions`.
- `PortfolioController` GET `/api/investment/portfolio/positions`, `/positions/{ticker}` — `currentPrice`/`pnl` = null пока.

### Тесты

`TransactionServiceTest` (юнит, моки), `TransactionControllerTest` (MockMvc + Testcontainers).

### Фронт (`budget-invest-bloom/`)

- `src/types/investment.ts` — DTO интерфейсы.
- Хуки TanStack Query: `useTransactions`, `useCreateTransaction`, `useDeleteTransaction`, `usePositions`.
- `src/pages/Investments.tsx` — заменить моки на реальные данные; добавить форму «Добавить сделку».

### Проверка фазы 3

В браузере: добавить сделку, увидеть позицию, удалить транзакцию — агрегат пересчитывается.

---

## Фаза 4 — MOEX ISS клиент и кэш рыночных данных

**Цель:** сервис загружает метаданные бумаг и текущие цены из MOEX, кэширует их, `currentPrice` в позициях корректен.

### Backend

- Зависимости: `spring-boot-starter-webflux` (только WebClient), `caffeine`.
- `MoexIssClient` — `WebClient` с `MOEX_ISS_BASE_URL`, методы `fetchSecurity(ticker)`, `fetchSnapshots(tickers)`, retry + exponential backoff + jitter, circuit breaker (Resilience4j).
- `MarketDataService` — `ensureSecurity(ticker)`, `getSnapshot(ticker)` (Caffeine TTL 5 мин → fallback БД → fallback MOEX; при недоступности MOEX отдаёт stale с флагом).
- `MarketDataController` GET `/api/investment/market/search?q=`, `/market/security/{ticker}`.
- Интеграция в `TransactionService.create`: `ensureSecurity()` вместо заглушки.
- `PortfolioService`: суммарная стоимость, P&L, доли STOCK/BOND, разрез по секторам.
- `PortfolioController` GET `/api/investment/portfolio/overview`.

### Тесты

`MoexIssClientTest` (MockWebServer), `MarketDataServiceTest` (кэш-логика).

### Фронт

KPI-карточки overview, текущая цена и P&L в позициях, Recharts pie по секторам.

---

## Фаза 5 — Аналитика и история цен

**Цель:** графики стоимости портфеля и цены бумаги во времени.

### Backend

- Докачка `PriceHistory` из MOEX on-demand при первом запросе по бумаге.
- `AnalyticsService.portfolioValueHistory(userId, from, to)` — восстановление позиций на каждый день, Σ(quantity × close).
- `AnalyticsService.securityPriceHistory(ticker, from, to)`.
- `AnalyticsController` GET `/api/investment/analytics/portfolio/value-history?from&to`, `/analytics/security/{ticker}/price-history?from&to`.

### Фронт

`LineChart` (Recharts) стоимости портфеля и цены бумаги.

### Проверка фазы 5

График на демо-наборе (2–3 сделки) совпадает с ручным расчётом.

---

## Фаза 6 — Прогноз сложного процента + Scheduler + дивиденды

**Цель:** калькулятор прогноза, фоновое обновление цен и дивидендов.

### Backend

- `ProjectionService.project(...)` по алгоритму из `architecture.md` §«Алгоритм калькулятора»:
  - `historicalAnnualReturn(ticker, N)` = CAGR по `PriceHistory` за N=3 года.
  - Дивиденды за последние 12 мес добавляются к доходности.
  - Ежемесячная итерация: `value = value × (1 + r_month) + deposit − value × withdrawal_rate/12`.
- `AnalyticsController` POST `/api/investment/analytics/projection`.
- `MarketDataRefreshScheduler` (`@Scheduled`):
  - Каждые 5 мин (торговое время МСК) — обновление `PriceSnapshot` для активных позиций.
  - Раз в сутки ночью — инкрементальная догрузка `PriceHistory` и обновление `Dividend`.
- `MarketDataService.fetchHistoryAsync(ticker)` — on-demand с `historyStatus PENDING → READY`.

### Фронт

Страница «Калькулятор»: форма параметров + `LineChart` прогноза; индикатор «данные загружаются» при `historyStatus = PENDING`.

### Проверка фазы 6

Прогноз детерминирован. Scheduler не роняет сервис при недоступности MOEX (stale данные, circuit breaker open).

---

## Файлы-образцы

| Цель | Источник |
|---|---|
| Build/Gradle | `budget/build.gradle.kts`, `budget/settings.gradle.kts`, `auth/gradle.properties` |
| Dockerfile | `budget/Dockerfile.dev` |
| application.yml | `budget/src/main/resources/application.yml`, `application-dev.yml` |
| Liquibase | `budget/src/main/resources/db/changelog/db.changelog-master.yml`, `v1.0.0/001-create-categories-table.yml` |
| DTO common | `budget/.../dto/common/ApiRequest.java`, `UserContextDto.java` |
| DTO response | `budget/.../dto/response/ResponseApi.java` |
| ExceptionHandler | `budget/.../exception/GlobalExceptionHandler.java` |
| Controller стиль | `budget/.../controller/CategoryController.java` |
| Тесты-база | `budget/src/test/.../AbstractIntegrationTest.java`, `TestcontainersConfiguration.java` |
| Gateway routes | `gateway/src/main/resources/application-dev.yml` |
| Frontend api | `budget-invest-bloom/src/lib/api.ts`, `src/pages/Investments.tsx` |

---

## Итоговая проверка (после всех фаз)

1. `docker-compose up -d` — все сервисы с `status: healthy`.
2. Залогиниться во фронте → `/investments` → добавить сделку `SBER 10 шт по 250 ₽` → позиция с реальной ценой и P&L.
3. График стоимости портфеля отображается; калькулятор возвращает ряд значений.
4. Перезапуск `investment-service` не теряет данные (volume `investment_postgres_data`).
5. `./gradlew :investment:test` — зелёные.

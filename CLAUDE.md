# Project

**Budget Invest Bloom** — микросервисная платформа управления личными финансами.
Сервисы: auth/, budget/, investment/, gateway/, budget-invest-bloom/ (frontend) — каждый отдельный git-репозиторий, оркестрация через корневой `docker-compose.yml`.
ВСЕГДА общайся на русском. Комментарии в коде — на английском.

# Stack & Build

| Сервис | Нестандартное |
|---|---|
| Auth | `./gradlew bootTestRun` — запуск с Testcontainers |
| Gateway | build.gradle на **Groovy DSL** (остальные сервисы — Kotlin DSL) |
| Docker | у каждого сервиса своя БД: `auth-postgres` / `budget-postgres` / `investment-postgres` |
| JWT | валидирует ТОЛЬКО gateway, далее `userId` прокидывается в `ApiRequest.user` — auth/budget/investment токены не разбирают |

# Common Mistakes

- Кросс-сервисный контракт запроса/ответа — классы `ApiRequest`/`ApiResponse` в `dto/common` каждого сервиса. ALWAYS использовать их, NEVER заводить свои обёртки
- ВСЕ git-коммиты делать из корня монорепо, NEVER из поддиректорий сервисов
- Починка падающих тестов идёт ТОЛЬКО через test-writer, NEVER через java-dev (java-dev чинит прод вместо тестов)
- NEVER добавлять `@Column` для camelCase-полей — Hibernate сам мапит в snake_case
- `docs/architecture.md` и `docs/investment-implementation-roadmap.md` — про investment-сервис, не общая архитектура платформы

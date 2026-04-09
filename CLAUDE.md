# Project

**Budget Invest Bloom** — микросервисная платформа управления личными финансами.
Каждый сервис (auth/, budget/, gateway/, budget-invest-bloom/) — отдельный git-репозиторий.
Оркестрация через корневой `docker-compose.yml`.
ВСЕГДА общайся на русском. Комментарии в коде — на английском.

# Stack & Build

| Сервис | Нестандартное |
|---|---|
| Auth | `./gradlew bootTestRun` — запуск с Testcontainers |
| Gateway | build.gradle на **Groovy DSL** (остальные — Kotlin DSL) |
| Docker | `docker-compose up auth-postgres` / `budget-postgres` — отдельные БД |

# Common Mistakes

- auth/docs/ описывают ПЛАНИРУЕМЫЕ фичи (Redis, Kafka, OAuth2, 2FA) — это НЕ текущий код
- budget/docs/api/ — примеры используют @Data и индексы — НАРУШАЕТ конвенции агентов
- API контракт (запрос/ответ): смотри docs/standartRequestAndResponse.md

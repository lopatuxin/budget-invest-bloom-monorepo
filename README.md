<div align="center">

# 💰 Budget Invest Bloom

**Платформа управления личными финансами**

[![Java](https://img.shields.io/badge/Java-24-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docs.docker.com/compose/)

</div>

---

Fullstack-приложение на микросервисной архитектуре для учёта расходов, бюджетирования и аналитики личных финансов. Backend на Java 24 + Spring Boot 3, фронтенд на React 18 + TypeScript, API Gateway с JWT-авторизацией, всё запускается одной командой через Docker Compose.

## 🏗️ Архитектура

```mermaid
graph TB
    Client[🌐 Browser :8080] --> Frontend[⚛️ Frontend<br/>React + Vite + shadcn-ui]
    Frontend --> Gateway[🚪 API Gateway :8082<br/>Spring Cloud Gateway]
    Gateway -->|JWT Validation| Auth[🔐 Auth Service :8081<br/>Spring Security + JWT]
    Gateway --> Budget[💼 Budget Service :8083<br/>Spring Boot + JPA]
    Auth --> AuthDB[(🗄️ PostgreSQL<br/>auth_dev :5432)]
    Budget --> BudgetDB[(🗄️ PostgreSQL<br/>budget_dev :5433)]

    style Client fill:#4a9eff,color:#fff
    style Frontend fill:#61dafb,color:#000
    style Gateway fill:#ff6b35,color:#fff
    style Auth fill:#6db33f,color:#fff
    style Budget fill:#6db33f,color:#fff
    style AuthDB fill:#4169e1,color:#fff
    style BudgetDB fill:#4169e1,color:#fff
```

## 🛠️ Стек технологий

| Слой | Технологии |
|------|-----------|
| **Backend** | Java 24, Spring Boot 3, Spring Security, Spring Cloud Gateway, Spring Data JPA, Liquibase, JWT (JJWT), MapStruct, Lombok |
| **Frontend** | React 18, TypeScript, Vite, shadcn-ui (Radix UI), Tailwind CSS, TanStack Query, React Router, React Hook Form, Zod, Recharts |
| **Базы данных** | PostgreSQL 15 (2 изолированных инстанса) |
| **Инфраструктура** | Docker, Docker Compose, Testcontainers |
| **Документация** | SpringDoc OpenAPI (Swagger UI) |

## 📁 Структура проекта

```
budget-invest-bloom-monorepo/
├── auth/                    # Сервис аутентификации (JWT, refresh tokens)
├── budget/                  # Сервис управления бюджетом
├── gateway/                 # API Gateway (маршрутизация + валидация токенов)
├── budget-invest-bloom/     # React SPA (фронтенд)
├── docs/                    # Документация API-контрактов
└── docker-compose.yml       # Оркестрация всех сервисов
```

## 🚀 Быстрый старт

```bash
# Клонировать репозиторий
git clone https://github.com/lopatuxin/budget-invest-bloom-monorepo.git
cd budget-invest-bloom-monorepo

# Запустить всё одной командой
docker compose up
```

Приложение будет доступно:

| Сервис | URL | Описание |
|--------|-----|----------|
| Frontend | http://localhost:8080 | Веб-интерфейс |
| Gateway | http://localhost:8082 | API Gateway |
| Auth API | http://localhost:8081 | Аутентификация |
| Budget API | http://localhost:8083 | Управление бюджетом |

## 🔐 Аутентификация

- **Access Token** — JWT, время жизни 15 минут
- **Refresh Token** — время жизни 7 дней
- Валидация токенов на уровне Gateway через общий JWT-секрет с Auth-сервисом

## 📡 API

Все сервисы используют унифицированный контракт запросов и ответов.

**Формат ответа:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Операция выполнена успешно",
  "timestamp": "2026-04-06T12:00:00Z",
  "body": { }
}
```

Подробная документация: [`docs/standartRequestAndResponse.md`](docs/standartRequestAndResponse.md)

---

<div align="center">
  <sub>Разработано с ☕ и Spring Boot</sub>
</div>

# Спецификация API бюджетного сервиса (Budget Service)

## Оглавление

1. [Обзор](#обзор)
2. [Базовая конфигурация](#базовая-конфигурация)
3. [Стандартная структура API](#стандартная-структура-api)
4. [Схема базы данных](#схема-базы-данных)
5. [API Endpoints](#api-endpoints)
6. [DTO (Data Transfer Objects)](#dto-data-transfer-objects)
7. [Бизнес-логика сервисов](#бизнес-логика-сервисов)
8. [Обработка ошибок](#обработка-ошибок)
9. [Liquibase миграции](#liquibase-миграции)

---

## Обзор

Бюджетный сервис предоставляет функциональность для управления личным бюджетом пользователя:
- Учёт доходов и расходов
- Категоризация операций
- Установка лимитов по категориям
- Расчёт финансовых показателей (баланс, капитал, личная инфляция)
- Аналитика и статистика по периодам

**Базовый URL:** `http://localhost:8083/budget/api`

**Порт:** `8083`

---

## Базовая конфигурация

### Порты
| Компонент | Порт |
|-----------|------|
| Budget Service | 8083 |
| Budget PostgreSQL | 5433 (внешний), 5432 (в Docker) |

### База данных
- **Имя БД:** `budget_dev`
- **Пользователь:** `budget_user`
- **Пароль:** `dev_password`

---

## Стандартная структура API

### Формат запроса (Request)

Все POST/PUT запросы должны соответствовать структуре:

```json
{
  "user": {
    "userId": "uuid",
    "email": "string",
    "role": "USER|ADMIN|MODERATOR",
    "sessionId": "uuid"
  },
  "data": {
    // Полезная нагрузка запроса
  }
}
```

**Важно:** Блок `user` заполняется API Gateway из JWT токена. Backend извлекает `userId` для привязки данных к пользователю.

### Формат ответа (Response)

Все ответы соответствуют структуре:

```json
{
  "id": "uuid",
  "status": 200,
  "message": "Сообщение на русском языке",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    // Данные ответа
  }
}
```

| Поле | Тип | Описание |
|------|-----|----------|
| id | UUID | Корреляционный ID для трейсинга |
| status | int | HTTP статус код |
| message | string | Сообщение для пользователя (на русском) |
| timestamp | ISO-8601 | Временная метка ответа |
| body | object | Данные ответа |

---

## Схема базы данных

### Диаграмма ER

```
┌──────────────────────┐         ┌──────────────────────┐
│      categories      │         │       incomes        │
├──────────────────────┤         ├──────────────────────┤
│ id (PK, UUID)        │         │ id (PK, UUID)        │
│ user_id (UUID)       │         │ user_id (UUID)       │
│ name (VARCHAR 100)   │         │ source (VARCHAR 50)  │
│ budget (DECIMAL)     │         │ amount (DECIMAL)     │
│ emoji (VARCHAR 10)   │         │ description (TEXT)   │
│ created_at (TIMESTMP)│         │ date (DATE)          │
│ updated_at (TIMESTMP)│         │ created_at (TIMESTMP)│
└──────────────────────┘         │ updated_at (TIMESTMP)│
         │                       └──────────────────────┘
         │ 1:N
         ▼
┌──────────────────────┐         ┌──────────────────────┐
│      expenses        │         │   capital_records    │
├──────────────────────┤         ├──────────────────────┤
│ id (PK, UUID)        │         │ id (PK, UUID)        │
│ user_id (UUID)       │         │ user_id (UUID)       │
│ category_id (FK,UUID)│         │ amount (DECIMAL)     │
│ amount (DECIMAL)     │         │ month (INT)          │
│ description (TEXT)   │         │ year (INT)           │
│ date (DATE)          │         │ created_at (TIMESTMP)│
│ created_at (TIMESTMP)│         │ updated_at (TIMESTMP)│
│ updated_at (TIMESTMP)│         └──────────────────────┘
└──────────────────────┘
```

### Таблица: categories (Категории расходов)

| Колонка | Тип | Ограничения | Описание |
|---------|-----|-------------|----------|
| id | UUID | PK, NOT NULL | Уникальный идентификатор |
| user_id | UUID | NOT NULL, INDEX | ID пользователя (из Auth Service) |
| name | VARCHAR(100) | NOT NULL | Название категории |
| budget | DECIMAL(15,2) | NOT NULL, DEFAULT 0 | Лимит бюджета на категорию |
| emoji | VARCHAR(10) | NULL | Эмодзи для отображения |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Дата создания |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Дата обновления |

**Уникальный индекс:** `(user_id, name)` - одно название категории на пользователя

### Таблица: expenses (Расходы)

| Колонка | Тип | Ограничения | Описание |
|---------|-----|-------------|----------|
| id | UUID | PK, NOT NULL | Уникальный идентификатор |
| user_id | UUID | NOT NULL, INDEX | ID пользователя |
| category_id | UUID | FK → categories.id, NOT NULL | Категория расхода |
| amount | DECIMAL(15,2) | NOT NULL, CHECK > 0 | Сумма расхода |
| description | TEXT | NULL | Описание операции |
| date | DATE | NOT NULL | Дата расхода |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Дата создания записи |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Дата обновления записи |

**Индексы:**
- `(user_id, date)` - для выборки по периоду
- `(user_id, category_id, date)` - для выборки по категории за период

### Таблица: incomes (Доходы)

| Колонка | Тип | Ограничения | Описание |
|---------|-----|-------------|----------|
| id | UUID | PK, NOT NULL | Уникальный идентификатор |
| user_id | UUID | NOT NULL, INDEX | ID пользователя |
| source | VARCHAR(50) | NOT NULL | Источник дохода |
| amount | DECIMAL(15,2) | NOT NULL, CHECK > 0 | Сумма дохода |
| description | TEXT | NULL | Описание операции |
| date | DATE | NOT NULL | Дата дохода |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Дата создания записи |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Дата обновления записи |

**Допустимые значения source:**
- `SALARY` - Зарплата
- `FREELANCE` - Фриланс
- `INVESTMENTS` - Инвестиции
- `GIFTS` - Подарки
- `OTHER` - Прочее

**Индексы:**
- `(user_id, date)` - для выборки по периоду

### Таблица: capital_records (Записи капитала)

| Колонка | Тип | Ограничения | Описание |
|---------|-----|-------------|----------|
| id | UUID | PK, NOT NULL | Уникальный идентификатор |
| user_id | UUID | NOT NULL | ID пользователя |
| amount | DECIMAL(15,2) | NOT NULL | Общий капитал на конец месяца |
| month | INT | NOT NULL, CHECK 1-12 | Месяц записи |
| year | INT | NOT NULL, CHECK >= 2020 | Год записи |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Дата создания |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Дата обновления |

**Уникальный индекс:** `(user_id, month, year)` - одна запись капитала на месяц

---

## API Endpoints

### Сводка (Summary)

#### GET /budget/api/summary

Получить сводку бюджета за указанный период.

**Query Parameters:**
| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| month | int | Да | Месяц (1-12) |
| year | int | Да | Год (2020-2100) |

**Response 200:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": 200,
  "message": "Сводка бюджета успешно получена",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "period": {
      "month": 12,
      "year": 2024
    },
    "income": 150000.00,
    "expenses": 89500.00,
    "balance": 60500.00,
    "capital": 875000.00,
    "personalInflation": 6.8,
    "trends": {
      "income": "+8.2%",
      "expenses": "+3.1%",
      "balance": "+12.8%",
      "capital": "+15.4%",
      "inflation": "+0.3%"
    },
    "categories": [
      {
        "id": "uuid",
        "name": "Еда",
        "amount": 25000.00,
        "budget": 30000.00,
        "emoji": "🍽️",
        "percentUsed": 83.33
      }
    ]
  }
}
```

---

### Категории (Categories)

#### GET /budget/api/categories

Получить все категории пользователя.

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Категории успешно получены",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "categories": [
      {
        "id": "uuid",
        "name": "Еда",
        "budget": 30000.00,
        "emoji": "🍽️",
        "createdAt": "2024-01-01T00:00:00Z"
      }
    ],
    "total": 5
  }
}
```

#### POST /budget/api/categories

Создать новую категорию.

**Request:**
```json
{
  "user": {
    "userId": "uuid",
    "email": "user@example.com",
    "role": "USER",
    "sessionId": "uuid"
  },
  "data": {
    "name": "Развлечения",
    "budget": 15000.00,
    "emoji": "🎬"
  }
}
```

**Валидация:**
- `name`: обязательное, 1-100 символов, уникальное для пользователя
- `budget`: обязательное, > 0
- `emoji`: опциональное, максимум 10 символов

**Response 201:**
```json
{
  "id": "uuid",
  "status": 201,
  "message": "Категория успешно создана",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "category": {
      "id": "new-uuid",
      "name": "Развлечения",
      "budget": 15000.00,
      "emoji": "🎬",
      "createdAt": "2024-12-15T10:00:00Z"
    }
  }
}
```

**Response 409 (Conflict):**
```json
{
  "id": "uuid",
  "status": 409,
  "message": "Категория с таким названием уже существует",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": null
}
```

#### PUT /budget/api/categories/{categoryId}

Обновить категорию.

**Path Parameters:**
- `categoryId` (UUID) - ID категории

**Request:**
```json
{
  "user": { ... },
  "data": {
    "name": "Продукты питания",
    "budget": 35000.00,
    "emoji": "🛒"
  }
}
```

**Валидация:**
- Все поля опциональные, но хотя бы одно должно присутствовать
- `name`: 1-100 символов, уникальное для пользователя
- `budget`: > 0

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Категория успешно обновлена",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "category": {
      "id": "category-uuid",
      "name": "Продукты питания",
      "budget": 35000.00,
      "emoji": "🛒",
      "updatedAt": "2024-12-15T10:00:00Z"
    }
  }
}
```

**Response 404:**
```json
{
  "id": "uuid",
  "status": 404,
  "message": "Категория не найдена",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": null
}
```

#### DELETE /budget/api/categories/{categoryId}

Удалить категорию.

**Path Parameters:**
- `categoryId` (UUID) - ID категории

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Категория успешно удалена",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": null
}
```

**Response 409 (Conflict):**
```json
{
  "id": "uuid",
  "status": 409,
  "message": "Невозможно удалить категорию: существуют связанные расходы",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "expensesCount": 15
  }
}
```

#### GET /budget/api/categories/{categoryId}/expenses

Получить расходы по категории с историей.

**Path Parameters:**
- `categoryId` (UUID) - ID категории

**Query Parameters:**
| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| month | int | Да | Месяц (1-12) |
| year | int | Да | Год (2020-2100) |

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Расходы по категории успешно получены",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "category": {
      "id": "uuid",
      "name": "Еда",
      "budget": 30000.00,
      "emoji": "🍽️"
    },
    "period": {
      "month": 12,
      "year": 2024
    },
    "totalMonth": 25000.00,
    "percentUsed": 83.33,
    "dailyExpenses": [
      {
        "id": "uuid",
        "date": "2024-12-15",
        "description": "Продукты в супермаркете",
        "amount": 1200.00
      }
    ],
    "monthlyHistory": [
      { "month": "Янв", "year": 2024, "amount": 22000.00 },
      { "month": "Фев", "year": 2024, "amount": 24500.00 }
    ],
    "yearlyHistory": [
      { "year": 2022, "amount": 240000.00 },
      { "year": 2023, "amount": 280000.00 },
      { "year": 2024, "amount": 275000.00 }
    ]
  }
}
```

---

### Расходы (Expenses)

#### GET /budget/api/expenses

Получить расходы за период.

**Query Parameters:**
| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| month | int | Да | Месяц (1-12) |
| year | int | Да | Год (2020-2100) |
| categoryId | UUID | Нет | Фильтр по категории |

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Расходы успешно получены",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "period": {
      "month": 12,
      "year": 2024
    },
    "expenses": [
      {
        "id": "uuid",
        "categoryId": "uuid",
        "categoryName": "Еда",
        "categoryEmoji": "🍽️",
        "amount": 1200.00,
        "description": "Продукты в супермаркете",
        "date": "2024-12-15"
      }
    ],
    "total": 89500.00,
    "count": 45
  }
}
```

#### POST /budget/api/expenses

Добавить расход.

**Request:**
```json
{
  "user": {
    "userId": "uuid",
    "email": "user@example.com",
    "role": "USER",
    "sessionId": "uuid"
  },
  "data": {
    "categoryId": "uuid",
    "amount": 1200.00,
    "description": "Продукты в супермаркете",
    "date": "2024-12-15"
  }
}
```

**Валидация:**
- `categoryId`: обязательное, должна существовать и принадлежать пользователю
- `amount`: обязательное, > 0, максимум 999999999.99
- `description`: опциональное, максимум 500 символов
- `date`: обязательное, формат ISO (YYYY-MM-DD), не в будущем

**Response 201:**
```json
{
  "id": "uuid",
  "status": 201,
  "message": "Расход успешно добавлен",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "expense": {
      "id": "new-uuid",
      "categoryId": "uuid",
      "categoryName": "Еда",
      "amount": 1200.00,
      "description": "Продукты в супермаркете",
      "date": "2024-12-15",
      "createdAt": "2024-12-15T10:00:00Z"
    }
  }
}
```

#### DELETE /budget/api/expenses/{expenseId}

Удалить расход.

**Path Parameters:**
- `expenseId` (UUID) - ID расхода

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Расход успешно удалён",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": null
}
```

---

### Доходы (Incomes)

#### GET /budget/api/incomes

Получить доходы за период.

**Query Parameters:**
| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| month | int | Да | Месяц (1-12) |
| year | int | Да | Год (2020-2100) |
| source | string | Нет | Фильтр по источнику |

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Доходы успешно получены",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "period": {
      "month": 12,
      "year": 2024
    },
    "incomes": [
      {
        "id": "uuid",
        "source": "SALARY",
        "sourceDisplay": "Зарплата",
        "amount": 150000.00,
        "description": "Зарплата за декабрь",
        "date": "2024-12-05"
      }
    ],
    "total": 150000.00,
    "count": 2,
    "bySource": {
      "SALARY": 120000.00,
      "FREELANCE": 30000.00
    }
  }
}
```

#### POST /budget/api/incomes

Добавить доход.

**Request:**
```json
{
  "user": {
    "userId": "uuid",
    "email": "user@example.com",
    "role": "USER",
    "sessionId": "uuid"
  },
  "data": {
    "source": "SALARY",
    "amount": 150000.00,
    "description": "Зарплата за декабрь",
    "date": "2024-12-05"
  }
}
```

**Валидация:**
- `source`: обязательное, одно из: SALARY, FREELANCE, INVESTMENTS, GIFTS, OTHER
- `amount`: обязательное, > 0, максимум 999999999.99
- `description`: опциональное, максимум 500 символов
- `date`: обязательное, формат ISO (YYYY-MM-DD), не в будущем

**Response 201:**
```json
{
  "id": "uuid",
  "status": 201,
  "message": "Доход успешно добавлен",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "income": {
      "id": "new-uuid",
      "source": "SALARY",
      "sourceDisplay": "Зарплата",
      "amount": 150000.00,
      "description": "Зарплата за декабрь",
      "date": "2024-12-05",
      "createdAt": "2024-12-15T10:00:00Z"
    }
  }
}
```

#### DELETE /budget/api/incomes/{incomeId}

Удалить доход.

**Path Parameters:**
- `incomeId` (UUID) - ID дохода

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Доход успешно удалён",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": null
}
```

---

### Метрики (Metrics)

#### GET /budget/api/metrics/{metric}

Получить детальную статистику по метрике.

**Path Parameters:**
- `metric` (string) - Тип метрики: `income`, `expenses`, `balance`, `capital`, `inflation`

**Query Parameters:**
| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| year | int | Да | Год (2020-2100) |

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Метрика успешно получена",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "metric": "income",
    "metricDisplay": "Доходы",
    "year": 2024,
    "monthlyData": [
      {
        "month": 1,
        "monthName": "Янв",
        "income": 145000.00,
        "expenses": 85000.00,
        "balance": 60000.00,
        "capital": 800000.00,
        "inflation": 6.2
      }
    ],
    "statistics": {
      "current": 150000.00,
      "average": 149250.00,
      "minimum": 140000.00,
      "maximum": 153000.00,
      "total": 1791000.00,
      "changePercent": 1.35,
      "trend": "+1.35%"
    },
    "previousYearComparison": {
      "previousTotal": 1650000.00,
      "changePercent": 8.55,
      "trend": "+8.55%"
    }
  }
}
```

---

### Капитал (Capital)

#### GET /budget/api/capital

Получить историю капитала.

**Query Parameters:**
| Параметр | Тип | Обязательный | Описание |
|----------|-----|--------------|----------|
| year | int | Нет | Год (если не указан - все годы) |

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "История капитала успешно получена",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "currentCapital": 875000.00,
    "records": [
      {
        "id": "uuid",
        "month": 12,
        "year": 2024,
        "amount": 875000.00
      }
    ],
    "growthPercent": 15.4,
    "trend": "+15.4%"
  }
}
```

#### PUT /budget/api/capital

Установить/обновить капитал на текущий месяц.

**Request:**
```json
{
  "user": { ... },
  "data": {
    "amount": 875000.00,
    "month": 12,
    "year": 2024
  }
}
```

**Валидация:**
- `amount`: обязательное, >= 0
- `month`: обязательное, 1-12
- `year`: обязательное, 2020-2100

**Response 200:**
```json
{
  "id": "uuid",
  "status": 200,
  "message": "Капитал успешно обновлён",
  "timestamp": "2024-12-15T10:00:00Z",
  "body": {
    "record": {
      "id": "uuid",
      "amount": 875000.00,
      "month": 12,
      "year": 2024,
      "updatedAt": "2024-12-15T10:00:00Z"
    }
  }
}
```

---

## DTO (Data Transfer Objects)

### Структура пакетов

```
pyc.lopatuxin.budget.dto/
├── request/
│   ├── StandardRequest.java
│   ├── UserContext.java
│   ├── CreateCategoryRequest.java
│   ├── UpdateCategoryRequest.java
│   ├── CreateExpenseRequest.java
│   ├── CreateIncomeRequest.java
│   └── UpdateCapitalRequest.java
├── response/
│   ├── StandardResponse.java
│   ├── BudgetSummaryResponse.java
│   ├── CategoryResponse.java
│   ├── CategoryListResponse.java
│   ├── CategoryExpensesResponse.java
│   ├── ExpenseResponse.java
│   ├── ExpenseListResponse.java
│   ├── IncomeResponse.java
│   ├── IncomeListResponse.java
│   ├── MetricResponse.java
│   └── CapitalResponse.java
└── common/
    ├── PeriodDto.java
    ├── TrendsDto.java
    ├── MonthlyDataDto.java
    └── StatisticsDto.java
```

### Базовые классы

#### StandardRequest.java
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StandardRequest<T> {
    @NotNull(message = "Блок user обязателен")
    private UserContext user;

    @NotNull(message = "Блок data обязателен")
    private T data;
}
```

#### UserContext.java
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    @NotNull(message = "userId обязателен")
    private UUID userId;

    @NotBlank(message = "email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    @NotNull(message = "role обязательна")
    private UserRole role;

    private UUID sessionId;
}
```

#### StandardResponse.java
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StandardResponse<T> {
    private UUID id;
    private int status;
    private String message;
    private Instant timestamp;
    private T body;

    public static <T> StandardResponse<T> success(String message, T body) {
        return StandardResponse.<T>builder()
            .id(UUID.randomUUID())
            .status(200)
            .message(message)
            .timestamp(Instant.now())
            .body(body)
            .build();
    }

    public static <T> StandardResponse<T> created(String message, T body) {
        return StandardResponse.<T>builder()
            .id(UUID.randomUUID())
            .status(201)
            .message(message)
            .timestamp(Instant.now())
            .body(body)
            .build();
    }

    public static <T> StandardResponse<T> error(int status, String message) {
        return StandardResponse.<T>builder()
            .id(UUID.randomUUID())
            .status(status)
            .message(message)
            .timestamp(Instant.now())
            .body(null)
            .build();
    }
}
```

### Request DTOs

#### CreateCategoryRequest.java
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {
    @NotBlank(message = "Название категории обязательно")
    @Size(min = 1, max = 100, message = "Название должно быть от 1 до 100 символов")
    private String name;

    @NotNull(message = "Бюджет обязателен")
    @DecimalMin(value = "0.01", message = "Бюджет должен быть больше 0")
    @DecimalMax(value = "999999999.99", message = "Бюджет превышает максимальное значение")
    private BigDecimal budget;

    @Size(max = 10, message = "Эмодзи не должно превышать 10 символов")
    private String emoji;
}
```

#### CreateExpenseRequest.java
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseRequest {
    @NotNull(message = "Категория обязательна")
    private UUID categoryId;

    @NotNull(message = "Сумма обязательна")
    @DecimalMin(value = "0.01", message = "Сумма должна быть больше 0")
    @DecimalMax(value = "999999999.99", message = "Сумма превышает максимальное значение")
    private BigDecimal amount;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    private String description;

    @NotNull(message = "Дата обязательна")
    @PastOrPresent(message = "Дата не может быть в будущем")
    private LocalDate date;
}
```

#### CreateIncomeRequest.java
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateIncomeRequest {
    @NotNull(message = "Источник дохода обязателен")
    private IncomeSource source;

    @NotNull(message = "Сумма обязательна")
    @DecimalMin(value = "0.01", message = "Сумма должна быть больше 0")
    @DecimalMax(value = "999999999.99", message = "Сумма превышает максимальное значение")
    private BigDecimal amount;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    private String description;

    @NotNull(message = "Дата обязательна")
    @PastOrPresent(message = "Дата не может быть в будущем")
    private LocalDate date;
}
```

---

## Бизнес-логика сервисов

### BudgetSummaryService

**Метод:** `getSummary(UUID userId, int month, int year)`

**Логика:**
1. Получить сумму всех доходов пользователя за указанный месяц/год
2. Получить сумму всех расходов пользователя за указанный месяц/год
3. Рассчитать баланс: `balance = income - expenses`
4. Получить запись капитала за месяц (или последнюю известную)
5. Рассчитать личную инфляцию (сравнение расходов с прошлым годом)
6. Рассчитать тренды относительно предыдущего месяца
7. Получить категории с суммами расходов за месяц

**Расчёт трендов:**
```java
trend = ((currentValue - previousValue) / previousValue) * 100
// Форматирование: "+8.2%" или "-3.5%"
```

**Расчёт личной инфляции:**
```java
// Сравнение средних расходов текущего года с прошлым
currentYearAvgExpenses = сумма расходов текущего года / кол-во прошедших месяцев
previousYearAvgExpenses = сумма расходов прошлого года / 12
inflation = ((currentYearAvgExpenses - previousYearAvgExpenses) / previousYearAvgExpenses) * 100
```

### CategoryService

**Метод:** `createCategory(UUID userId, CreateCategoryRequest request)`

**Логика:**
1. Проверить уникальность названия категории для пользователя
2. Если существует - вернуть ошибку 409
3. Создать сущность Category
4. Сохранить в БД
5. Вернуть созданную категорию

**Метод:** `deleteCategory(UUID userId, UUID categoryId)`

**Логика:**
1. Проверить существование категории и принадлежность пользователю
2. Проверить наличие связанных расходов
3. Если есть расходы - вернуть ошибку 409 с количеством
4. Удалить категорию
5. Вернуть успешный ответ

### ExpenseService

**Метод:** `createExpense(UUID userId, CreateExpenseRequest request)`

**Логика:**
1. Проверить существование категории и принадлежность пользователю
2. Проверить, что дата не в будущем
3. Создать сущность Expense
4. Сохранить в БД
5. Вернуть созданный расход с информацией о категории

### MetricsService

**Метод:** `getMetric(UUID userId, MetricType metric, int year)`

**Логика:**
1. Получить данные за каждый месяц года:
   - income: сумма доходов за месяц
   - expenses: сумма расходов за месяц
   - balance: income - expenses
   - capital: запись капитала за месяц
   - inflation: расчёт на основе расходов
2. Рассчитать статистику:
   - current: значение за последний месяц
   - average: среднее за год
   - minimum/maximum: мин/макс за год
   - total: сумма за год
   - changePercent: изменение относительно предыдущего месяца
3. Сравнить с предыдущим годом

---

## Обработка ошибок

### Коды ошибок

| Код | Сообщение | Причина |
|-----|-----------|---------|
| 400 | Некорректные данные запроса | Ошибка валидации |
| 401 | Требуется авторизация | Отсутствует или невалидный токен |
| 403 | Доступ запрещён | Нет прав на ресурс |
| 404 | Ресурс не найден | Категория/расход/доход не существует |
| 409 | Конфликт данных | Дублирование или связанные данные |
| 422 | Ошибка обработки | Бизнес-валидация не пройдена |
| 500 | Внутренняя ошибка сервера | Непредвиденная ошибка |

### GlobalExceptionHandler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest()
            .body(StandardResponse.error(400, "Ошибка валидации", errors));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<StandardResponse<Void>> handleCategoryNotFound(
            CategoryNotFoundException ex) {
        return ResponseEntity.status(404)
            .body(StandardResponse.error(404, "Категория не найдена"));
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ResponseEntity<StandardResponse<Void>> handleCategoryExists(
            CategoryAlreadyExistsException ex) {
        return ResponseEntity.status(409)
            .body(StandardResponse.error(409, "Категория с таким названием уже существует"));
    }

    @ExceptionHandler(CategoryHasExpensesException.class)
    public ResponseEntity<StandardResponse<Map<String, Integer>>> handleCategoryHasExpenses(
            CategoryHasExpensesException ex) {
        return ResponseEntity.status(409)
            .body(StandardResponse.error(409,
                "Невозможно удалить категорию: существуют связанные расходы",
                Map.of("expensesCount", ex.getCount())));
    }
}
```

---

## Liquibase миграции

### Структура файлов

```
db/changelog/
├── db.changelog-master.yml
└── v1.0.0/
    ├── changelog-v1.0.0.yml
    ├── 001-create-categories-table.yml
    ├── 002-create-expenses-table.yml
    ├── 003-create-incomes-table.yml
    └── 004-create-capital-records-table.yml
```

### db.changelog-master.yml

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/v1.0.0/changelog-v1.0.0.yml
```

### changelog-v1.0.0.yml

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/v1.0.0/001-create-categories-table.yml
  - include:
      file: db/changelog/v1.0.0/002-create-expenses-table.yml
  - include:
      file: db/changelog/v1.0.0/003-create-incomes-table.yml
  - include:
      file: db/changelog/v1.0.0/004-create-capital-records-table.yml
```

### 001-create-categories-table.yml

```yaml
databaseChangeLog:
  - changeSet:
      id: 001-create-categories-table
      author: budget-service
      changes:
        - createTable:
            tableName: categories
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: user_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: name
                  type: varchar(100)
                  constraints:
                    nullable: false
              - column:
                  name: budget
                  type: decimal(15,2)
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
              - column:
                  name: emoji
                  type: varchar(10)
              - column:
                  name: created_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false

        - createIndex:
            indexName: idx_categories_user_id
            tableName: categories
            columns:
              - column:
                  name: user_id

        - addUniqueConstraint:
            constraintName: uq_categories_user_name
            tableName: categories
            columnNames: user_id, name

      rollback:
        - dropTable:
            tableName: categories
```

### 002-create-expenses-table.yml

```yaml
databaseChangeLog:
  - changeSet:
      id: 002-create-expenses-table
      author: budget-service
      changes:
        - createTable:
            tableName: expenses
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: user_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: category_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: amount
                  type: decimal(15,2)
                  constraints:
                    nullable: false
              - column:
                  name: description
                  type: text
              - column:
                  name: date
                  type: date
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false

        - addForeignKeyConstraint:
            constraintName: fk_expenses_category
            baseTableName: expenses
            baseColumnNames: category_id
            referencedTableName: categories
            referencedColumnNames: id
            onDelete: RESTRICT

        - createIndex:
            indexName: idx_expenses_user_date
            tableName: expenses
            columns:
              - column:
                  name: user_id
              - column:
                  name: date

        - createIndex:
            indexName: idx_expenses_user_category_date
            tableName: expenses
            columns:
              - column:
                  name: user_id
              - column:
                  name: category_id
              - column:
                  name: date

        - sql:
            sql: ALTER TABLE expenses ADD CONSTRAINT chk_expenses_amount CHECK (amount > 0)

      rollback:
        - dropTable:
            tableName: expenses
```

### 003-create-incomes-table.yml

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-create-incomes-table
      author: budget-service
      changes:
        - createTable:
            tableName: incomes
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: user_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: source
                  type: varchar(50)
                  constraints:
                    nullable: false
              - column:
                  name: amount
                  type: decimal(15,2)
                  constraints:
                    nullable: false
              - column:
                  name: description
                  type: text
              - column:
                  name: date
                  type: date
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false

        - createIndex:
            indexName: idx_incomes_user_date
            tableName: incomes
            columns:
              - column:
                  name: user_id
              - column:
                  name: date

        - sql:
            sql: |
              ALTER TABLE incomes ADD CONSTRAINT chk_incomes_amount CHECK (amount > 0);
              ALTER TABLE incomes ADD CONSTRAINT chk_incomes_source
                CHECK (source IN ('SALARY', 'FREELANCE', 'INVESTMENTS', 'GIFTS', 'OTHER'));

      rollback:
        - dropTable:
            tableName: incomes
```

### 004-create-capital-records-table.yml

```yaml
databaseChangeLog:
  - changeSet:
      id: 004-create-capital-records-table
      author: budget-service
      changes:
        - createTable:
            tableName: capital_records
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: user_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: amount
                  type: decimal(15,2)
                  constraints:
                    nullable: false
              - column:
                  name: month
                  type: int
                  constraints:
                    nullable: false
              - column:
                  name: year
                  type: int
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamp with time zone
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false

        - addUniqueConstraint:
            constraintName: uq_capital_user_month_year
            tableName: capital_records
            columnNames: user_id, month, year

        - createIndex:
            indexName: idx_capital_user_year
            tableName: capital_records
            columns:
              - column:
                  name: user_id
              - column:
                  name: year

        - sql:
            sql: |
              ALTER TABLE capital_records ADD CONSTRAINT chk_capital_month CHECK (month >= 1 AND month <= 12);
              ALTER TABLE capital_records ADD CONSTRAINT chk_capital_year CHECK (year >= 2020);

      rollback:
        - dropTable:
            tableName: capital_records
```

---

## Приложения

### Enum: IncomeSource

```java
public enum IncomeSource {
    SALARY("Зарплата"),
    FREELANCE("Фриланс"),
    INVESTMENTS("Инвестиции"),
    GIFTS("Подарки"),
    OTHER("Прочее");

    private final String displayName;

    IncomeSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

### Enum: MetricType

```java
public enum MetricType {
    INCOME("Доходы"),
    EXPENSES("Расходы"),
    BALANCE("Баланс"),
    CAPITAL("Капитал"),
    INFLATION("Инфляция");

    private final String displayName;

    MetricType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

### Названия месяцев (русский)

```java
public class MonthNames {
    public static final String[] SHORT = {
        "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
        "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
    };

    public static final String[] FULL = {
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    };
}
```

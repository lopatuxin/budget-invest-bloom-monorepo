# Первоначальная настройка Liquibase

Это руководство описывает базовую настройку Liquibase для нового Spring Boot проекта без миграций. После выполнения этих шагов проект сможет запуститься с пустой базой данных.

## 1. Добавление зависимости в build.gradle.kts

Добавьте зависимость Liquibase в секцию `dependencies`:

```kotlin
dependencies {
    // ... другие зависимости
    implementation("org.liquibase:liquibase-core")
    runtimeOnly("org.postgresql:postgresql")
    // ... другие зависимости
}
```

## 2. Настройка конфигурации Spring Boot

### application.yml (базовый профиль)

Минимальная конфигурация:

```yaml
spring:
  application:
    name: auth
```

### application-dev.yml (профиль для разработки)

Полная конфигурация для работы с базой данных:

```yaml
spring:
  application:
    name: auth

  datasource:
    url: jdbc:postgresql://localhost:5432/auth_dev
    username: auth_user
    driver-class-name: org.postgresql.Driver
    password: dev_password

  jpa:
    hibernate:
      ddl-auto: validate  # ВАЖНО: используем validate, чтобы Hibernate не создавал схему автоматически
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yml  # Путь к главному файлу changelog

logging:
  level:
    liquibase: INFO  # Логи Liquibase для отслеживания выполнения миграций
```

**Важные моменты:**
- `hibernate.ddl-auto: validate` - Hibernate только проверяет схему, но не создает/изменяет её. Всё управление схемой делегируется Liquibase.
- `liquibase.change-log` - указывает на главный файл changelog, который будет содержать все миграции.

## 3. Создание структуры директорий для Liquibase

Создайте следующую структуру директорий в `src/main/resources/`:

```
src/main/resources/
└── db/
    └── changelog/
        ├── db.changelog-master.yml  (главный файл)
        └── v1.0.0/                   (директория для версии 1.0.0)
            └── changelog-v1.0.0.yml  (файл версии)
```

## 4. Создание главного файла changelog

### db.changelog-master.yml

Создайте файл `src/main/resources/db/changelog/db.changelog-master.yml`:

```yaml
databaseChangeLog:
  - logicalFilePath: db/changelog/db.changelog-master.yml
```

Это минимальный файл без включений. Позже вы будете добавлять миграции через секции `include`.

**Пример с включением версии v1.0.0:**

```yaml
databaseChangeLog:
  - logicalFilePath: db/changelog/db.changelog-master.yml
  - include:
      file: db/changelog/v1.0.0/changelog-v1.0.0.yml
```

### changelog-v1.0.0.yml (опционально)

Если вы планируете организовать миграции по версиям, создайте файл `src/main/resources/db/changelog/v1.0.0/changelog-v1.0.0.yml`:

```yaml
databaseChangeLog:
  - logicalFilePath: db/changelog/v1.0.0/changelog-v1.0.0.yml
  # Здесь будут включаться файлы миграций
```

## 5. Настройка базы данных PostgreSQL

### Docker Compose

Для локальной разработки удобно использовать Docker Compose:

```yaml
services:
  postgres-dev:
    image: postgres:latest
    container_name: auth-postgres-dev
    environment:
      POSTGRES_DB: auth_dev
      POSTGRES_USER: auth_user
      POSTGRES_PASSWORD: dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

Запустите PostgreSQL:

```bash
docker-compose up postgres-dev
```

## 6. Запуск приложения

### Проверка настройки

1. Убедитесь, что PostgreSQL запущен
2. Запустите приложение:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Что произойдет при первом запуске:

1. Liquibase подключится к базе данных
2. Создаст служебные таблицы:
   - `databasechangelog` - история выполненных миграций
   - `databasechangeloglock` - блокировка для предотвращения конкурентных миграций
3. Поскольку миграций нет, других таблиц создано не будет
4. Приложение успешно запустится

### Проверка через логи

В логах вы увидите сообщения Liquibase:

```
INFO  liquibase.lockservice : Successfully acquired change log lock
INFO  liquibase.changelog : Reading from auth_dev.databasechangelog
INFO  liquibase.lockservice : Successfully released change log lock
```

## 7. Проверка таблиц Liquibase в БД

Подключитесь к PostgreSQL и проверьте созданные таблицы:

```sql
\c auth_dev
\dt

-- Вы должны увидеть:
--  databasechangelog
--  databasechangeloglock
```

## Структура организации миграций (рекомендации)

### По версиям

```
db/changelog/
├── db.changelog-master.yml
├── v1.0.0/
│   ├── changelog-v1.0.0.yml
│   ├── 001-create-users-table.yml
│   └── 002-create-roles-table.yml
└── v1.1.0/
    ├── changelog-v1.1.0.yml
    └── 001-add-user-status-column.yml
```

### Включение в master файл

```yaml
databaseChangeLog:
  - logicalFilePath: db/changelog/db.changelog-master.yml
  - include:
      file: db/changelog/v1.0.0/changelog-v1.0.0.yml
  - include:
      file: db/changelog/v1.1.0/changelog-v1.1.0.yml
```

### Включение миграций в файл версии

```yaml
databaseChangeLog:
  - logicalFilePath: db/changelog/v1.0.0/changelog-v1.0.0.yml
  - include:
      file: db/changelog/v1.0.0/001-create-users-table.yml
  - include:
      file: db/changelog/v1.0.0/002-create-roles-table.yml
```

## Типичные проблемы и решения

### Ошибка "Failed to acquire change log lock"

**Причина:** Предыдущий процесс Liquibase не завершился корректно.

**Решение:**
```sql
DELETE FROM databasechangeloglock;
```

### Ошибка "Validation Failed"

**Причина:** JPA entities не соответствуют схеме БД.

**Решение:** Проверьте, что все entity классы правильно отражают структуру таблиц в миграциях.

### База данных не создается

**Причина:** База данных должна существовать до запуска приложения.

**Решение:** Создайте базу вручную или используйте Docker с инициализацией:
```sql
CREATE DATABASE auth_dev;
CREATE USER auth_user WITH PASSWORD 'dev_password';
GRANT ALL PRIVILEGES ON DATABASE auth_dev TO auth_user;
```

## Следующие шаги

После успешной настройки Liquibase вы можете:

1. Создавать миграции для таблиц
2. Использовать различные форматы changelog (YAML, XML, SQL, JSON)
3. Настроить Liquibase для разных окружений (dev, test, prod)
4. Использовать Liquibase Maven/Gradle плагины для управления миграциями

## Полезные команды Gradle для Liquibase

```bash
# Просмотр статуса миграций
./gradlew liquibaseStatus

# Откат последней миграции
./gradlew liquibaseRollbackCount -PliquibaseCommandValue=1

# Генерация changelog из существующей БД
./gradlew liquibaseGenerateChangeLog
```

**Примечание:** Для использования этих команд необходимо подключить Liquibase Gradle Plugin.
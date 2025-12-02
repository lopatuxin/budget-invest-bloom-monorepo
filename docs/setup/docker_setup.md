# Настройка Docker для микросервисов проекта

## Содержание

1. [Обзор](#обзор)
2. [Структура файлов Docker](#структура-файлов-docker)
3. [Docker Compose Configuration](#docker-compose-configuration)
4. [Dockerfile для Development](#dockerfile-для-development)
5. [Переменные окружения](#переменные-окружения)
6. [Команды для работы с Docker](#команды-для-работы-с-docker)
7. [Healthchecks и мониторинг](#healthchecks-и-мониторинг)
8. [Volumes и Networks](#volumes-и-networks)
9. [Hot-reload для разработки](#hot-reload-для-разработки)
10. [Best Practices](#best-practices)
11. [Troubleshooting](#troubleshooting)

---

## Обзор

Данная документация описывает стандартную настройку Docker для микросервисов проекта. Конфигурация включает:

- **PostgreSQL** - база данных
- **Spring Boot приложение** - микросервис на Java 24 с Gradle 8.14.2
- **Hot-reload** - автоматическая перезагрузка при изменении кода (только для development)
- **Healthchecks** - проверка состояния сервисов
- **Volumes** - персистентное хранение данных
- **Networks** - изолированная сеть для межсервисного взаимодействия

---

## Структура файлов Docker

```
project-root/
├── docker-compose.yml          # Основная конфигурация сервисов
├── Dockerfile.dev              # Dockerfile для development с hot-reload
├── .env                        # Активные переменные окружения (git ignored)
├── .env.example                # Пример файла с переменными окружения
├── .env.dev                    # Переменные для development
├── .env.prod                   # Переменные для production
└── docker/
    └── init/                   # SQL-скрипты инициализации БД (опционально)
```

---

## Docker Compose Configuration

### Полная конфигурация docker-compose.yml

```yaml
services:
  # PostgreSQL Database
  postgres-dev:
    image: postgres:latest
    container_name: auth-postgres-dev
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-auth_dev}
      POSTGRES_USER: ${POSTGRES_USER:-auth_user}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-dev_password}
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docker/init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-auth_user} -d ${POSTGRES_DB:-auth_dev}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    networks:
      - auth-network

  # Spring Boot Application (Development с Hot-Reload)
  auth-app:
    build:
      context: .
      dockerfile: Dockerfile.dev
    container_name: auth-app-dev
    environment:
      # Spring Configuration
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-dev:5432/${POSTGRES_DB:-auth_dev}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-auth_user}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-dev_password}

      # JWT Configuration
      JWT_SECRET: ${JWT_SECRET:-404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}
      JWT_ACCESS_TOKEN_EXPIRATION: ${JWT_ACCESS_TOKEN_EXPIRATION:-900000}
      JWT_REFRESH_TOKEN_EXPIRATION: ${JWT_REFRESH_TOKEN_EXPIRATION:-604800000}

      # DevTools Configuration (для hot-reload)
      SPRING_DEVTOOLS_RESTART_ENABLED: ${DEVTOOLS_RESTART_ENABLED:-true}
      SPRING_DEVTOOLS_LIVERELOAD_ENABLED: ${DEVTOOLS_LIVERELOAD_ENABLED:-true}
    ports:
      - "${APP_PORT:-8081}:8081"
      - "${LIVERELOAD_PORT:-35729}:35729"  # Порт для LiveReload
    volumes:
      # Volume mapping для hot-reload (монтируем исходный код)
      - ./src:/app/src:rw
      - ./build.gradle.kts:/app/build.gradle.kts:rw
      - ./settings.gradle.kts:/app/settings.gradle.kts:rw
      # Кэш Gradle (для ускорения сборки)
      - gradle_cache:/root/.gradle
      # Временные файлы сборки
      - ./build:/app/build
    depends_on:
      postgres-dev:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - auth-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/auth/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s

volumes:
  postgres_data:
    driver: local
  gradle_cache:
    driver: local

networks:
  auth-network:
    driver: bridge
```

### Описание сервисов

#### PostgreSQL (postgres-dev)

- **image**: `postgres:latest` - официальный образ PostgreSQL
- **environment**: переменные окружения для создания БД и пользователя
- **ports**: проброс порта `5432` на хост (настраивается через `${POSTGRES_PORT}`)
- **volumes**:
  - `postgres_data` - персистентное хранение данных PostgreSQL
  - `./docker/init` - SQL-скрипты для инициализации БД (выполняются при первом запуске)
- **healthcheck**: проверка готовности БД перед запуском зависимых сервисов
- **restart**: `unless-stopped` - автоматический перезапуск при падении
- **networks**: подключение к изолированной сети `auth-network`

#### Spring Boot Application (auth-app)

- **build**: сборка из `Dockerfile.dev` для development с hot-reload
- **environment**: переменные окружения Spring Boot (профиль, БД, JWT, DevTools)
- **ports**:
  - `8081` - основной порт приложения
  - `35729` - порт LiveReload для Spring DevTools
- **volumes**:
  - Маппинг исходного кода (`./src`) для hot-reload
  - Gradle конфигурационные файлы
  - Кэш Gradle для ускорения сборки
  - Директория `build` для временных файлов
- **depends_on**: ожидает готовности PostgreSQL (проверка через healthcheck)
- **healthcheck**: проверка через Spring Actuator endpoint `/actuator/health`
- **start_period**: 60 секунд для первоначальной загрузки приложения

---

## Dockerfile для Development

### Dockerfile.dev

```dockerfile
# Dockerfile для development с hot-reload
# Используем базовый образ Eclipse Temurin JDK 24
FROM eclipse-temurin:24-jdk AS base

# Устанавливаем Gradle 8.14 (поддерживает Java 24)
ENV GRADLE_VERSION=8.14.2
ENV GRADLE_HOME=/opt/gradle
RUN apt-get update && apt-get install -y wget unzip curl && \
    wget https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip gradle-${GRADLE_VERSION}-bin.zip && \
    mv gradle-${GRADLE_VERSION} ${GRADLE_HOME} && \
    rm gradle-${GRADLE_VERSION}-bin.zip && \
    ln -s ${GRADLE_HOME}/bin/gradle /usr/bin/gradle

WORKDIR /app

# Копируем файлы конфигурации Gradle для кэширования зависимостей
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# Загружаем зависимости (этот слой будет кэшироваться)
RUN gradle dependencies --no-daemon || true

# Копируем исходный код
COPY src ./src

# Устанавливаем переменные окружения для development
ENV SPRING_PROFILES_ACTIVE=dev
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false --add-opens=java.base/java.lang=ALL-UNNAMED"
ENV JAVA_TOOL_OPTIONS="--enable-native-access=ALL-UNNAMED"

# Открываем порт приложения
EXPOSE 8081

# Открываем порт для LiveReload (Spring DevTools)
EXPOSE 35729

# Запускаем приложение с bootRun (поддержка hot-reload через DevTools)
CMD ["gradle", "bootRun", "--no-daemon", "--continuous"]
```

### Особенности Dockerfile.dev

1. **Базовый образ**: `eclipse-temurin:24-jdk` - официальный JDK 24 от Eclipse Foundation
2. **Gradle**: устанавливается версия 8.14.2 с поддержкой Java 24
3. **Кэширование зависимостей**: слой с `gradle dependencies` кэшируется, ускоряя повторные сборки
4. **Spring DevTools**: включен для автоматической перезагрузки при изменении кода
5. **LiveReload**: порт 35729 для автоматического обновления браузера
6. **Continuous mode**: `--continuous` флаг для отслеживания изменений файлов

---

## Переменные окружения

### Файл .env.example (шаблон)

```env
# Пример файла с переменными окружения
# Скопируйте этот файл в .env.dev или .env.prod и настройте значения

# PostgreSQL Configuration
POSTGRES_DB=auth_dev
POSTGRES_USER=auth_user
POSTGRES_PASSWORD=dev_password
POSTGRES_PORT=5432

# Spring Configuration
SPRING_PROFILES_ACTIVE=dev

# Application Ports
APP_PORT=8081
LIVERELOAD_PORT=35729

# JWT Configuration
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000

# DevTools Configuration (только для development)
DEVTOOLS_RESTART_ENABLED=true
DEVTOOLS_LIVERELOAD_ENABLED=true
```

### Категории переменных окружения

#### PostgreSQL Configuration
- `POSTGRES_DB` - имя базы данных (по умолчанию: `auth_dev`)
- `POSTGRES_USER` - пользователь БД (по умолчанию: `auth_user`)
- `POSTGRES_PASSWORD` - пароль БД (по умолчанию: `dev_password`)
- `POSTGRES_PORT` - порт БД на хосте (по умолчанию: `5432`)

#### Spring Configuration
- `SPRING_PROFILES_ACTIVE` - активный профиль Spring Boot (`dev`, `prod`, `test`)

#### Application Ports
- `APP_PORT` - порт приложения на хосте (по умолчанию: `8081`)
- `LIVERELOAD_PORT` - порт LiveReload для DevTools (по умолчанию: `35729`)

#### JWT Configuration
- `JWT_SECRET` - секретный ключ для подписи JWT токенов (base64)
- `JWT_ACCESS_TOKEN_EXPIRATION` - время жизни access token в миллисекундах (15 минут = 900000)
- `JWT_REFRESH_TOKEN_EXPIRATION` - время жизни refresh token в миллисекундах (7 дней = 604800000)

#### DevTools Configuration
- `DEVTOOLS_RESTART_ENABLED` - включение автоматической перезагрузки (`true`/`false`)
- `DEVTOOLS_LIVERELOAD_ENABLED` - включение LiveReload (`true`/`false`)

### Создание .env файла

```bash
# Для development
cp .env.example .env.dev

# Для production (обязательно измените секретные значения!)
cp .env.example .env.prod

# Активный файл (используется docker-compose)
cp .env.dev .env
```

### ⚠️ Важно для Production

**Обязательно измените следующие значения в `.env.prod`:**

1. `POSTGRES_PASSWORD` - используйте сильный пароль
2. `JWT_SECRET` - сгенерируйте новый секретный ключ (минимум 256 бит)
3. `DEVTOOLS_RESTART_ENABLED=false` - отключите DevTools
4. `DEVTOOLS_LIVERELOAD_ENABLED=false` - отключите LiveReload

```bash
# Генерация безопасного JWT секрета (256 бит в base64)
openssl rand -base64 32 | tr -d '\n' && echo
```

---

## Команды для работы с Docker

### Запуск сервисов

```bash
# Запустить только PostgreSQL
docker-compose up postgres-dev

# Запустить PostgreSQL в фоновом режиме
docker-compose up -d postgres-dev

# Запустить все сервисы (PostgreSQL + приложение)
docker-compose up

# Запустить все сервисы в фоновом режиме
docker-compose up -d

# Пересобрать образы и запустить
docker-compose up --build
```

### Остановка сервисов

```bash
# Остановить все сервисы
docker-compose down

# Остановить и удалить volumes (полная очистка)
docker-compose down -v

# Остановить определенный сервис
docker-compose stop postgres-dev
docker-compose stop auth-app
```

### Просмотр логов

```bash
# Логи всех сервисов
docker-compose logs

# Логи с отслеживанием (live)
docker-compose logs -f

# Логи конкретного сервиса
docker-compose logs postgres-dev
docker-compose logs auth-app

# Последние 100 строк логов
docker-compose logs --tail=100 auth-app
```

### Перезапуск сервисов

```bash
# Перезапустить все сервисы
docker-compose restart

# Перезапустить конкретный сервис
docker-compose restart auth-app
docker-compose restart postgres-dev
```

### Выполнение команд внутри контейнеров

```bash
# Подключиться к PostgreSQL
docker-compose exec postgres-dev psql -U auth_user -d auth_dev

# Выполнить SQL-скрипт
docker-compose exec postgres-dev psql -U auth_user -d auth_dev -f /path/to/script.sql

# Войти в shell контейнера приложения
docker-compose exec auth-app bash

# Выполнить Gradle команду
docker-compose exec auth-app gradle test
```

### Мониторинг и статус

```bash
# Статус всех сервисов
docker-compose ps

# Проверка healthcheck
docker-compose ps
docker inspect --format='{{json .State.Health}}' auth-app-dev | jq

# Использование ресурсов
docker stats
```

### Очистка

```bash
# Удалить неиспользуемые образы
docker image prune -a

# Удалить неиспользуемые volumes
docker volume prune

# Полная очистка Docker
docker system prune -a --volumes
```

---

## Healthchecks и мониторинг

### PostgreSQL Healthcheck

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-auth_user} -d ${POSTGRES_DB:-auth_dev}"]
  interval: 10s      # Проверка каждые 10 секунд
  timeout: 5s        # Таймаут проверки 5 секунд
  retries: 5         # 5 попыток перед объявлением unhealthy
```

**Команда проверки вручную:**
```bash
docker-compose exec postgres-dev pg_isready -U auth_user -d auth_dev
```

### Spring Boot Application Healthcheck

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8081/auth/actuator/health"]
  interval: 30s      # Проверка каждые 30 секунд
  timeout: 10s       # Таймаут проверки 10 секунд
  retries: 5         # 5 попыток перед объявлением unhealthy
  start_period: 60s  # 60 секунд на первоначальную загрузку
```

**Проверка вручную:**
```bash
curl http://localhost:8081/auth/actuator/health
```

**Ожидаемый ответ:**
```json
{
  "status": "UP"
}
```

### Spring Boot Actuator Endpoints

Доступные endpoints для мониторинга (в development режиме):

- `/actuator/health` - статус здоровья приложения
- `/actuator/info` - информация о приложении
- `/actuator/metrics` - метрики приложения
- `/actuator/env` - переменные окружения

```bash
# Получить все метрики
curl http://localhost:8081/auth/actuator/metrics

# Получить конкретную метрику (например, использование памяти)
curl http://localhost:8081/auth/actuator/metrics/jvm.memory.used
```

---

## Volumes и Networks

### Volumes (Персистентное хранение)

#### postgres_data
```yaml
volumes:
  postgres_data:
    driver: local
```

- **Назначение**: хранение данных PostgreSQL
- **Путь внутри контейнера**: `/var/lib/postgresql/data`
- **Тип**: локальный volume (данные сохраняются после перезапуска контейнера)
- **Очистка**: `docker-compose down -v` (удаляет все данные БД!)

#### gradle_cache
```yaml
volumes:
  gradle_cache:
    driver: local
```

- **Назначение**: кэш зависимостей Gradle
- **Путь внутри контейнера**: `/root/.gradle`
- **Преимущество**: ускорение повторных сборок (зависимости не загружаются заново)

### Bind Mounts (для Hot-Reload)

```yaml
volumes:
  - ./src:/app/src:rw                           # Исходный код Java
  - ./build.gradle.kts:/app/build.gradle.kts:rw # Gradle конфигурация
  - ./settings.gradle.kts:/app/settings.gradle.kts:rw
  - ./build:/app/build                          # Временные файлы сборки
```

- **Режим**: `rw` (read-write) - контейнер может читать и записывать
- **Синхронизация**: изменения на хосте мгновенно видны в контейнере
- **Hot-reload**: Spring DevTools отслеживает изменения и перезагружает приложение

### Networks (Сетевая изоляция)

```yaml
networks:
  auth-network:
    driver: bridge
```

- **Тип**: bridge network (изолированная виртуальная сеть)
- **DNS**: контейнеры могут обращаться друг к другу по имени сервиса
  ```
  jdbc:postgresql://postgres-dev:5432/auth_dev
  ```
- **Изоляция**: контейнеры изолированы от других Docker сетей
- **Безопасность**: сервисы доступны снаружи только через проброшенные порты

---

## Hot-reload для разработки

### Как это работает

1. **Spring DevTools** включен в зависимостях (`build.gradle.kts`):
   ```kotlin
   developmentOnly("org.springframework.boot:spring-boot-devtools")
   ```

2. **Bind mounts** монтируют исходный код:
   ```yaml
   volumes:
     - ./src:/app/src:rw
   ```

3. **Gradle continuous mode** отслеживает изменения:
   ```dockerfile
   CMD ["gradle", "bootRun", "--no-daemon", "--continuous"]
   ```

4. **LiveReload** обновляет браузер:
   - Порт 35729 проброшен на хост
   - Браузер автоматически обновляется при изменениях

### Workflow разработки

1. Запустите сервисы:
   ```bash
   docker-compose up
   ```

2. Измените код в `src/`:
   ```java
   // Отредактируйте любой Java файл
   @GetMapping("/test")
   public String test() {
       return "Updated response";
   }
   ```

3. Сохраните файл - приложение автоматически перезагрузится:
   ```
   [restartedMain] o.s.b.d.autoconfigure.OptionalLiveReloadServer : LiveReload server is running on port 35729
   [restartedMain] pyc.lopatuxin.auth.AuthApplication            : Started AuthApplication in 2.345 seconds
   ```

4. Проверьте изменения:
   ```bash
   curl http://localhost:8081/auth/test
   # Output: Updated response
   ```

### Ограничения Hot-Reload

**Перезагружается автоматически:**
- Изменения в Java классах
- Изменения в `application*.yml`
- Изменения в ресурсах (templates, static files)

**Требует ручного перезапуска:**
- Изменения в `build.gradle.kts` (новые зависимости)
- Изменения в структуре БД (новые миграции Liquibase)
- Изменения в docker-compose.yml

```bash
# Перезапуск после изменения зависимостей
docker-compose restart auth-app

# Полная пересборка после изменения Dockerfile
docker-compose up --build
```

---

## Best Practices

### 1. Безопасность

#### ✅ DO (Рекомендуется)

- Используйте `.env` файлы для секретов (добавьте в `.gitignore`)
- Генерируйте уникальные `JWT_SECRET` для каждого окружения
- Используйте сильные пароли БД в production
- Отключайте DevTools в production (`DEVTOOLS_RESTART_ENABLED=false`)
- Ограничьте проброс портов в production (не публикуйте БД наружу)

#### ❌ DON'T (Не рекомендуется)

- Не коммитьте `.env` файлы в Git
- Не используйте одинаковые пароли для dev и prod
- Не оставляйте дефолтные секреты в production
- Не публикуйте порт PostgreSQL (5432) в production

### 2. Performance

#### ✅ DO (Рекомендуется)

- Используйте volumes для кэширования (`gradle_cache`)
- Оптимизируйте Dockerfile с multi-stage builds
- Используйте healthchecks для правильного порядка запуска
- Настройте `start_period` для медленно стартующих приложений

```yaml
healthcheck:
  start_period: 60s  # Даем время на первоначальную загрузку
```

#### ❌ DON'T (Не рекомендуется)

- Не монтируйте все директории в bind mounts (только необходимые)
- Не используйте `latest` тег в production (фиксируйте версии)

```yaml
# ❌ Плохо
image: postgres:latest

# ✅ Хорошо
image: postgres:16.1
```

### 3. Development Workflow

#### ✅ DO (Рекомендуется)

- Используйте `.env.dev` для локальной разработки
- Запускайте только необходимые сервисы:
  ```bash
  docker-compose up postgres-dev  # Только БД
  ./gradlew bootRun               # Приложение на хосте
  ```
- Проверяйте логи при проблемах:
  ```bash
  docker-compose logs -f auth-app
  ```

#### ❌ DON'T (Не рекомендуется)

- Не запускайте `docker-compose down -v` без необходимости (теряются данные БД)
- Не игнорируйте healthcheck статусы

### 4. Структура проекта

#### ✅ DO (Рекомендуется)

```
project/
├── docker-compose.yml      # Только для development
├── docker-compose.prod.yml # Отдельный файл для production
├── Dockerfile.dev          # Development с hot-reload
├── Dockerfile              # Production (optimized, multi-stage)
├── .env.example            # Шаблон (коммитится в Git)
├── .env.dev                # Development переменные (коммитится в Git)
├── .env.prod               # Production переменные (шаблон, без секретов)
└── .env                    # Активный файл (в .gitignore)
```

---

## Troubleshooting

### Проблема: PostgreSQL не стартует

**Симптомы:**
```
postgres-dev | Error: Database is uninitialized and superuser password is not specified.
```

**Решение:**
1. Проверьте `.env` файл:
   ```bash
   cat .env | grep POSTGRES
   ```
2. Убедитесь, что переменные установлены:
   ```env
   POSTGRES_DB=auth_dev
   POSTGRES_USER=auth_user
   POSTGRES_PASSWORD=dev_password
   ```
3. Пересоздайте контейнер:
   ```bash
   docker-compose down -v
   docker-compose up postgres-dev
   ```

### Проблема: Приложение не может подключиться к БД

**Симптомы:**
```
Connection to postgres-dev:5432 refused
```

**Решение:**
1. Проверьте статус PostgreSQL:
   ```bash
   docker-compose ps postgres-dev
   ```
2. Проверьте healthcheck:
   ```bash
   docker-compose exec postgres-dev pg_isready -U auth_user -d auth_dev
   ```
3. Убедитесь, что `depends_on` настроен правильно:
   ```yaml
   depends_on:
     postgres-dev:
       condition: service_healthy
   ```

### Проблема: Hot-reload не работает

**Симптомы:**
Изменения в коде не применяются автоматически

**Решение:**
1. Проверьте bind mounts:
   ```bash
   docker-compose exec auth-app ls -la /app/src
   ```
2. Проверьте DevTools в логах:
   ```bash
   docker-compose logs auth-app | grep "LiveReload"
   ```
3. Убедитесь, что DevTools включен:
   ```env
   DEVTOOLS_RESTART_ENABLED=true
   DEVTOOLS_LIVERELOAD_ENABLED=true
   ```
4. Проверьте, что изменяете файлы внутри смонтированных директорий

### Проблема: Порт уже занят

**Симптомы:**
```
Error: Bind for 0.0.0.0:8081 failed: port is already allocated
```

**Решение:**
1. Найдите процесс, использующий порт:
   ```bash
   # Windows
   netstat -ano | findstr :8081

   # Linux/Mac
   lsof -i :8081
   ```
2. Измените порт в `.env`:
   ```env
   APP_PORT=8082
   ```
3. Перезапустите сервисы:
   ```bash
   docker-compose down
   docker-compose up
   ```

### Проблема: Недостаточно места на диске

**Симптомы:**
```
Error: no space left on device
```

**Решение:**
1. Очистите неиспользуемые образы и volumes:
   ```bash
   docker system df  # Проверить использование
   docker image prune -a
   docker volume prune
   docker system prune -a --volumes
   ```

### Проблема: Gradle не может скачать зависимости

**Симптомы:**
```
Could not resolve all dependencies
```

**Решение:**
1. Проверьте доступ к интернету из контейнера:
   ```bash
   docker-compose exec auth-app ping -c 3 8.8.8.8
   ```
2. Очистите Gradle cache:
   ```bash
   docker-compose down
   docker volume rm auth_gradle_cache
   docker-compose up --build
   ```
3. Проверьте proxy настройки (если используется корпоративный proxy)

### Проблема: Приложение стартует слишком долго

**Решение:**
1. Увеличьте `start_period` в healthcheck:
   ```yaml
   healthcheck:
     start_period: 120s  # Увеличено до 2 минут
   ```
2. Проверьте логи для выявления bottleneck:
   ```bash
   docker-compose logs -f auth-app
   ```
3. Оптимизируйте зависимости (уберите неиспользуемые)

---

## Адаптация для других сервисов

Данная конфигурация легко адаптируется для других микросервисов проекта:

### 1. Измените названия сервисов

```yaml
services:
  postgres-dev:
    container_name: user-postgres-dev  # Было: auth-postgres-dev

  user-app:                            # Было: auth-app
    container_name: user-app-dev       # Было: auth-app-dev
```

### 2. Обновите переменные окружения

```env
# PostgreSQL Configuration
POSTGRES_DB=user_dev              # Было: auth_dev
POSTGRES_USER=user_user           # Было: auth_user
POSTGRES_PASSWORD=dev_password

# Application Ports
APP_PORT=8082                     # Другой порт для избежания конфликтов
LIVERELOAD_PORT=35730             # Было: 35729
```

### 3. Обновите context path в Spring Boot

```yaml
# application-dev.yml
server:
  port: 8082
  servlet:
    context-path: /user  # Было: /auth
```

### 4. Обновите healthcheck endpoint

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8082/user/actuator/health"]
```

### 5. Обновите network name (опционально)

```yaml
networks:
  user-network:  # Было: auth-network
    driver: bridge
```

---

## Заключение

Данная Docker конфигурация обеспечивает:

✅ **Изолированную среду разработки** - каждый сервис работает в своем контейнере
✅ **Hot-reload** - автоматическая перезагрузка при изменении кода
✅ **Персистентность данных** - данные БД сохраняются между перезапусками
✅ **Healthchecks** - автоматическая проверка состояния сервисов
✅ **Масштабируемость** - легко добавить новые сервисы
✅ **Безопасность** - секреты управляются через переменные окружения

**Рекомендуемый workflow:**

1. Создайте `.env` файл из `.env.dev`
2. Запустите `docker-compose up` для полного стека
3. Разрабатывайте с автоматической перезагрузкой
4. Используйте `docker-compose logs -f` для мониторинга
5. Создавайте отдельные конфигурации для production

**Полезные ссылки:**

- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Spring Boot DevTools](https://docs.spring.io/spring-boot/reference/using/devtools.html)
- [PostgreSQL Docker Hub](https://hub.docker.com/_/postgres)
- [Eclipse Temurin Docker Hub](https://hub.docker.com/_/eclipse-temurin)

---

**Версия документации:** 1.0
**Последнее обновление:** 2025-11-30
**Совместимость:**
- Docker Compose v2.x
- PostgreSQL latest
- Eclipse Temurin JDK 24
- Gradle 8.14.2
- Spring Boot 3.5.4

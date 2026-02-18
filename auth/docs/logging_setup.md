# Настройка логирования для микросервисов проекта

## Содержание

1. [Обзор](#обзор)
2. [Архитектура логирования](#архитектура-логирования)
3. [Конфигурация Logback](#конфигурация-logback)
4. [Уровни логирования](#уровни-логирования)
5. [HTTP Request/Response логирование](#http-requestresponse-логирование)
6. [Маскирование чувствительных данных](#маскирование-чувствительных-данных)
7. [Логирование в коде](#логирование-в-коде)
8. [Настройка для разных окружений](#настройка-для-разных-окружений)
9. [Best Practices](#best-practices)
10. [Примеры использования](#примеры-использования)
11. [Troubleshooting](#troubleshooting)
12. [Адаптация для других сервисов](#адаптация-для-других-сервисов)

---

## Обзор

Система логирования проекта построена на основе **SLF4J + Logback** и обеспечивает:

✅ **Структурированное логирование** - HTTP запросы/ответы, бизнес-логика, ошибки
✅ **Безопасность** - автоматическое маскирование паролей, токенов и других чувствительных данных
✅ **Производительность** - исключение служебных endpoint'ов (actuator, swagger) из логов
✅ **Читаемость** - форматирование JSON, UTF-8 поддержка кириллицы, временные метки
✅ **Гибкость** - разные уровни логирования для development, test, production

### Компоненты системы логирования

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                    │
│  ┌────────────┐  ┌─────────────┐  ┌──────────────────┐ │
│  │ Controllers│  │  Services   │  │  Exception       │ │
│  │            │  │             │  │  Handler         │ │
│  │ @Slf4j     │  │  @Slf4j     │  │                  │ │
│  │ log.info() │  │  log.debug()│  │  log.warn/error()│ │
│  └────────────┘  └─────────────┘  └──────────────────┘ │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│              RequestResponseLoggingFilter               │
│  • Логирует все HTTP запросы и ответы                  │
│  • Измеряет время выполнения (duration)                │
│  • Форматирует JSON для читаемости                     │
│  • Применяет SensitiveDataMasker                       │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                SensitiveDataMasker                      │
│  • Маскирует password, token, accessToken, refreshToken│
│  • Маскирует Authorization и Cookie заголовки          │
│  • Частичное маскирование токенов (****...****...)     │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                   Logback (SLF4J)                       │
│  • Форматирование сообщений                            │
│  • UTF-8 encoding (поддержка кириллицы)                │
│  • Уровни логирования (DEBUG, INFO, WARN, ERROR)       │
│  • Консольный вывод (CONSOLE appender)                 │
└─────────────────────────────────────────────────────────┘
                           ↓
                    Console Output
```

---

## Архитектура логирования

### Технологический стек

- **Logging Framework**: SLF4J (Simple Logging Facade for Java)
- **Logging Implementation**: Logback (встроен в Spring Boot)
- **Lombok**: `@Slf4j` аннотация для автоматического создания logger'а
- **Spring Boot**: `spring-boot-starter-logging` (включен по умолчанию)

### Основные файлы

```
src/
├── main/
│   ├── java/pyc/lopatuxin/auth/
│   │   ├── config/
│   │   │   └── RequestResponseLoggingFilter.java  # HTTP логирование
│   │   ├── util/
│   │   │   └── SensitiveDataMasker.java          # Маскирование данных
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java        # Логирование ошибок
│   │   └── service/
│   │       └── *.java                             # Бизнес-логика (использует @Slf4j)
│   └── resources/
│       ├── logback-spring.xml                     # Основная конфигурация Logback
│       ├── application.yml                        # Минимальная конфигурация
│       └── application-dev.yml                    # Development уровни логирования
└── test/
    └── resources/
        └── application-test.yml                   # Test конфигурация
```

---

## Конфигурация Logback

### logback-spring.xml (основная конфигурация)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Подключаем стандартные настройки Spring Boot -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Консольный аппендер с UTF-8 кодировкой -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <charset>UTF-8</charset>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- Корневой логгер -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>

    <!-- Логирование для приложения -->
    <logger name="pyc.lopatuxin.auth" level="INFO"/>

    <!-- Логирование Spring Security -->
    <logger name="org.springframework.security" level="INFO"/>
</configuration>
```

### Ключевые элементы конфигурации

#### 1. CONSOLE Appender

```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <charset>UTF-8</charset>  <!-- Поддержка кириллицы -->
        <pattern>${CONSOLE_LOG_PATTERN}</pattern>  <!-- Паттерн из Spring Boot defaults -->
    </encoder>
</appender>
```

**Назначение:**
- Вывод логов в консоль (stdout/stderr)
- UTF-8 кодировка для корректного отображения кириллицы
- Использует стандартный паттерн Spring Boot

**Стандартный паттерн Spring Boot:**
```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

**Пример вывода:**
```
2025-11-30 15:23:45.123 [http-nio-8081-exec-1] INFO  p.l.a.service.LoginService - User logged in: user@example.com
```

#### 2. Root Logger

```xml
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
</root>
```

**Назначение:**
- Базовый уровень логирования для всех пакетов: `INFO`
- Применяется ко всем библиотекам (Hibernate, Spring, PostgreSQL driver, и т.д.)
- Можно переопределить для конкретных пакетов через `<logger>` элементы

#### 3. Logger для приложения

```xml
<logger name="pyc.lopatuxin.auth" level="INFO"/>
```

**Назначение:**
- Устанавливает уровень `INFO` для всех классов в пакете `pyc.lopatuxin.auth`
- В production оставляем `INFO`, в development переопределяем на `DEBUG` через `application-dev.yml`

#### 4. Logger для Spring Security

```xml
<logger name="org.springframework.security" level="INFO"/>
```

**Назначение:**
- Управляет логированием Spring Security
- `INFO` - минимальное логирование (только важные события)
- `DEBUG` - детальное логирование аутентификации и авторизации (для отладки)

---

## Уровни логирования

### Иерархия уровней (от наименьшего к наибольшему)

```
TRACE < DEBUG < INFO < WARN < ERROR
```

### Описание уровней

| Уровень | Когда использовать | Примеры |
|---------|-------------------|---------|
| **TRACE** | Детальная трассировка выполнения (редко используется) | Вход/выход из каждого метода, значения всех переменных |
| **DEBUG** | Детальная информация для отладки | Параметры методов, промежуточные результаты, состояние объектов |
| **INFO** | Важные бизнес-события, информационные сообщения | Успешная авторизация, создание сущности, старт процесса |
| **WARN** | Потенциальные проблемы, не критичные ошибки | Валидация не прошла, использование deprecated API, повторная попытка |
| **ERROR** | Ошибки, требующие внимания | Исключения, сбои операций, недоступность внешних систем |

### Настройка уровней через application.yml

#### application-dev.yml (Development)

```yaml
logging:
  level:
    pyc.lopatuxin.auth: DEBUG              # Детальное логирование приложения
    org.springframework.security: INFO     # Минимальное логирование Security
    org.hibernate.SQL: DEBUG               # SQL запросы
    org.hibernate.type.descriptor.sql: TRACE  # Параметры SQL запросов
```

**Результат в development:**
```
2025-11-30 15:23:45.123 [main] DEBUG p.l.a.service.LoginService - Attempting login for email: user@example.com
2025-11-30 15:23:45.156 [main] DEBUG org.hibernate.SQL - select user0_.id as id1_0_, user0_.email as email2_0_ from users user0_ where user0_.email=?
2025-11-30 15:23:45.157 [main] TRACE o.h.t.d.sql.BasicBinder - binding parameter [1] as [VARCHAR] - [user@example.com]
2025-11-30 15:23:45.189 [main] INFO  p.l.a.service.LoginService - User logged in successfully: user@example.com
```

#### application-prod.yml (Production) - рекомендуемая конфигурация

```yaml
logging:
  level:
    pyc.lopatuxin.auth: INFO               # Только важные события
    org.springframework.security: WARN     # Только предупреждения и ошибки
    org.hibernate.SQL: WARN                # Не логируем SQL в production
```

**Результат в production:**
```
2025-11-30 15:23:45.189 [main] INFO  p.l.a.service.LoginService - User logged in successfully: user@example.com
```

#### application-test.yml (Testing)

```yaml
# В тестах обычно минимальное логирование для чистоты вывода
logging:
  level:
    pyc.lopatuxin.auth: WARN               # Только предупреждения и ошибки
    org.springframework: WARN
    org.hibernate: WARN
```

### Переопределение уровня логирования через переменные окружения

```bash
# В docker-compose.yml или .env файле
LOGGING_LEVEL_PYC_LOPATUXIN_AUTH=DEBUG
LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY=DEBUG
```

```yaml
# docker-compose.yml
services:
  auth-app:
    environment:
      LOGGING_LEVEL_PYC_LOPATUXIN_AUTH: ${LOGGING_LEVEL_APP:-INFO}
      LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY: ${LOGGING_LEVEL_SECURITY:-WARN}
```

---

## HTTP Request/Response логирование

### RequestResponseLoggingFilter

Фильтр для автоматического логирования всех HTTP запросов и ответов.

#### Основной класс

```java
@Slf4j
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Пропускаем служебные endpoints
        if (shouldNotLog(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Оборачиваем request/response для чтения тела
        ContentCachingRequestWrapper wrappedRequest =
            new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse =
            new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String timestamp = LocalDateTime.now().format(FORMATTER);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequestAndResponse(wrappedRequest, wrappedResponse, timestamp, duration);
            wrappedResponse.copyBodyToResponse();  // ВАЖНО: копируем body обратно
        }
    }
}
```

#### Исключение служебных endpoints

```java
private boolean shouldNotLog(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.contains("/actuator") ||      // Actuator endpoints
           uri.contains("/favicon.ico") ||   // Favicon
           uri.contains("/swagger") ||       // Swagger UI
           uri.contains("/api-docs");        // OpenAPI docs
}
```

**Причины исключения:**
- Actuator endpoints вызываются очень часто (healthchecks каждые 10-30 секунд)
- Swagger UI генерирует множество запросов при загрузке документации
- Эти endpoints не содержат бизнес-логику, логи засоряют вывод

#### Формат логирования

```java
private void logRequestAndResponse(...) {
    StringBuilder logMessage = new StringBuilder("\n");
    logMessage.append("=".repeat(80)).append("\n");
    logMessage.append("HTTP REQUEST/RESPONSE LOG [").append(timestamp).append("]\n");
    logMessage.append("=".repeat(80)).append("\n");

    // REQUEST
    logMessage.append("REQUEST:\n");
    logMessage.append("  Method: ").append(request.getMethod()).append("\n");
    logMessage.append("  URI: ").append(request.getRequestURI()).append("\n");
    logMessage.append("  Query: ").append(request.getQueryString()).append("\n");
    logMessage.append("  Content-Type: ").append(request.getContentType()).append("\n");

    // Важные заголовки (с маскированием)
    appendHeaders(logMessage, request);

    // Тело запроса (с маскированием)
    if (!maskedRequestBody.isEmpty()) {
        logMessage.append("  Body: ").append(formatJson(maskedRequestBody)).append("\n");
    }

    // RESPONSE
    logMessage.append("RESPONSE:\n");
    logMessage.append("  Status: ").append(response.getStatus()).append("\n");
    logMessage.append("  Content-Type: ").append(response.getContentType()).append("\n");
    logMessage.append("  Duration: ").append(duration).append("ms\n");

    // Тело ответа (с маскированием)
    if (!maskedResponseBody.isEmpty()) {
        logMessage.append("  Body: ").append(formatJson(maskedResponseBody)).append("\n");
    }

    logMessage.append("=".repeat(80));

    log.info(logMessage.toString());
}
```

#### Пример вывода лога

```
================================================================================
HTTP REQUEST/RESPONSE LOG [2025-11-30 15:23:45.123]
================================================================================
REQUEST:
  Method: POST
  URI: /auth/api/login
  Query:
  Content-Type: application/json
  Header [User-Agent]: Mozilla/5.0 (Windows NT 10.0; Win64; x64)
  Body: {
    "email": "user@example.com",
    "password": "***MASKED***"
  }

RESPONSE:
  Status: 200
  Content-Type: application/json
  Duration: 234ms
  Body: {
    "success": true,
    "data": {
      "accessToken": "***MASKED***",
      "tokenType": "Bearer",
      "expiresIn": 900,
      "user": {
        "id": 1,
        "email": "user@example.com"
      }
    }
  }
================================================================================
```

#### Логирование заголовков

```java
private void appendHeaders(StringBuilder logMessage, ContentCachingRequestWrapper request) {
    List<String> importantHeaders = List.of(
        "Authorization",   // JWT токены
        "Cookie",          // Refresh token cookie
        "User-Agent",      // Информация о клиенте
        "X-Forwarded-For"  // IP адрес при использовании proxy
    );

    for (String headerName : importantHeaders) {
        List<String> headerValues = Collections.list(request.getHeaders(headerName));
        if (!headerValues.isEmpty()) {
            String headerValue = String.join(", ", headerValues);
            // Маскируем Authorization и Cookie
            String maskedValue = SensitiveDataMasker.maskSensitiveHeader(
                headerName,
                headerValue
            );
            logMessage.append("  Header [")
                .append(headerName)
                .append("]: ")
                .append(maskedValue)
                .append("\n");
        }
    }
}
```

**Пример вывода заголовков:**
```
Header [Authorization]: Bearer eyJh...***MASKED***...kpXQ
Header [Cookie]: refreshToken=RTk5...***MASKED***...mN2M; JSESSIONID=ABC123
Header [User-Agent]: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36
Header [X-Forwarded-For]: 192.168.1.100
```

#### Форматирование JSON

```java
private String formatJson(String json) {
    if (json == null || json.trim().isEmpty()) {
        return "";
    }

    try {
        // Простое форматирование для читаемости
        return json.trim()
            .replace(",", ",\n    ")
            .replace("{", "{\n    ")
            .replace("}", "\n  }");
    } catch (Exception _) {
        return json;
    }
}
```

**Было:**
```json
{"success":true,"data":{"accessToken":"eyJh...","tokenType":"Bearer"}}
```

**Стало:**
```json
{
    "success": true,
    "data": {
        "accessToken": "***MASKED***",
        "tokenType": "Bearer"
    }
  }
```

### Регистрация фильтра

```java
@Configuration
public class WebConfig {

    @Bean
    public RequestResponseLoggingFilter requestResponseLoggingFilter() {
        return new RequestResponseLoggingFilter();
    }
}
```

Фильтр автоматически применяется ко всем HTTP запросам благодаря наследованию от `OncePerRequestFilter`.

---

## Маскирование чувствительных данных

### SensitiveDataMasker

Утилитный класс для защиты конфиденциальной информации в логах.

#### Полный код класса

```java
@Slf4j
@UtilityClass
public class SensitiveDataMasker {

    private static final String MASK = "***MASKED***";

    // Чувствительные поля в JSON
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password",
            "accessToken",
            "refreshToken",
            "token"
    );

    // Чувствительные HTTP заголовки
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie"
    );

    // Паттерн для поиска JSON полей: "fieldName":"value"
    private static final Pattern JSON_FIELD_PATTERN = Pattern.compile(
            "\"(\\w+)\"\\s*:\\s*\"([^\"]*)\""
    );

    /**
     * Маскирует чувствительные данные в JSON строке
     */
    public static String maskSensitiveJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        try {
            String maskedJson = json;
            Matcher matcher = JSON_FIELD_PATTERN.matcher(json);

            while (matcher.find()) {
                String fieldName = matcher.group(1);
                String fieldValue = matcher.group(2);

                if (SENSITIVE_FIELDS.contains(fieldName) && !fieldValue.isBlank()) {
                    String originalPattern = "\"" + fieldName + "\"\\s*:\\s*\""
                        + Pattern.quote(fieldValue) + "\"";
                    String replacement = "\"" + fieldName + "\": \"" + MASK + "\"";
                    maskedJson = maskedJson.replaceAll(originalPattern, replacement);
                }
            }

            return maskedJson;
        } catch (Exception e) {
            log.warn("Ошибка при маскировании JSON, возвращаю исходную строку", e);
            return json;
        }
    }

    /**
     * Маскирует чувствительные HTTP заголовки
     */
    public static String maskSensitiveHeader(String headerName, String headerValue) {
        if (headerName == null || headerValue == null) {
            return headerValue;
        }

        String normalizedHeaderName = headerName.toLowerCase().trim();

        if (SENSITIVE_HEADERS.contains(normalizedHeaderName)) {
            return maskTokenValue(headerValue);
        }

        return headerValue;
    }

    /**
     * Маскирует токен, оставляя видимыми первые и последние символы
     */
    private static String maskTokenValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        // Bearer токены
        if (value.toLowerCase().startsWith("bearer ")) {
            String token = value.substring(7);
            return "Bearer " + partiallyMaskToken(token);
        }

        // Cookie заголовки
        if (value.contains("=")) {
            return maskCookieValue(value);
        }

        return partiallyMaskToken(value);
    }

    /**
     * Частично маскирует токен: показывает первые 4 и последние 4 символа
     */
    private static String partiallyMaskToken(String token) {
        if (token == null || token.length() <= 8) {
            return MASK;
        }

        String prefix = token.substring(0, 4);
        String suffix = token.substring(token.length() - 4);
        return prefix + "..." + MASK + "..." + suffix;
    }

    /**
     * Маскирует значения в Cookie заголовке
     */
    private static String maskCookieValue(String cookieHeader) {
        String[] cookies = cookieHeader.split(";");
        StringBuilder maskedCookies = new StringBuilder();

        for (String cookie : cookies) {
            String trimmedCookie = cookie.trim();
            String[] parts = trimmedCookie.split("=", 2);

            if (parts.length == 2) {
                String cookieName = parts[0].trim();
                String cookieValue = parts[1].trim();

                // Маскируем cookie с ключевыми словами token, auth, session
                if (cookieName.toLowerCase().contains("token") ||
                        cookieName.toLowerCase().contains("auth") ||
                        cookieName.toLowerCase().contains("session")) {
                    maskedCookies.append(cookieName)
                        .append("=")
                        .append(partiallyMaskToken(cookieValue));
                } else {
                    maskedCookies.append(trimmedCookie);
                }
            } else {
                maskedCookies.append(trimmedCookie);
            }

            maskedCookies.append("; ");
        }

        if (!maskedCookies.isEmpty()) {
            maskedCookies.setLength(maskedCookies.length() - 2);
        }

        return maskedCookies.toString();
    }
}
```

### Примеры маскирования

#### 1. JSON поля

**Исходный JSON:**
```json
{
  "email": "user@example.com",
  "password": "SuperSecret123!",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "RTk5MjM4N0Y3QjdBNEMzRjk4QTFCMkQzRTZG..."
}
```

**Замаскированный JSON:**
```json
{
  "email": "user@example.com",
  "password": "***MASKED***",
  "accessToken": "***MASKED***",
  "refreshToken": "***MASKED***"
}
```

#### 2. Authorization заголовок

**Исходное значение:**
```
Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

**Замаскированное значение:**
```
Bearer eyJh...***MASKED***...w5c
```

**Объяснение:**
- Показаны первые 4 символа: `eyJh`
- Показаны последние 4 символа: `w5c`
- Середина заменена на `***MASKED***`
- Префикс `Bearer ` сохранен для понимания типа токена

#### 3. Cookie заголовок

**Исходное значение:**
```
refreshToken=RTk5MjM4N0Y3QjdBNEMzRjk4QTFCMkQzRTZGN2M; JSESSIONID=ABC123DEF456; theme=dark
```

**Замаскированное значение:**
```
refreshToken=RTk5...***MASKED***...N2M; JSESSIONID=ABC1...***MASKED***...F456; theme=dark
```

**Объяснение:**
- `refreshToken` содержит "token" → маскируется
- `JSESSIONID` содержит "session" → маскируется
- `theme` не содержит ключевых слов → не маскируется

### Настройка чувствительных полей

#### Добавление новых чувствительных полей

```java
private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password",
        "accessToken",
        "refreshToken",
        "token",
        // Добавить новые поля:
        "apiKey",
        "secretKey",
        "privateKey",
        "creditCard",
        "ssn"
);
```

#### Добавление новых чувствительных заголовков

```java
private static final Set<String> SENSITIVE_HEADERS = Set.of(
        "authorization",
        "cookie",
        // Добавить новые заголовки:
        "x-api-key",
        "x-auth-token",
        "proxy-authorization"
);
```

### Обработка ошибок маскирования

```java
try {
    // ... логика маскирования ...
} catch (Exception e) {
    log.warn("Ошибка при маскировании JSON, возвращаю исходную строку", e);
    return json;  // В случае ошибки возвращаем исходную строку
}
```

**Важно:** Если маскирование не удалось, возвращается оригинальная строка (потенциально с чувствительными данными). В production рекомендуется возвращать пустую строку или error message.

---

## Логирование в коде

### Использование @Slf4j (Lombok)

#### Добавление логгера в класс

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LoginService {

    public void login(String email) {
        log.debug("Attempting login for user: {}", email);

        // ... бизнес-логика ...

        log.info("User logged in successfully: {}", email);
    }
}
```

**Что делает @Slf4j:**
- Автоматически создает приватное поле: `private static final Logger log = LoggerFactory.getLogger(LoginService.class);`
- Не нужно писать boilerplate код
- Logger доступен через переменную `log`

### Уровни логирования в коде

#### 1. log.trace() - Детальная трассировка

```java
@Slf4j
@Service
public class UserService {

    public User findById(Long id) {
        log.trace("Entering findById() with id={}", id);

        User user = userRepository.findById(id).orElse(null);

        log.trace("Exiting findById() with result={}", user);
        return user;
    }
}
```

**Когда использовать:**
- Вход/выход из методов
- Значения всех параметров и переменных
- Подробная трассировка выполнения

**⚠️ Внимание:** `TRACE` логи генерируют огромный объем данных. Используйте только для глубокой отладки конкретных проблем.

#### 2. log.debug() - Отладочная информация

```java
@Slf4j
@Service
public class LoginService {

    public LoginResponse login(LoginRequest request) {
        log.debug("Login attempt for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> {
                log.debug("User not found: {}", request.getEmail());
                return new BadCredentialsException("Invalid credentials");
            });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.debug("Invalid password for user: {}", request.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }

        log.debug("Login successful for user: {}", request.getEmail());
        return generateTokens(user);
    }
}
```

**Когда использовать:**
- Параметры методов
- Промежуточные результаты вычислений
- Условия ветвления (if/else)
- Состояние объектов

**Включение в development:**
```yaml
logging:
  level:
    pyc.lopatuxin.auth: DEBUG
```

#### 3. log.info() - Важные бизнес-события

```java
@Slf4j
@Service
public class UserService {

    @Transactional
    public User createUser(RegisterRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        log.info("User created successfully: id={}, email={}",
            savedUser.getId(), savedUser.getEmail());

        return savedUser;
    }

    @Transactional
    public void deleteUser(Long userId) {
        log.info("Deleting user: id={}", userId);

        userRepository.deleteById(userId);

        log.info("User deleted successfully: id={}", userId);
    }
}
```

**Когда использовать:**
- Успешное завершение важных операций
- Создание/обновление/удаление сущностей
- Начало/окончание бизнес-процессов
- Важные решения в бизнес-логике

**Уровень по умолчанию:** `INFO` - логи видны всегда (dev, test, prod)

#### 4. log.warn() - Предупреждения

```java
@Slf4j
@Service
public class RefreshTokenService {

    public void cleanupExpiredTokens() {
        List<RefreshToken> expiredTokens = refreshTokenRepository
            .findByExpiresAtBefore(LocalDateTime.now());

        if (!expiredTokens.isEmpty()) {
            log.warn("Found {} expired refresh tokens, cleaning up...",
                expiredTokens.size());

            refreshTokenRepository.deleteAll(expiredTokens);

            log.warn("Deleted {} expired refresh tokens", expiredTokens.size());
        }
    }

    public User authenticateToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository
            .findByToken(token)
            .orElse(null);

        if (refreshToken == null) {
            log.warn("Refresh token not found in database: {}",
                token.substring(0, 10) + "...");
            throw new InvalidTokenException("Invalid refresh token");
        }

        if (refreshToken.isExpired()) {
            log.warn("Attempt to use expired refresh token: userId={}, expired at={}",
                refreshToken.getUser().getId(),
                refreshToken.getExpiresAt());
            throw new InvalidTokenException("Refresh token expired");
        }

        return refreshToken.getUser();
    }
}
```

**Когда использовать:**
- Потенциальные проблемы (не критичные)
- Неожиданное поведение системы
- Использование deprecated функционала
- Повторные попытки операций
- Валидация не прошла

#### 5. log.error() - Ошибки

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi<Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseApi.error("Internal server error"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseApi<Object>> handleAuthException(
            AuthenticationException ex) {
        log.error("Authentication failed: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ResponseApi.error(ex.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ResponseApi<Object>> handleDatabaseException(
            DataAccessException ex) {
        log.error("Database error: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseApi.error("Database operation failed"));
    }
}
```

```java
@Slf4j
@Service
public class EmailService {

    public void sendEmail(String to, String subject, String body) {
        try {
            mailSender.send(to, subject, body);
            log.info("Email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            // Можно выбросить исключение или продолжить выполнение
            throw new EmailSendingException("Failed to send email", e);
        }
    }
}
```

**Когда использовать:**
- Исключения и ошибки
- Сбои операций
- Недоступность внешних систем (БД, API, email)
- Критичные проблемы, требующие немедленного внимания

**⚠️ Важно:** При логировании исключений всегда передавайте объект exception в качестве последнего параметра:
```java
log.error("Error message: {}", ex.getMessage(), ex);  // ✅ Правильно - включает stack trace
log.error("Error message: {}", ex.getMessage());      // ❌ Неправильно - без stack trace
```

### Параметризованные сообщения

#### ✅ Правильно - использование плейсхолдеров {}

```java
String email = "user@example.com";
Long userId = 123L;

// Плейсхолдеры {} заменяются на значения параметров
log.info("User logged in: email={}, userId={}", email, userId);
log.debug("Processing request for user {} with role {}", userId, role);
log.warn("Failed login attempt for user: {}", email);
```

**Преимущества:**
- Эффективность: строка не конкатенируется, если уровень логирования отключен
- Читаемость: четкое разделение шаблона и параметров
- Безопасность: автоматическое экранирование спецсимволов

#### ❌ Неправильно - конкатенация строк

```java
// Неэффективно: строка конкатенируется даже если DEBUG отключен
log.debug("User logged in: " + email + ", userId=" + userId);

// Создает мусор в памяти
log.info("Processing user with id: " + user.getId() + " and email: " + user.getEmail());
```

### Условное логирование

#### Для дорогих операций

```java
if (log.isDebugEnabled()) {
    // Эта строка выполнится только если DEBUG включен
    String expensiveDebugInfo = generateDetailedReport(user);
    log.debug("Detailed user report: {}", expensiveDebugInfo);
}

if (log.isTraceEnabled()) {
    // Сериализация может быть дорогой операцией
    log.trace("Full request: {}", objectMapper.writeValueAsString(request));
}
```

**Когда использовать:**
- Сериализация объектов в JSON/XML
- Генерация отчетов
- Вызов методов только для логирования
- Циклы с большим количеством итераций

### Логирование коллекций

```java
@Slf4j
@Service
public class UserService {

    public List<User> findActiveUsers() {
        List<User> users = userRepository.findByIsActiveTrue();

        // ✅ Правильно - логируем количество
        log.info("Found {} active users", users.size());

        // ❌ Неправильно - логируем весь список (может быть огромным)
        // log.info("Active users: {}", users);

        if (log.isDebugEnabled()) {
            // В DEBUG режиме можем показать больше деталей
            log.debug("Active user IDs: {}",
                users.stream()
                    .map(User::getId)
                    .collect(Collectors.toList()));
        }

        return users;
    }
}
```

### Структурированное логирование

```java
@Slf4j
@Service
public class OrderService {

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating order: userId={}, itemCount={}, totalAmount={}",
            request.getUserId(),
            request.getItems().size(),
            request.getTotalAmount()
        );

        try {
            Order order = processOrder(request);

            log.info("Order created successfully: orderId={}, userId={}, status={}, amount={}",
                order.getId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount()
            );

            return order;

        } catch (InsufficientStockException e) {
            log.warn("Order creation failed due to insufficient stock: userId={}, items={}",
                request.getUserId(),
                request.getItems().stream()
                    .map(Item::getProductId)
                    .collect(Collectors.toList())
            );
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating order: userId={}, error={}",
                request.getUserId(),
                e.getMessage(),
                e
            );
            throw new OrderCreationException("Failed to create order", e);
        }
    }
}
```

---

## Настройка для разных окружений

### Development (application-dev.yml)

```yaml
spring:
  application:
    name: auth

logging:
  level:
    # Подробное логирование приложения
    pyc.lopatuxin.auth: DEBUG

    # Логирование Spring Security (полезно для отладки аутентификации)
    org.springframework.security: DEBUG

    # SQL запросы и параметры
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql: TRACE

    # Spring Framework
    org.springframework.web: DEBUG
    org.springframework.jdbc: DEBUG

  # Дополнительные настройки для development
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

  file:
    # Опционально: запись в файл для development
    name: logs/auth-dev.log
    max-size: 10MB
    max-history: 7
```

**Что логируется:**
- ✅ Все DEBUG сообщения приложения
- ✅ SQL запросы с параметрами
- ✅ Spring Security аутентификация/авторизация
- ✅ HTTP запросы/ответы (через RequestResponseLoggingFilter)
- ✅ Детали работы Spring Framework

**Пример вывода:**
```
2025-11-30 15:23:45.123 [http-nio-8081-exec-1] DEBUG p.l.a.service.LoginService - Attempting login for email: user@example.com
2025-11-30 15:23:45.156 [http-nio-8081-exec-1] DEBUG org.hibernate.SQL - select user0_.id as id1_0_ from users user0_ where user0_.email=?
2025-11-30 15:23:45.157 [http-nio-8081-exec-1] TRACE o.h.t.d.sql.BasicBinder - binding parameter [1] as [VARCHAR] - [user@example.com]
2025-11-30 15:23:45.189 [http-nio-8081-exec-1] DEBUG o.s.security.web.FilterChainProxy - /api/login at position 3 of 12 in additional filter chain
2025-11-30 15:23:45.234 [http-nio-8081-exec-1] INFO  p.l.a.service.LoginService - User logged in successfully: user@example.com
```

### Production (application-prod.yml)

```yaml
spring:
  application:
    name: auth

logging:
  level:
    # Только важные события
    pyc.lopatuxin.auth: INFO

    # Минимальное логирование Spring Security
    org.springframework.security: WARN

    # Не логируем SQL в production
    org.hibernate.SQL: WARN

    # Минимальное логирование для всех библиотек
    root: WARN

  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

  file:
    # Обязательно: запись в файл для production
    name: /var/log/auth/auth.log
    max-size: 100MB
    max-history: 30  # Хранить логи за 30 дней
    total-size-cap: 1GB  # Максимальный размер всех файлов логов
```

**Что логируется:**
- ✅ INFO, WARN, ERROR сообщения приложения
- ✅ HTTP запросы/ответы с ошибками (статус >= 400)
- ✅ Исключения и ошибки
- ❌ DEBUG логи отключены
- ❌ SQL запросы не логируются

**Пример вывода:**
```
2025-11-30 15:23:45.234 [http-nio-8081-exec-1] INFO  p.l.a.service.LoginService - User logged in successfully: user@example.com
2025-11-30 15:24:12.567 [http-nio-8081-exec-2] WARN  p.l.a.e.GlobalExceptionHandler - Authentication failed: Invalid credentials
2025-11-30 15:25:33.890 [http-nio-8081-exec-3] ERROR p.l.a.service.EmailService - Failed to send email: Connection refused
```

### Testing (application-test.yml)

```yaml
spring:
  application:
    name: auth-test

logging:
  level:
    # Минимальное логирование в тестах
    pyc.lopatuxin.auth: WARN
    org.springframework: WARN
    org.hibernate: WARN

    # Полностью отключаем некоторые шумные логи
    org.testcontainers: WARN
    com.github.dockerjava: WARN

  pattern:
    console: "%d{HH:mm:ss.SSS} %-5level %logger{20} - %msg%n"  # Короткий формат для тестов
```

**Что логируется:**
- ✅ Только WARN и ERROR из тестов
- ✅ Чистый вывод для результатов тестов
- ❌ Нет DEBUG/INFO шума от Spring/Hibernate/Testcontainers

**Пример вывода:**
```
15:23:45.123 INFO  LoginServiceTest - Starting test: shouldLoginSuccessfully
15:23:45.890 INFO  LoginServiceTest - Test passed: shouldLoginSuccessfully
15:23:46.123 WARN  LoginServiceTest - Expected exception: Invalid credentials
```

### Настройка через переменные окружения

#### В Docker Compose

```yaml
services:
  auth-app:
    environment:
      # Профиль Spring Boot
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}

      # Уровни логирования через ENV переменные
      LOGGING_LEVEL_PYC_LOPATUXIN_AUTH: ${LOGGING_LEVEL_APP:-INFO}
      LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY: ${LOGGING_LEVEL_SECURITY:-WARN}
      LOGGING_LEVEL_ORG_HIBERNATE_SQL: ${LOGGING_LEVEL_SQL:-WARN}

      # Путь к файлу логов
      LOGGING_FILE_NAME: ${LOGGING_FILE:-/var/log/auth/auth.log}

      # Максимальный размер файла логов
      LOGGING_FILE_MAX_SIZE: ${LOGGING_MAX_SIZE:-100MB}
```

#### В .env файлах

**.env.dev:**
```env
SPRING_PROFILES_ACTIVE=dev
LOGGING_LEVEL_APP=DEBUG
LOGGING_LEVEL_SECURITY=DEBUG
LOGGING_LEVEL_SQL=DEBUG
LOGGING_FILE=logs/auth-dev.log
LOGGING_MAX_SIZE=10MB
```

**.env.prod:**
```env
SPRING_PROFILES_ACTIVE=prod
LOGGING_LEVEL_APP=INFO
LOGGING_LEVEL_SECURITY=WARN
LOGGING_LEVEL_SQL=WARN
LOGGING_FILE=/var/log/auth/auth.log
LOGGING_MAX_SIZE=100MB
```

#### Переопределение через командную строку

```bash
# Запуск с переопределением уровня логирования
java -jar auth.jar \
  --logging.level.pyc.lopatuxin.auth=DEBUG \
  --logging.level.org.springframework.security=DEBUG

# Через системные свойства
java -Dlogging.level.pyc.lopatuxin.auth=DEBUG \
     -Dlogging.file.name=/tmp/auth.log \
     -jar auth.jar
```

### Логирование в файл (production)

```yaml
logging:
  file:
    # Путь к файлу логов
    name: /var/log/auth/auth.log

    # Максимальный размер одного файла
    max-size: 100MB

    # Количество ротируемых файлов
    max-history: 30

    # Максимальный общий размер всех логов
    total-size-cap: 1GB

  # Паттерн для файла (можно отличаться от консоли)
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

**Ротация логов:**
```
/var/log/auth/
├── auth.log              # Текущий файл
├── auth.log.2025-11-30   # Вчерашний
├── auth.log.2025-11-29
├── auth.log.2025-11-28
...
└── auth.log.2025-11-01   # Старый (будет удален через 30 дней)
```

---

## Best Practices

### 1. Безопасность логирования

#### ✅ DO (Рекомендуется)

```java
// Всегда маскируйте чувствительные данные
log.info("User logged in: email={}",
    SensitiveDataMasker.maskEmail(user.getEmail()));

// Не логируйте пароли
log.debug("Login request received for user: {}", request.getEmail());
// НЕ: log.debug("Login request: {}", request);  // может содержать пароль

// Используйте RequestResponseLoggingFilter для HTTP логов
// Фильтр автоматически маскирует чувствительные данные

// Логируйте только ID токенов, не сами токены
log.info("Refresh token created: tokenId={}", refreshToken.getId());
```

#### ❌ DON'T (Не рекомендуется)

```java
// НИКОГДА не логируйте пароли
log.debug("Password: {}", password);  // ❌ ОПАСНО!

// НИКОГДА не логируйте полные токены
log.debug("Access token: {}", accessToken);  // ❌ ОПАСНО!

// Не логируйте сырые request/response без маскирования
log.info("Request body: {}", requestBody);  // ❌ Может содержать секреты

// Не логируйте персональные данные без необходимости
log.info("User SSN: {}", user.getSsn());  // ❌ Нарушение privacy
```

### 2. Производительность

#### ✅ DO (Рекомендуется)

```java
// Используйте параметризованные сообщения
log.debug("User {} performed action {}", userId, action);

// Проверяйте уровень для дорогих операций
if (log.isDebugEnabled()) {
    String expensiveJson = objectMapper.writeValueAsString(largeObject);
    log.debug("Object state: {}", expensiveJson);
}

// Логируйте размер коллекций, не содержимое
log.info("Found {} users matching criteria", users.size());

// Используйте асинхронное логирование для high-load приложений
// (настраивается в logback-spring.xml через AsyncAppender)
```

#### ❌ DON'T (Не рекомендуется)

```java
// Не используйте конкатенацию строк
log.debug("User " + userId + " performed " + action);  // ❌ Неэффективно

// Не вызывайте дорогие методы в параметрах лога
log.debug("State: {}", generateExpensiveReport());  // ❌ Выполняется всегда

// Не логируйте большие объекты целиком
log.debug("All users: {}", allUsersList);  // ❌ Может быть миллион записей

// Не логируйте в циклах без ограничений
for (User user : millionUsers) {
    log.debug("Processing user: {}", user);  // ❌ Миллион логов!
}
```

### 3. Читаемость

#### ✅ DO (Рекомендуется)

```java
// Используйте понятные, структурированные сообщения
log.info("Order created: orderId={}, userId={}, amount={}, status={}",
    order.getId(),
    order.getUserId(),
    order.getAmount(),
    order.getStatus()
);

// Добавляйте контекст в логи
log.warn("Payment failed: orderId={}, reason={}, retryCount={}",
    orderId,
    failureReason,
    retryCount
);

// Используйте единый стиль логирования в проекте
// Например: "Entity operation: entityType={}, entityId={}, operation={}"
log.info("User operation: userId={}, operation=DELETE", userId);
log.info("Order operation: orderId={}, operation=CREATE", orderId);
```

#### ❌ DON'T (Не рекомендуется)

```java
// Не пишите непонятные сообщения
log.info("OK");  // ❌ Что OK?
log.debug("Here");  // ❌ Где "here"?
log.error("Error!");  // ❌ Какая ошибка?

// Не используйте разный стиль в разных местах
log.info("User: {} logged in", email);  // Стиль 1
log.info("Logged in user={}", email);   // Стиль 2
log.info("Login successful for " + email);  // Стиль 3
```

### 4. Исключения

#### ✅ DO (Рекомендуется)

```java
// Всегда логируйте stack trace для ERROR
try {
    processPayment(order);
} catch (PaymentException e) {
    log.error("Payment processing failed: orderId={}, error={}",
        order.getId(),
        e.getMessage(),
        e  // ← ВАЖНО: передаем exception последним параметром
    );
    throw e;
}

// Логируйте на правильном уровне в зависимости от серьезности
try {
    sendNotification(user);
} catch (NotificationException e) {
    // Email не отправился - не критично, пользователь не пострадал
    log.warn("Failed to send notification: userId={}, reason={}",
        user.getId(),
        e.getMessage()
    );
    // Продолжаем работу
}
```

#### ❌ DON'T (Не рекомендуется)

```java
// Не теряйте stack trace
try {
    processPayment(order);
} catch (PaymentException e) {
    log.error("Payment failed: {}", e.getMessage());  // ❌ Нет stack trace!
    throw e;
}

// Не логируйте и не пробрасывайте одновременно (двойное логирование)
try {
    someOperation();
} catch (Exception e) {
    log.error("Error in someOperation", e);
    throw e;  // ❌ Будет залогировано еще раз выше по стеку
}

// Не глотайте исключения без логирования
try {
    riskyOperation();
} catch (Exception e) {
    // ❌ Молча игнорируем ошибку - очень плохо!
}
```

### 5. Уровни логирования

#### Правильный выбор уровня

```java
@Slf4j
@Service
public class OrderService {

    public Order createOrder(CreateOrderRequest request) {
        // DEBUG: входные параметры
        log.debug("Creating order: userId={}, itemCount={}",
            request.getUserId(),
            request.getItems().size()
        );

        // DEBUG: промежуточные шаги
        log.debug("Validating order items...");
        validateItems(request.getItems());

        log.debug("Calculating total amount...");
        BigDecimal total = calculateTotal(request);

        // INFO: важное бизнес-событие
        Order order = saveOrder(request, total);
        log.info("Order created successfully: orderId={}, userId={}, amount={}",
            order.getId(),
            order.getUserId(),
            order.getTotalAmount()
        );

        try {
            // DEBUG: отправка уведомления
            log.debug("Sending order confirmation email: orderId={}", order.getId());
            emailService.sendOrderConfirmation(order);
        } catch (EmailException e) {
            // WARN: email не отправился, но заказ создан
            log.warn("Failed to send order confirmation: orderId={}, error={}",
                order.getId(),
                e.getMessage()
            );
        }

        return order;
    }

    private void validateItems(List<OrderItem> items) {
        for (OrderItem item : items) {
            if (item.getQuantity() <= 0) {
                // WARN: некорректные данные от пользователя
                log.warn("Invalid item quantity: itemId={}, quantity={}",
                    item.getProductId(),
                    item.getQuantity()
                );
                throw new ValidationException("Invalid quantity");
            }

            if (!inventoryService.isAvailable(item.getProductId(), item.getQuantity())) {
                // WARN: товара нет в наличии
                log.warn("Insufficient stock: productId={}, requested={}, available={}",
                    item.getProductId(),
                    item.getQuantity(),
                    inventoryService.getAvailableQuantity(item.getProductId())
                );
                throw new InsufficientStockException("Product out of stock");
            }
        }
    }
}
```

### 6. Контекст и корреляция

#### Добавление correlation ID (для трассировки запросов)

```java
@Slf4j
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        // Добавляем в MDC для включения во все логи
        MDC.put("correlationId", correlationId);

        // Добавляем в response
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

**Обновленный logback-spring.xml:**
```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n</pattern>
```

**Результат:**
```
2025-11-30 15:23:45.123 [http-nio-8081-exec-1] [a1b2c3d4-e5f6-7890] INFO  p.l.a.service.OrderService - Order created: orderId=123
2025-11-30 15:23:45.456 [http-nio-8081-exec-1] [a1b2c3d4-e5f6-7890] DEBUG p.l.a.service.EmailService - Sending email for order: 123
2025-11-30 15:23:45.789 [http-nio-8081-exec-1] [a1b2c3d4-e5f6-7890] INFO  p.l.a.service.OrderService - Order processing completed
```

**Преимущество:** Все логи одного запроса имеют одинаковый `correlationId` → легко отследить весь flow запроса.

---

## Примеры использования

### Пример 1: Сервис с полным циклом логирования

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public User registerUser(RegisterRequest request) {
        log.info("Starting user registration: email={}", request.getEmail());

        // Валидация
        log.debug("Validating registration request: email={}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException("Email already registered");
        }

        // Создание пользователя
        log.debug("Creating user entity: email={}", request.getEmail());
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(false);  // Требуется подтверждение email

        User savedUser = userRepository.save(user);
        log.info("User entity created: userId={}, email={}",
            savedUser.getId(),
            savedUser.getEmail()
        );

        // Отправка email подтверждения
        try {
            log.debug("Sending verification email: userId={}", savedUser.getId());
            String verificationToken = generateVerificationToken(savedUser);
            emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);

            log.info("Verification email sent: userId={}, email={}",
                savedUser.getId(),
                savedUser.getEmail()
            );

        } catch (EmailException e) {
            log.error("Failed to send verification email: userId={}, email={}, error={}",
                savedUser.getId(),
                savedUser.getEmail(),
                e.getMessage(),
                e
            );
            // Можем откатить транзакцию или оставить пользователя неактивированным
            // В данном случае оставляем - пользователь сможет запросить повторную отправку
        }

        log.info("User registration completed: userId={}, email={}",
            savedUser.getId(),
            savedUser.getEmail()
        );

        return savedUser;
    }

    private String generateVerificationToken(User user) {
        log.debug("Generating verification token: userId={}", user.getId());
        // ... генерация токена ...
        return token;
    }
}
```

**Вывод в development (DEBUG):**
```
2025-11-30 15:23:45.100 INFO  p.l.a.s.UserRegistrationService - Starting user registration: email=user@example.com
2025-11-30 15:23:45.101 DEBUG p.l.a.s.UserRegistrationService - Validating registration request: email=user@example.com
2025-11-30 15:23:45.150 DEBUG p.l.a.s.UserRegistrationService - Creating user entity: email=user@example.com
2025-11-30 15:23:45.200 INFO  p.l.a.s.UserRegistrationService - User entity created: userId=123, email=user@example.com
2025-11-30 15:23:45.201 DEBUG p.l.a.s.UserRegistrationService - Sending verification email: userId=123
2025-11-30 15:23:45.202 DEBUG p.l.a.s.UserRegistrationService - Generating verification token: userId=123
2025-11-30 15:23:45.350 INFO  p.l.a.s.UserRegistrationService - Verification email sent: userId=123, email=user@example.com
2025-11-30 15:23:45.351 INFO  p.l.a.s.UserRegistrationService - User registration completed: userId=123, email=user@example.com
```

**Вывод в production (INFO):**
```
2025-11-30 15:23:45.100 INFO  p.l.a.s.UserRegistrationService - Starting user registration: email=user@example.com
2025-11-30 15:23:45.200 INFO  p.l.a.s.UserRegistrationService - User entity created: userId=123, email=user@example.com
2025-11-30 15:23:45.350 INFO  p.l.a.s.UserRegistrationService - Verification email sent: userId=123, email=user@example.com
2025-11-30 15:23:45.351 INFO  p.l.a.s.UserRegistrationService - User registration completed: userId=123, email=user@example.com
```

### Пример 2: Global Exception Handler

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ResponseApi<Object>> handleValidationException(
            ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(ResponseApi.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApi<Object>> handleValidationException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation errors: {}", errors);
        return ResponseEntity.badRequest()
            .body(ResponseApi.error("Validation failed", errors));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ResponseApi<Object>> handleUserAlreadyExistsException(
            UserAlreadyExistsException ex) {
        log.warn("User already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ResponseApi.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseApi<Object>> handleAccessDeniedException(
            AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ResponseApi.error(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseApi<Object>> handleAuthenticationException(
            AuthenticationException ex) {
        log.error("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ResponseApi.error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi<Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResponseApi.error("Internal server error"));
    }
}
```

---

## Troubleshooting

### Проблема: Логи не отображаются

**Симптомы:**
Ожидаемые логи не появляются в консоли

**Решение:**

1. Проверьте уровень логирования:
```yaml
logging:
  level:
    pyc.lopatuxin.auth: DEBUG  # Убедитесь, что уровень достаточно низкий
```

2. Проверьте, что класс использует `@Slf4j`:
```java
@Slf4j  // ← Должна быть эта аннотация
@Service
public class MyService {
    public void myMethod() {
        log.info("This is a log message");
    }
}
```

3. Проверьте, что logback-spring.xml корректен:
```bash
# Проверка синтаксиса XML
cat src/main/resources/logback-spring.xml
```

4. Включите debug режим Logback:
```yaml
# application-dev.yml
logging:
  level:
    ROOT: DEBUG
debug: true  # Показывает конфигурацию Logback при старте
```

### Проблема: Кириллица отображается как ???

**Симптомы:**
```
User logged in: email=пользователь@example.com → User logged in: email=????????????@example.com
```

**Решение:**

1. Убедитесь, что в logback-spring.xml указана UTF-8 кодировка:
```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <charset>UTF-8</charset>  <!-- ← ВАЖНО -->
        <pattern>${CONSOLE_LOG_PATTERN}</pattern>
    </encoder>
</appender>
```

2. Для тестов добавьте в build.gradle.kts:
```kotlin
tasks.withType<Test> {
    useJUnitPlatform()

    systemProperty("file.encoding", "UTF-8")
    systemProperty("sun.stdout.encoding", "UTF-8")
    systemProperty("sun.stderr.encoding", "UTF-8")

    jvmArgs("-Dfile.encoding=UTF-8", "-Dconsole.encoding=UTF-8")
}
```

3. В Docker добавьте переменные окружения:
```yaml
environment:
  LANG: ru_RU.UTF-8
  LC_ALL: ru_RU.UTF-8
  JAVA_TOOL_OPTIONS: "-Dfile.encoding=UTF-8"
```

### Проблема: Слишком много логов в production

**Симптомы:**
Диск заполняется логами, производительность снижается

**Решение:**

1. Повысьте уровень логирования:
```yaml
logging:
  level:
    pyc.lopatuxin.auth: INFO  # Было: DEBUG
    org.springframework: WARN  # Было: INFO
    org.hibernate: WARN  # Было: INFO
```

2. Настройте ротацию логов:
```yaml
logging:
  file:
    max-size: 100MB
    max-history: 7  # Хранить только за последнюю неделю
    total-size-cap: 500MB  # Максимум 500MB всех логов
```

3. Исключите служебные endpoints из RequestResponseLoggingFilter:
```java
private boolean shouldNotLog(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.contains("/actuator") ||
           uri.contains("/health") ||
           uri.contains("/metrics");
}
```

4. Используйте асинхронное логирование:
```xml
<!-- logback-spring.xml -->
<appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="CONSOLE" />
    <queueSize>512</queueSize>
    <discardingThreshold>0</discardingThreshold>
</appender>

<root level="INFO">
    <appender-ref ref="ASYNC"/>
</root>
```

### Проблема: Нет stack trace у исключений

**Симптомы:**
```
2025-11-30 15:23:45.123 ERROR p.l.a.service.PaymentService - Payment failed: Connection refused
```
Но нет информации о том, где именно произошла ошибка

**Решение:**

Всегда передавайте exception последним параметром в log.error():

```java
// ❌ Неправильно - нет stack trace
log.error("Payment failed: {}", e.getMessage());

// ✅ Правильно - с stack trace
log.error("Payment failed: {}", e.getMessage(), e);
//                                              ↑ exception последним параметром
```

**Результат:**
```
2025-11-30 15:23:45.123 ERROR p.l.a.service.PaymentService - Payment failed: Connection refused
java.net.ConnectException: Connection refused
    at java.base/sun.nio.ch.Net.connect0(Native Method)
    at java.base/sun.nio.ch.Net.connect(Net.java:579)
    at pyc.lopatuxin.auth.service.PaymentService.processPayment(PaymentService.java:45)
    at pyc.lopatuxin.auth.controller.PaymentController.createPayment(PaymentController.java:23)
    ...
```

### Проблема: Чувствительные данные в логах

**Симптомы:**
Пароли, токены или другие секреты видны в логах

**Решение:**

1. Убедитесь, что RequestResponseLoggingFilter использует SensitiveDataMasker:
```java
String maskedRequestBody = SensitiveDataMasker.maskSensitiveJson(requestBody);
String maskedResponseBody = SensitiveDataMasker.maskSensitiveJson(responseBody);
```

2. Добавьте новые чувствительные поля в SensitiveDataMasker:
```java
private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password",
        "accessToken",
        "refreshToken",
        "token",
        "apiKey",  // ← Добавить новое поле
        "secret"   // ← Добавить новое поле
);
```

3. Не логируйте целые объекты:
```java
// ❌ Плохо - может содержать password
log.debug("Request: {}", request);

// ✅ Хорошо - логируем только безопасные поля
log.debug("Request: email={}, username={}", request.getEmail(), request.getUsername());
```

---

## Адаптация для других сервисов

Данная конфигурация легко адаптируется для других микросервисов:

### 1. Обновите package name в logback-spring.xml

```xml
<!-- Было: -->
<logger name="pyc.lopatuxin.auth" level="INFO"/>

<!-- Стало (для user-service): -->
<logger name="pyc.lopatuxin.user" level="INFO"/>

<!-- Стало (для order-service): -->
<logger name="pyc.lopatuxin.order" level="INFO"/>
```

### 2. Обновите в application-dev.yml

```yaml
# Было:
logging:
  level:
    pyc.lopatuxin.auth: DEBUG

# Стало (для user-service):
logging:
  level:
    pyc.lopatuxin.user: DEBUG

# Стало (для order-service):
logging:
  level:
    pyc.lopatuxin.order: DEBUG
```

### 3. Скопируйте утилитные классы

```bash
# RequestResponseLoggingFilter
cp src/main/java/pyc/lopatuxin/auth/config/RequestResponseLoggingFilter.java \
   ../user-service/src/main/java/pyc/lopatuxin/user/config/

# SensitiveDataMasker
cp src/main/java/pyc/lopatuxin/auth/util/SensitiveDataMasker.java \
   ../user-service/src/main/java/pyc/lopatuxin/user/util/
```

Обновите package names в скопированных файлах.

### 4. Добавьте специфичные для сервиса поля в SensitiveDataMasker

```java
// Для payment-service
private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password",
        "accessToken",
        "refreshToken",
        "token",
        "creditCardNumber",  // ← Специфично для payment
        "cvv",               // ← Специфично для payment
        "cardholderName"     // ← Специфично для payment
);
```

### 5. Настройте логирование файлов

```yaml
# user-service
logging:
  file:
    name: /var/log/user-service/user.log

# order-service
logging:
  file:
    name: /var/log/order-service/order.log

# payment-service
logging:
  file:
    name: /var/log/payment-service/payment.log
```

---

## Заключение

Данная система логирования обеспечивает:

✅ **Комплексное логирование** - HTTP запросы, бизнес-логика, ошибки
✅ **Безопасность** - автоматическое маскирование паролей, токенов и чувствительных данных
✅ **Гибкость** - разные уровни для dev/test/prod окружений
✅ **Производительность** - исключение служебных endpoints, асинхронное логирование
✅ **Читаемость** - структурированные логи, UTF-8, форматирование JSON
✅ **Отладка** - correlation ID для трассировки запросов

**Рекомендуемый workflow:**

1. Development: `DEBUG` уровень для детальной отладки
2. Testing: `WARN` уровень для чистоты вывода тестов
3. Production: `INFO` уровень + запись в файлы с ротацией

**Ключевые компоненты:**

- `logback-spring.xml` - основная конфигурация Logback
- `application-{profile}.yml` - уровни логирования для разных окружений
- `RequestResponseLoggingFilter` - автоматическое HTTP логирование
- `SensitiveDataMasker` - защита чувствительных данных
- `@Slf4j` - аннотация Lombok для логирования в коде

---

**Версия документации:** 1.0
**Последнее обновление:** 2025-11-30
**Совместимость:**
- Spring Boot 3.5.4
- SLF4J 2.x (встроен в Spring Boot)
- Logback 1.4.x (встроен в Spring Boot)
- Lombok 1.18.x

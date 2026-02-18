# Auth Service - Сервис аутентификации и авторизации

## Общее описание

Auth Service является центральным микросервисом безопасности системы ведения личных финансов, отвечающим за управление пользователями, аутентификацию, авторизацию и обеспечение безопасности доступа ко всем ресурсам системы. Сервис предоставляет полный жизненный цикл управления пользователями с поддержкой современных стандартов безопасности.

## Назначение и функциональность

### Основные возможности
- **Управление пользователями**: регистрация, верификация, управление профилями
- **Аутентификация**: проверка подлинности через логин/пароль, OAuth2, 2FA
- **Авторизация**: контроль доступа на основе ролей и разрешений
- **Управление токенами**: выдача, обновление и отзыв JWT токенов
- **Безопасность**: защита от атак, аудит действий, блокировка аккаунтов
- **Восстановление доступа**: сброс паролей, разблокировка аккаунтов
- **Интеграция с OAuth**: поддержка входа через внешние провайдеры

### Бизнес-процессы
1. **Регистрация пользователей**: создание новых аккаунтов с верификацией email
2. **Аутентификация**: проверка подлинности и выдача токенов доступа
3. **Управление сессиями**: контроль активных сессий пользователей
4. **Аудит безопасности**: отслеживание подозрительной активности

## API Endpoints

### Аутентификация
- `POST /api/auth/register` - Регистрация нового пользователя
- `POST /api/auth/login` - Вход в систему
- `POST /api/auth/logout` - Выход из системы
- `POST /api/auth/refresh` - Обновление JWT токена

### Управление паролями
- `POST /api/auth/password/forgot` - Запрос сброса пароля
- `POST /api/auth/password/reset` - Сброс пароля по токену
- `PUT /api/auth/password/change` - Изменение пароля

### Профиль пользователя
- `GET /api/auth/profile` - Получение профиля текущего пользователя
- `PUT /api/auth/profile` - Обновление профиля пользователя
- `POST /api/auth/profile/verify-email` - Верификация email адреса

### Двухфакторная аутентификация
- `POST /api/auth/2fa/enable` - Включение 2FA
- `POST /api/auth/2fa/disable` - Отключение 2FA
- `POST /api/auth/2fa/verify` - Проверка 2FA кода

### OAuth интеграция
- `GET /api/auth/oauth/{provider}/authorize` - Инициация OAuth flow
- `GET /api/auth/oauth/{provider}/callback` - Обработка OAuth callback
- `POST /api/auth/oauth/link` - Привязка OAuth аккаунта

### Управление сессиями
- `GET /api/auth/sessions` - Список активных сессий
- `DELETE /api/auth/sessions/{sessionId}` - Закрытие конкретной сессии
- `DELETE /api/auth/sessions/all` - Закрытие всех сессий

### Административные функции
- `GET /api/auth/admin/users` - Список пользователей (только для админов)
- `PUT /api/auth/admin/users/{userId}/block` - Блокировка пользователя
- `GET /api/auth/admin/audit` - Журнал аудита безопасности

## Архитектура системы

### Компоненты
- **API Gateway**: JWT валидация, маршрутизация, rate limiting
- **Auth Service Core**: Основная бизнес-логика аутентификации и авторизации
- **Security Module**: Шифрование паролей, генерация токенов, 2FA
- **Database Layer**: PostgreSQL для хранения пользователей и аудита
- **Cache Layer**: Redis для сессий, токенов и временных данных
- **Email Service**: Отправка верификационных писем и уведомлений
- **OAuth Integration**: Интеграция с внешними провайдерами (Google, GitHub)

### Принципы проектирования
- **Zero Trust Security**: проверка каждого запроса на авторизацию
- **Defense in Depth**: многослойная защита от атак
- **Least Privilege**: минимальные необходимые права доступа
- **Fail Secure**: безопасное поведение при ошибках

## Интеграции и зависимости

### Внешние интеграции
- **OAuth Providers**: Google OAuth2, GitHub OAuth, возможность расширения
- **Email Providers**: SMTP сервер или внешний сервис (SendGrid, AWS SES)
- **SMS Providers**: для 2FA через SMS (Twilio, AWS SNS)

### Внутренние зависимости
- **PostgreSQL**: основная база данных для пользователей и аудита
- **Redis**: кэширование сессий, временное хранение токенов
- **Message Queue**: уведомления о событиях безопасности

### Интеграция с другими сервисами
- **[[Budget Service]]**: проверка прав доступа к финансовым данным
- **Investment Service**: авторизация операций с портфелем
- **Analytics Service**: права доступа к аналитической информации
- **Notification Service**: уведомления о безопасности

## Безопасность

### Аутентификация и авторизация
- **JWT токены**: stateless аутентификация с подписанными токенами
- **Refresh токены**: безопасное обновление доступа без повторного логина
- **Role-based access control (RBAC)**: гранулярный контроль разрешений
- **Multi-factor authentication**: дополнительная защита через TOTP/SMS
- **OAuth2 integration**: безопасная интеграция с внешними провайдерами

### Защита от атак
- **Password hashing**: bcrypt с солью для безопасного хранения паролей
- **Brute force protection**: блокировка аккаунтов при подозрительной активности
- **Rate limiting**: ограничение частоты запросов по IP и пользователю
- **CSRF protection**: защита от межсайтовых атак
- **SQL injection prevention**: параметризованные запросы
- **XSS protection**: валидация и экранирование пользовательского ввода

### Аудит и мониторинг
- **Security audit log**: логирование всех критичных действий
- **Failed login attempts**: отслеживание неудачных попыток входа
- **Suspicious activity detection**: обнаружение аномальной активности
- **Data encryption**: шифрование чувствительных данных в покое и транзите

## Развертывание и конфигурация

### Переменные окружения
- `DATABASE_URL`: строка подключения к PostgreSQL
- `REDIS_URL`: строка подключения к Redis
- `JWT_SECRET`: секретный ключ для подписи JWT токенов (обязательно уникальный)
- `JWT_EXPIRATION`: время жизни access токена (по умолчанию 15 минут)
- `REFRESH_TOKEN_EXPIRATION`: время жизни refresh токена (по умолчанию 7 дней)
- `BCRYPT_ROUNDS`: количество раундов хеширования bcrypt (рекомендуется 12)
- `EMAIL_SMTP_HOST`: хост SMTP сервера
- `EMAIL_SMTP_PORT`: порт SMTP сервера
- `OAUTH_GOOGLE_CLIENT_ID`: Client ID для Google OAuth
- `OAUTH_GOOGLE_CLIENT_SECRET`: Client Secret для Google OAuth
- `RATE_LIMIT_LOGIN`: лимит попыток входа (по умолчанию 5 в минуту)
- `ACCOUNT_LOCKOUT_DURATION`: время блокировки аккаунта (по умолчанию 30 минут)
- `PASSWORD_RESET_TOKEN_EXPIRATION`: время жизни токена сброса пароля (1 час)

### Docker конфигурация
- Multi-stage build для минимизации размера образа
- Health checks для мониторинга состояния контейнера
- Resource limits для предотвращения утечек памяти
- Secrets management для безопасного хранения ключей
- Non-root user для повышения безопасности

### Миграции базы данных
- Версионированные миграции через Flyway/Liquibase
- Encrypted миграции для чувствительных изменений
- Rollback стратегии для критических обновлений
- Автоматизированные миграции в CI/CD pipeline
- Backup перед критичными миграциями

## Тестирование

### Покрытие тестами
- **Unit тесты**: бизнес-логика, валидация, криптография (>95% покрытие)
- **Integration тесты**: взаимодействие с БД, Redis, внешними API
- **Security тесты**: тестирование уязвимостей, пентест
- **API тесты**: полное тестирование всех endpoints
- **Load тесты**: производительность под нагрузкой (1000+ concurrent users)
- **Chaos тесты**: устойчивость к сбоям зависимостей

### Test Data Management
- Тестовые пользователи с различными ролями и статусами
- Мокирование внешних OAuth провайдеров
- Test containers для изолированного тестирования БД
- Автоматическая очистка тестовых данных
- Синтетические данные для нагрузочного тестирования

### Безопасность тестирования
- OWASP Top 10 vulnerability scanning
- Static code analysis для поиска уязвимостей
- Dynamic security testing (DAST)
- Dependency vulnerability scanning
- Secrets detection в коде и конфигурации

## Мониторинг и логирование

### Структурированные логи
- JSON формат для централизованного анализа
- Correlation IDs для трассировки сессий пользователей
- Маскирование паролей, токенов и PII данных
- Разные уровни логирования для production/development
- Централизованное хранение в ELK stack или аналогах

### Ключевые метрики безопасности
- **Authentication metrics**: successful/failed login attempts, MFA usage
- **Authorization metrics**: access denials, privilege escalations
- **Token metrics**: token generation/refresh/expiration rates
- **Security incidents**: brute force attempts, account lockouts
- **Performance metrics**: response times, throughput, error rates
- **Business metrics**: user registrations, active sessions

### Критичные алерты
- **Security incidents**: множественные неудачные попытки входа
- **System availability**: недоступность сервиса аутентификации
- **Performance degradation**: превышение времени ответа
- **Token issues**: проблемы с генерацией/валидацией JWT
- **Database issues**: недоступность PostgreSQL или Redis
- **OAuth failures**: проблемы с внешними провайдерами

### Compliance и Audit
- **GDPR compliance**: обработка персональных данных
- **Security audit trail**: логирование всех критичных операций
- **Data retention policies**: автоматическое удаление старых логов
- **Incident response**: автоматизированные процедуры реагирования
- **Regular security reviews**: периодический анализ безопасности

## Производительность и масштабирование

### Оптимизация производительности
- **Кэширование**: Redis для сессий и часто используемых данных
- **Database indexing**: оптимизированные индексы для быстрого поиска пользователей
- **Connection pooling**: эффективное управление подключениями к БД
- **Async processing**: асинхронная обработка неблокирующих операций
- **JWT optimization**: минимальный размер payload, эффективная валидация

### Стратегии масштабирования
- **Horizontal scaling**: множественные экземпляры сервиса за load balancer
- **Database scaling**: read replicas для читающих операций
- **Cache scaling**: Redis cluster для высокой нагрузки
- **CDN integration**: кэширование статичных ресурсов
- **Microservice communication**: efficient service-to-service auth

### Целевые показатели производительности
- **Authentication latency**: < 100ms (95th percentile)
- **Token validation**: < 10ms (99th percentile)
- **Throughput**: 10,000+ authentications per minute
- **Availability**: 99.9% uptime (< 8 hours downtime per year)
- **Recovery time**: < 2 minutes для service restart
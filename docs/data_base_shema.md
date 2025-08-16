# Схема базы данных - Auth Service (MVP)

## user

Основная таблица для хранения пользователей системы.

| Название колонки | Тип                                 | Описание                              |
|------------------|-------------------------------------|---------------------------------------|
| `id`             | UUID PRIMARY KEY                    | Уникальный идентификатор пользователя |
| `username`       | VARCHAR(50) UNIQUE NOT NULL         | Уникальное имя пользователя           |
| `email`          | VARCHAR(255) UNIQUE NOT NULL        | Email адрес пользователя              |
| `password_hash`  | VARCHAR(255) NOT NULL               | Хэш пароля (bcrypt)                   |
| `first_name`     | VARCHAR(100)                        | Имя пользователя                      |
| `last_name`      | VARCHAR(100)                        | Фамилия пользователя                  |
| `is_active`      | BOOLEAN DEFAULT TRUE                | Флаг активности аккаунта              |
| `is_verified`    | BOOLEAN DEFAULT FALSE               | Подтверждение email адреса            |
| `last_login_at`  | TIMESTAMP                           | Время последнего входа                |
| `created_at`     | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | Время создания                        |
| `updated_at`     | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | Время последнего обновления           |

## user_role

Роли пользователей для базового разграничения доступа.

| Название колонки | Тип                                 | Описание                             |
|------------------|-------------------------------------|--------------------------------------|
| `id`             | UUID PRIMARY KEY                    | Уникальный идентификатор роли        |
| `user_id`        | UUID NOT NULL                       | Ссылка на пользователя               |
| `role_name`      | VARCHAR(50) NOT NULL                | Название роли (USER, ADMIN, PREMIUM) |
| `granted_at`     | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | Время назначения роли                |

## refresh_token

Таблица для хранения refresh токенов для обновления JWT.

| Название колонки | Тип                                 | Описание                        |
|------------------|-------------------------------------|---------------------------------|
| `id`             | UUID PRIMARY KEY                    | Уникальный идентификатор токена |
| `user_id`        | UUID NOT NULL                       | Владелец токена                 |
| `token_hash`     | VARCHAR(255) NOT NULL               | Хэш refresh токена              |
| `expires_at`     | TIMESTAMP NOT NULL                  | Время истечения токена          |
| `is_used`        | BOOLEAN DEFAULT FALSE               | Флаг использования токена       |
| `user_agent`     | VARCHAR(500)                        | User Agent клиента              |
| `ip_address`     | INET                                | IP адрес клиента                |
| `created_at`     | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | Время создания                  |

## password_reset_token

Токены для сброса пароля пользователей.

| Название колонки | Тип                                 | Описание                        |
|------------------|-------------------------------------|---------------------------------|
| `id`             | UUID PRIMARY KEY                    | Уникальный идентификатор токена |
| `user_id`        | UUID NOT NULL                       | Пользователь для сброса пароля  |
| `token_hash`     | VARCHAR(255) NOT NULL               | Хэш токена сброса               |
| `expires_at`     | TIMESTAMP NOT NULL                  | Время истечения (обычно 1 час)  |
| `is_used`        | BOOLEAN DEFAULT FALSE               | Флаг использования токена       |
| `ip_address`     | INET                                | IP адрес запроса                |
| `created_at`     | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | Время создания                  |

## email_verification_token

Токены для подтверждения email адресов при регистрации.

| Название колонки | Тип                                 | Описание                         |
|------------------|-------------------------------------|----------------------------------|
| `id`             | UUID PRIMARY KEY                    | Уникальный идентификатор токена  |
| `user_id`        | UUID NOT NULL                       | Пользователь для верификации     |
| `token_hash`     | VARCHAR(255) NOT NULL               | Хэш токена верификации           |
| `expires_at`     | TIMESTAMP NOT NULL                  | Время истечения (обычно 24 часа) |
| `is_used`        | BOOLEAN DEFAULT FALSE               | Флаг использования токена        |
| `created_at`     | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | Время создания                   |

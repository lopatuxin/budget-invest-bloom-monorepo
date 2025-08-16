-- Инициализация базы данных для локальной разработки

-- Создание схемы приложения
CREATE SCHEMA IF NOT EXISTS auth;

-- Установка поискового пути для схемы
ALTER DATABASE auth_dev SET search_path TO auth, public;

-- Предоставление прав пользователю на схему
GRANT ALL PRIVILEGES ON SCHEMA auth TO auth_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA auth TO auth_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA auth TO auth_user;

-- Установка прав по умолчанию для новых объектов
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT ALL PRIVILEGES ON TABLES TO auth_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth GRANT ALL PRIVILEGES ON SEQUENCES TO auth_user;
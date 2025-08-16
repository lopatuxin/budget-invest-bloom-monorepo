@echo off
echo Перезапуск PostgreSQL контейнера с очисткой данных...
docker-compose down postgres-dev
docker volume rm auth_postgres_data 2>nul
docker-compose up -d postgres-dev
echo.
echo PostgreSQL контейнер перезапущен с чистой базой данных!
echo.
echo Параметры подключения:
echo   Host: localhost
echo   Port: 5432
echo   Database: auth_dev
echo   Username: auth_user
echo   Password: dev_password
pause
@echo off
echo Запуск PostgreSQL контейнера для разработки...
docker-compose up -d postgres-dev
echo.
echo PostgreSQL контейнер запущен!
echo.
echo Параметры подключения:
echo   Host: localhost
echo   Port: 5432
echo   Database: auth_dev
echo   Username: auth_user
echo   Password: dev_password
echo.
echo Для остановки контейнера используйте: stop-postgres.bat
pause
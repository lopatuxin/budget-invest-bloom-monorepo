@echo off
echo Остановка PostgreSQL контейнера...
docker-compose stop postgres-dev
echo.
echo PostgreSQL контейнер остановлен!
pause
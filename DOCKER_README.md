# Docker Setup Guide

Это руководство по настройке и использованию Docker для проекта Budget Invest Bloom.

## Структура файлов

- `Dockerfile` - мультистейдж Dockerfile для development и production
- `docker-compose.yml` - конфигурация Docker Compose
- `.dockerignore` - файлы, исключаемые из Docker контекста
- `nginx.conf` - конфигурация Nginx для production
- `.env.docker` - пример переменных окружения

## Быстрый старт

### Development режим с hot-reload

```bash
# Запустить только frontend в режиме разработки
docker-compose up frontend-dev

# Или в фоновом режиме
docker-compose up -d frontend-dev
```

Приложение будет доступно на `http://localhost:8080`

### Production режим

```bash
# Собрать и запустить production версию
docker-compose --profile production up frontend-prod

# Или в фоновом режиме
docker-compose --profile production up -d frontend-prod
```

Production версия будет доступна на `http://localhost:3000`

## Основные команды

### Управление контейнерами

```bash
# Запустить сервисы
docker-compose up

# Запустить в фоновом режиме
docker-compose up -d

# Остановить сервисы
docker-compose down

# Остановить и удалить volumes
docker-compose down -v

# Пересобрать образы
docker-compose build

# Пересобрать без кэша
docker-compose build --no-cache

# Посмотреть логи
docker-compose logs -f frontend-dev

# Посмотреть запущенные контейнеры
docker-compose ps
```

### Работа с контейнером

```bash
# Войти в контейнер
docker-compose exec frontend-dev sh

# Установить новые пакеты (если нужно)
docker-compose exec frontend-dev npm install <package-name>

# После установки пакетов пересоберите образ
docker-compose build frontend-dev
```

## Hot-reload

Hot-reload настроен через volume mapping в `docker-compose.yml`:

- `/src` - исходный код
- `/public` - статические файлы
- `/index.html` - главный HTML файл
- Конфигурационные файлы (vite.config.ts, tailwind.config.js и др.)

Изменения в этих файлах автоматически отслеживаются Vite и обновляются в браузере.

## Переменные окружения

### Для разработки

Используйте `.env.development` или создайте локальный `.env` файл:

```env
VITE_API_BASE_URL=http://localhost:8081
```

### Для production

Используйте `.env.production`:

```env
VITE_API_BASE_URL=https://your-production-domain.com
```

## Добавление новых сервисов

### Backend сервис

Раскомментируйте секцию `backend` в `docker-compose.yml`:

```yaml
backend:
  image: your-backend-image:latest
  container_name: budget-invest-backend
  ports:
    - "8081:8081"
  environment:
    - NODE_ENV=development
  networks:
    - budget-invest-network
  restart: unless-stopped
```

### База данных (PostgreSQL)

Раскомментируйте секции `database` и `volumes` в `docker-compose.yml`:

```yaml
database:
  image: postgres:15-alpine
  container_name: budget-invest-db
  ports:
    - "5432:5432"
  environment:
    - POSTGRES_USER=${DB_USER:-postgres}
    - POSTGRES_PASSWORD=${DB_PASSWORD:-postgres}
    - POSTGRES_DB=${DB_NAME:-budget_invest}
  volumes:
    - postgres-data:/var/lib/postgresql/data
  networks:
    - budget-invest-network
  restart: unless-stopped
```

## Troubleshooting

### Контейнер не запускается

```bash
# Проверьте логи
docker-compose logs frontend-dev

# Пересоберите образ
docker-compose build --no-cache frontend-dev
```

### Hot-reload не работает

1. Убедитесь, что используете `frontend-dev` сервис
2. Проверьте, что файлы монтируются правильно:
```bash
docker-compose exec frontend-dev ls -la /app/src
```
3. Перезапустите контейнер:
```bash
docker-compose restart frontend-dev
```

### Порт уже занят

Измените порт в `docker-compose.yml`:

```yaml
ports:
  - "3001:8080"  # Используйте другой порт
```

### Проблемы с правами доступа (Linux/Mac)

Если возникают проблемы с правами на файлы:

```bash
# Добавьте user в Dockerfile
USER node

# Или запустите с текущим пользователем
docker-compose run --user $(id -u):$(id -g) frontend-dev
```

## Оптимизация

### Production build

Production образ оптимизирован:
- Multi-stage build для минимизации размера
- Nginx для статических файлов
- Gzip компрессия
- Кэширование статических ресурсов

### Размер образа

Проверить размер образов:

```bash
docker images | grep budget-invest
```

## Дополнительные настройки

### Использование другой Node версии

Измените в `Dockerfile`:

```dockerfile
FROM node:18-alpine AS development  # вместо node:20-alpine
```

### Настройка Nginx

Измените `nginx.conf` для дополнительных настроек:
- Proxy для API
- SSL сертификаты
- Дополнительные заголовки безопасности

## Полезные ссылки

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Vite Documentation](https://vitejs.dev/)
- [Nginx Documentation](https://nginx.org/en/docs/)
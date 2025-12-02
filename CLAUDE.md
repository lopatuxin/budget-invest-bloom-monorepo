# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Communication Rule
Always communicate with the user in Russian language for all messages, explanations, and code-related discussions.

## Project Overview

This is a **Spring Boot microservice** for the Budget Invest Bloom system, using:
- **Java 24** with Eclipse Temurin JDK
- **Gradle 8.14.2** build system
- **PostgreSQL** database
- **Liquibase** for database migrations
- **Spring Boot 4.0.0** framework
- **Docker Compose** for local development

Package structure: `pyc.lopatuxin.budget`

## Build & Run Commands

### Build
```bash
# Build the project
./gradlew build

# Build without tests
./gradlew build -x test

# Clean and build
./gradlew clean build
```

### Run Application
```bash
# Run with default profile
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run with Docker Compose (includes PostgreSQL)
docker-compose up

# Run only PostgreSQL
docker-compose up postgres
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "ClassName"

# Run with coverage
./gradlew test jacocoTestReport
```

### Database Commands
```bash
# Start PostgreSQL via Docker
docker-compose up -d postgres

# Connect to PostgreSQL
docker-compose exec postgres psql -U myuser -d mydatabase

# View Liquibase migration status (if plugin configured)
./gradlew liquibaseStatus
```

## Architecture & Key Concepts

### Project Structure
```
src/
├── main/
│   ├── java/pyc/lopatuxin/budget/
│   │   └── BudgetApplication.java     # Main application entry point
│   └── resources/
│       └── application.yml            # Base configuration
└── test/
    └── java/pyc/lopatuxin/budget/
        ├── BudgetApplicationTests.java
        └── TestcontainersConfiguration.java
```

### Standard API Request/Response Structure

**All API endpoints follow a unified structure:**

**Request Format:**
```json
{
  "user": {
    "userId": "uuid",
    "email": "string",
    "role": "USER|ADMIN|MODERATOR",
    "sessionId": "uuid"
  },
  "data": {
    // Actual request payload
  }
}
```

**Response Format:**
```json
{
  "id": "uuid",              // Request correlation ID
  "status": 200,             // HTTP status code
  "message": "string",       // Human-readable message (Russian)
  "timestamp": "ISO-8601",   // Response timestamp
  "body": {
    // Actual response payload
  }
}
```

Key points:
- The `user` block is automatically populated by API Gateway from JWT tokens
- The `id` field enables distributed tracing across microservices
- All messages should be in Russian language
- Sensitive data (passwords, tokens) must be masked in logs

### Database Configuration

**Local Development (Docker Compose):**
- Database: `mydatabase`
- User: `myuser`
- Password: `secret`
- Port: `5432`

Connection URL: `jdbc:postgresql://localhost:5432/mydatabase`

**Liquibase:**
- Manages all database schema changes
- Do NOT use Hibernate DDL auto (use `validate` mode only)
- Migrations should be organized by version in `db/changelog/`

### Logging Configuration

The project uses a comprehensive logging setup with:
- **SLF4J + Logback** as logging framework
- **@Slf4j** annotation from Lombok for logger injection
- **UTF-8** encoding for Cyrillic character support
- **Sensitive data masking** for passwords, tokens, and headers
- **HTTP request/response logging** via filter (excludes actuator endpoints)

**Key Logging Practices:**
- Use parameterized logging: `log.info("User {}", userId)` not string concatenation
- Always include exception parameter: `log.error("Error: {}", msg, exception)`
- Mask sensitive fields: `password`, `accessToken`, `refreshToken`, `token`
- Different log levels per environment:
  - Development: `DEBUG` for application, SQL queries visible
  - Production: `INFO` for application, no SQL queries
  - Testing: `WARN` to reduce noise

Configuration files:
- `logback-spring.xml` - Main Logback configuration
- `application-dev.yml` - Development logging levels
- `application-prod.yml` - Production logging levels (recommended)

### Docker Development Setup

The project includes Docker configuration for hot-reload development:

**Key Features:**
- Hot-reload via Spring DevTools (port 35729)
- PostgreSQL container with persistent volume
- Source code mounted as bind volumes
- Gradle cache persistence
- Health checks for both app and database

**Docker Compose Commands:**
```bash
# Start all services with logs
docker-compose up

# Start in background
docker-compose up -d

# Rebuild and start
docker-compose up --build

# Stop all services
docker-compose down

# Stop and remove volumes (clears database)
docker-compose down -v

# View logs
docker-compose logs -f
docker-compose logs auth-app
docker-compose logs postgres
```

### Testing with Testcontainers

The project uses Testcontainers for integration testing with PostgreSQL:
- Automatic PostgreSQL container spin-up during tests
- Configuration in `TestcontainersConfiguration.java`
- Ensures tests run against real database

## Development Guidelines

### Adding New Endpoints

When creating new REST endpoints:
1. Always use the standard request/response structure (see above)
2. Implement proper validation using `@Valid` and validation annotations
3. Add request/response logging (automatically handled by filter)
4. Use appropriate HTTP status codes (200, 201, 400, 404, 500)
5. Write Russian language messages in responses
6. Include correlation ID from request context if available

### Database Migrations

**Never modify existing Liquibase migrations.** Instead:
1. Create new migration file in `db/changelog/vX.Y.Z/`
2. Name files descriptively: `NNN-description.yml`
3. Include in version changelog file
4. Test migration both up and down (if rollback supported)
5. Use `validate` mode for `hibernate.ddl-auto` in all profiles

Migration structure:
```
db/changelog/
├── db.changelog-master.yml           # Master changelog
├── v1.0.0/
│   ├── changelog-v1.0.0.yml         # Version index
│   ├── 001-create-users-table.yml
│   └── 002-create-roles-table.yml
└── v1.1.0/
    └── ...
```

### Security & Sensitive Data

**Critical: Never log sensitive data unmasked:**
- Passwords
- JWT tokens (access/refresh)
- API keys
- Credit card numbers
- Personal identification numbers

Use `SensitiveDataMasker` utility (if available) or create similar masking for:
- JSON request/response bodies
- HTTP headers (Authorization, Cookie)
- Log messages containing user data

### Code Organization

Follow the package structure:
```
pyc.lopatuxin.budget/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── service/         # Business logic
├── repository/      # Data access layer
├── entity/          # JPA entities
├── dto/             # Data transfer objects
├── exception/       # Custom exceptions
├── util/            # Utility classes
└── security/        # Security configuration
```

### Lombok Usage

The project uses Lombok annotations:
- `@Slf4j` - Logger injection
- `@Data` - Getters, setters, toString, equals, hashCode
- `@Builder` - Builder pattern
- `@NoArgsConstructor`, `@AllArgsConstructor` - Constructors
- `@RequiredArgsConstructor` - Constructor for final fields

### Spring Profiles

Configure different profiles in separate files:
- `application.yml` - Base configuration
- `application-dev.yml` - Development settings
- `application-test.yml` - Test configuration
- `application-prod.yml` - Production settings

Activate profile via:
- Command line: `--spring.profiles.active=dev`
- Environment variable: `SPRING_PROFILES_ACTIVE=dev`
- IDE configuration

## Common Issues & Solutions

### Port Already in Use
If port 8080 is occupied:
- Change port in `application.yml`: `server.port: 8081`
- Or kill the process using the port

### Database Connection Issues
1. Ensure PostgreSQL is running: `docker-compose ps`
2. Check connection details in `application-dev.yml`
3. Verify database exists: `docker-compose exec postgres psql -U myuser -l`

### Liquibase Lock
If Liquibase reports lock issues:
```sql
DELETE FROM databasechangeloglock;
```

### Hot-Reload Not Working
1. Ensure DevTools is in classpath
2. Check bind mounts in `docker-compose.yml`
3. Verify `DEVTOOLS_RESTART_ENABLED=true`
4. Try restarting: `docker-compose restart auth-app`

### Test Failures
- Check Testcontainers can access Docker daemon
- Ensure Docker is running
- Review test logs for database connectivity issues

## Additional Resources

Project documentation is available in `docs/setup/`:
- `standartRequestAndResponse.md` - Detailed API structure specification
- `docker_setup.md` - Comprehensive Docker configuration guide
- `logging_setup.md` - Complete logging setup and best practices
- `liquibase_setup.md` - Database migration setup instructions

For Spring Boot reference, see `HELP.md` in project root.
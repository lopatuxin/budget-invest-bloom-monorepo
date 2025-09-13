# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Communication Rule
- **Always communicate with the user in Russian language** for all messages, explanations, and code-related discussions.

---

## Project Overview

This is a Spring Boot 3.5.4 authentication service using Java 24. It's configured as a microservice with security, data persistence, and validation capabilities.

## Common Development Commands

### Build and Run
- `./gradlew build` - Build the application
- `./gradlew bootRun` - Run the application locally (requires PostgreSQL running)
- `./gradlew bootTestRun` - Run application with test configuration (uses Testcontainers)
- `./gradlew clean` - Clean build artifacts

### Database Management
- `docker-compose up postgres-dev` - Start PostgreSQL container for development
- `docker-compose down` - Stop PostgreSQL container
- `docker-compose down -v` - Stop and remove PostgreSQL container with volumes (clean slate)

### Testing
- `./gradlew test` - Run all tests
- `./gradlew check` - Run all verification tasks

### Docker/OCI
- `./gradlew bootBuildImage` - Build OCI container image

## Architecture

### Technology Stack
- **Framework**: Spring Boot 3.5.4 with Spring Security
- **Database**: PostgreSQL (with Liquibase migrations)
- **Testing**: JUnit 5 with Testcontainers for integration tests
- **Build**: Gradle with Kotlin DSL
- **Java Version**: Java 24

### Package Structure
- Base package: `pyc.lopatuxin.auth`
- Main application class: `AuthApplication.java`
- Test configuration with Testcontainers: `TestcontainersConfiguration.java`

#### Application Layers
- `controller/` - REST controllers handling HTTP requests
- `service/` - Business logic and service layer
- `repository/` - Data access layer with Spring Data JPA repositories
- `entity/` - JPA entities representing database tables
- `dto/` - Data Transfer Objects for API requests and responses
- `mapper/` - MapStruct mappers for entity-DTO conversion
- `config/` - Configuration classes (Security, etc.)
- `exception/` - Custom exceptions and global exception handler
- `enums/` - Enumeration classes

### Key Dependencies
- Spring Boot Starters: Web, Data JPA, Security, Validation
- Liquibase for database migrations
- Lombok for boilerplate reduction
- MapStruct for entity-DTO mapping
- SpringDoc OpenAPI for API documentation
- Spring Boot DevTools for development
- Testcontainers with PostgreSQL for testing

### Testing Strategy
- Uses Testcontainers for integration tests with real PostgreSQL database
- Separate test application (`TestAuthApplication`) for development testing
- Test configuration automatically spins up PostgreSQL container using `postgres:latest` image

### Database
- PostgreSQL as primary database
- Liquibase for database schema versioning (changelog directory: `src/main/resources/db/changelog/`)
- Testcontainers provides isolated PostgreSQL instances for tests
- Database connection configured via `application-dev.yml` for local development

### Configuration Profiles
- Default profile (`application.yml`): minimal configuration
- Development profile (`application-dev.yml`): full local development setup with PostgreSQL, debug logging, and dev tools
- Development database: `auth_dev` on localhost:5432 (user: `auth_user`, password: `dev_password`)
- Application runs on port 8080 with context path `/auth`

## Development Notes

- The application uses Spring Boot DevTools for hot reloading during development
- Test configuration is automatically imported in tests via `@Import(TestcontainersConfiguration.class)`
- Docker is required for running tests due to Testcontainers dependency
- Local development requires PostgreSQL running (use docker-compose)
- Application endpoints are available at `http://localhost:8080/auth/`
- Actuator endpoints exposed: health, info, metrics, env (for monitoring)
- Debug logging enabled for security and SQL in development profile
- **IMPORTANT**: Before writing any code, always check the relevant documentation in the `docs/` folder to ensure new code fully complies with the technical specifications and project requirements

## Project Structure and Important Files

### Key Directories
- `src/main/java/pyc/lopatuxin/auth/` - Main application code
- `src/main/resources/db/changelog/` - Liquibase database migrations
- `src/test/java/pyc/lopatuxin/auth/` - Test classes and configuration
- `docs/` - Project documentation including detailed service description
- `docker/` - Docker initialization scripts

### Configuration Files
- `application.yml` - Base Spring Boot configuration
- `application-dev.yml` - Development profile with PostgreSQL setup
- `docker-compose.yml` - PostgreSQL container for local development
- `build.gradle.kts` - Gradle build configuration with dependencies

### Database Schema
Current migrations in `src/main/resources/db/changelog/v1.0.0/`:
- `001-create-users-table.yml` - Core user entity
- `002-create-user-roles-table.yml` - Role-based access control
- `003-create-refresh-tokens-table.yml` - JWT refresh token management
- `004-create-password-reset-tokens-table.yml` - Password recovery
- `005-create-email-verification-tokens-table.yml` - Email verification

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.

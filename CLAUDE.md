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
- `./gradlew bootRun` - Run the application locally
- `./gradlew bootTestRun` - Run application with test configuration (uses Testcontainers)
- `./gradlew clean` - Clean build artifacts

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

### Key Dependencies
- Spring Boot Starters: Data JPA, Security, Validation
- Liquibase for database migrations
- Lombok for boilerplate reduction
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

## Development Notes

- The application uses Spring Boot DevTools for hot reloading during development
- Test configuration is automatically imported in tests via `@Import(TestcontainersConfiguration.class)`
- Docker is required for running tests due to Testcontainers dependency

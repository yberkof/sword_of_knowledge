# GEMINI.md

## Project Overview
**Sword of Knowledge (SoK)** is a high-performance backend for a realtime multiplayer game. It provides both a RESTful API for management tasks (auth, profiles, shop, etc.) and a Socket.IO-compatible server for low-latency gameplay.

### Core Technologies
- **Runtime**: Java 17 (Spring Boot 2.7.x)
- **Realtime**: Netty-Socket.io (running on port `8081` by default)
- **Database**: PostgreSQL with Flyway migrations
- **Caching/Realtime Sync**: Redis (optional, via Lettuce)
- **Security**: Stateless JWT-based authentication, with support for Firebase (Google) identity verification.
- **Monitoring**: Spring Boot Actuator + Micrometer/Prometheus.

## Architecture
The system follows a modular monolith approach:
- **API Layer (`com.sok.backend.api`)**: Standard Spring MVC controllers for REST endpoints.
- **Realtime Layer (`com.sok.backend.realtime`)**: Custom game engine using an in-memory room model. Each room uses a single-threaded executor to ensure thread-safe event processing without heavy locking.
- **Service Layer (`com.sok.backend.service`)**: Business logic for progression, payments, and economy.
- **Persistence Layer (`com.sok.backend.persistence`)**: JDBC-based repositories.

## Building and Running

### Prerequisites
- Java 17
- PostgreSQL 14+
- (Optional) Redis

### Commands
- **Compile**: `./mvnw compile`
- **Run Application**: `./mvnw spring-boot:run`
- **Run Tests**: `./mvnw test`
- **Build JAR**: `./mvnw clean package`

### Configuration
Key settings are in `src/main/resources/application.yml`. Environment variables like `DATABASE_URL`, `DB_USER`, `DB_PASSWORD`, and `AUTH_JWT_SECRET` should be provided in a `.env` file or exported to the shell.

## Development Conventions

### Agent Interaction Rule
- **Mandatory Greeting**: Every response to a directive or inquiry MUST start with the phrase: `"Roger that, Master!"`. Do not use emojis.

### Coding Standards
- **Style**: Adhere to SOLID and KISS principles. Keep logic simple and readable.
- **Naming**: `camelCase` for variables/methods, `PascalCase` for classes, and `kebab-case` for directories and static assets.
- **Logging**: All API requests are automatically logged via `RequestLoggingFilter`. Do not bypass or remove this.
- **Security**: Never hardcode secrets. Use the `app.auth.jwt-secret` property or environment variables.
- **Verification**: Always run `./mvnw compile` after making Java changes to catch errors early.

### Directory Structure
- `src/main/java/com/sok/backend/api`: REST Controllers.
- `src/main/java/com/sok/backend/realtime`: Socket.IO gateway and room management.
- `src/main/java/com/sok/backend/service`: Domain logic (Progression, Shop, etc.).
- `src/main/resources/db/migration`: Flyway SQL migration files.
- `docs/`: Extensive documentation on API, Socket protocol, and game flow.

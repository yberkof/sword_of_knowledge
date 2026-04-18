# Sword of Knowledge: AI Agent Guide

This document serves as a high-level technical map for AI agents (Antigravity, Cursor, etc.) working on this repository.

## 🏗️ Project Architecture
The project is split into two main parts:
1.  **Backend (This Repo)**: Spring Boot 2.7.x / Java 17.
2.  **Frontend**: Next.js 14 (Located in `../marefa-game`).

## 🛠️ Tech Stack
-   **Runtime**: Java 17 (OpenJDK).
-   **Database**: PostgreSQL 14+ (Schema: `sword_of_knowledge`).
-   **Migrations**: Flyway.
-   **Authentication**: JWT (Stateless) + Optional Firebase integration.
-   **Realtime**: Netty-Socket.io (Port 8081).
-   **Logging**: Custom `RequestLoggingFilter` for API metadata.

## 🔐 Authentication Handling
-   **Controllers**: `AuthController.java` handles `/login`, `/register`, `/token`, and `/refresh`.
-   **Strategy**: Grant-type based. `AuthService` delegates to specific `AuthIdentityProvider` implementations.
-   **Validation**: 
    -   Backend uses bcrypt for password hashing.
    -   `LocalJwtAuthFilter` validates tokens for every authenticated request.
-   **Identity Principal**: `AuthenticatedUser` containing the `uid`.

## 🚀 Development Operations
### Run Backend
```bash
./mvnw spring-boot:run
```
*Note: Ensure `.env` is configured or variables are exported. Default port: 8080.*

### Database Setup
-   Migrations are in `src/main/resources/db/migration`.
-   Config is in `application.yml`.

## 🤖 Agent Instructions (RULES)
-   **SOLID & KISS**: Favor readable, simple logic. Avoid over-engineering.
-   **Logging**: All API requests are logged via `RequestLoggingFilter`. Do not remove this unless explicitly asked.
-   **Security**: Never hardcode secrets. Use placeholders in `application.yml` and values in `.env`.
-   **Verification**: Always run `./mvnw compile` after any Java code change to verify correctness and catch compilation errors early.
-   **Casing**:
    -   Java: `camelCase` for variables/methods, `PascalCase` for classes.
    -   Folders: `kebab-case`.
-   **Drafting Responses**: Always start with "Roger that, Master!". No emojis.

## 📁 Key Directories
-   `src/main/java/com/sok/backend/api`: REST Endpoints (Controllers).
-   `src/main/java/com/sok/backend/security`: Auth filters and principal definitions.
-   `src/main/java/com/sok/backend/realtime`: Socket.IO implementation and game engine.
-   `src/main/java/com/sok/backend/service`: Domain business logic.

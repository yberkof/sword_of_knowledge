# Build context: repo root (`docker compose -f deploy/docker-compose.deploy.yml build` uses context `..`).
# Stage 1: Maven on the build host (VPS or CI). Stage 2: runtime JRE only.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/target/spring-backend-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

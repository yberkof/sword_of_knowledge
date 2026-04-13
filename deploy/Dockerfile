# Build context: repo root. Run: mvn -q package && docker build -f deploy/Dockerfile -t sok-backend:latest .
FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY target/spring-backend-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

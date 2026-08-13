# Multi-stage build for Acomi Backend (Render Docker Web Service).
# Secrets and connection settings come from the runtime environment only.

# ---- Build stage ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

RUN chmod +x mvnw \
    && ./mvnw -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Non-root process for runtime
RUN groupadd --system acomi && useradd --system --gid acomi --home-dir /app acomi

COPY --from=build --chown=acomi:acomi /app/target/acomi-backend-0.0.1-SNAPSHOT.jar app.jar

USER acomi

# Render injects PORT. Spring Boot binds via server.port=${PORT:8080}.
# Do not hardcode secrets, DB credentials, profile, or PORT here.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

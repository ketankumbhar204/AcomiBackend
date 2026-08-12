# Multi-stage build for Amico Backend (Render Docker Web Service).
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
RUN groupadd --system amico && useradd --system --gid amico --home-dir /app amico

COPY --from=build --chown=amico:amico /app/target/amico-backend-0.0.1-SNAPSHOT.jar app.jar

USER amico

# Render injects PORT. Spring Boot binds via server.port=${PORT:8080}.
# Do not hardcode secrets, DB credentials, profile, or PORT here.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

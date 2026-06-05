# ──────────────────────────────────────────────────────────────
# Vajra — CWR Anomaly Detection Engine
# Multi-stage Dockerfile for Railway / Render deployment
# ──────────────────────────────────────────────────────────────

# Stage 1: Build the fat JAR with Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy dependency manifest first — layer-cached until pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# ──────────────────────────────────────────────────────────────
# Stage 2: Minimal JRE runtime image (no Maven, no JDK)
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/anomaly-engine-1.0.0.jar app.jar

# Render and Railway inject $PORT at runtime; default to 8080 locally
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

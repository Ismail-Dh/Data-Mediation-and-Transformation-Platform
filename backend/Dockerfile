# ── Stage 1 : Build ───────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copier pom.xml seul → cache des dépendances (layer séparé)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copier le source et compiler
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2 : Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
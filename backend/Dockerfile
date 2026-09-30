# =============================================================================
# Taller Evaluativo 2 - Fase 2: Empaquetamiento Inmutable Multi-Stage
# Backend: studytrack-api
#
# Nota: la etiqueta "maven:3.9.6-alpine" no existe en Docker Hub; la variante
# Alpine oficial de Maven 3.9.6 con JDK 17 es la siguiente.
# =============================================================================

# ---------- Stage 1: Builder ----------
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Cache de dependencias: solo se invalida si cambia el pom.xml
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# ---------- Stage 2: Runtime ----------
# Solo JRE (sin SDK, sin Maven, sin código fuente)
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

COPY --from=builder --chown=appuser:appgroup /build/target/studytrack-api.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

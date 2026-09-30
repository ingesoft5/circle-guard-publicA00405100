### Build context = raiz del monorepo
### docker build -f services/circleguard-dashboard-service/Dockerfile -t circleguard/dashboard:TAG .
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradlew ./
COPY gradle ./gradle
COPY services ./services
RUN chmod +x gradlew && ./gradlew :services:circleguard-dashboard-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 circleguard
COPY --from=build /workspace/services/circleguard-dashboard-service/build/libs/*.jar app.jar
USER circleguard
EXPOSE 8084
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

### Build context = raiz del monorepo
### docker build -f services/circleguard-auth-service/Dockerfile -t circleguard/auth:TAG .
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradlew ./
COPY gradle ./gradle
COPY services ./services
RUN chmod +x gradlew && ./gradlew :services:circleguard-auth-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 circleguard
COPY --from=build /workspace/services/circleguard-auth-service/build/libs/*.jar app.jar
USER circleguard
EXPOSE 8180
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

### Build context = raiz del monorepo
### docker build -f services/circleguard-promotion-service/Dockerfile -t circleguard/promotion:TAG .
FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts gradlew ./
COPY gradle ./gradle
COPY services ./services
RUN chmod +x gradlew && ./gradlew :services:circleguard-promotion-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 circleguard
COPY --from=build /workspace/services/circleguard-promotion-service/build/libs/*.jar app.jar
USER circleguard
EXPOSE 8088
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

# Empaqueta el .jar ya compilado (./gradlew assemble). Contexto: raiz del repo.
#   docker build -f services/circleguard-notification-service/Dockerfile -t circleguard/notification:TAG .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/circleguard-notification-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

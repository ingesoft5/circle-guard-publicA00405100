# Empaqueta el .jar ya compilado (./gradlew assemble). Contexto: raiz del repo.
#   docker build -f services/circleguard-dashboard-service/Dockerfile -t circleguard/dashboard:TAG .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/circleguard-dashboard-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

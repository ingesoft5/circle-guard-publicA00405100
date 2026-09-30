# Empaqueta el .jar ya compilado (./gradlew assemble). Contexto: raiz del repo.
#   docker build -f services/circleguard-identity-service/Dockerfile -t circleguard/identity:TAG .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/circleguard-identity-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

# Empaqueta el .jar ya compilado (./gradlew assemble). Contexto: raiz del repo.
#   docker build -f services/circleguard-auth-service/Dockerfile -t circleguard/auth:TAG .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/circleguard-auth-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8180
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

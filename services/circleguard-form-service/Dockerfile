# Empaqueta el .jar ya compilado (./gradlew assemble). Contexto: raiz del repo.
#   docker build -f services/circleguard-form-service/Dockerfile -t circleguard/form:TAG .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/circleguard-form-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

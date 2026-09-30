# Empaqueta el .jar ya compilado (./gradlew assemble). Contexto: raiz del repo.
#   docker build -f services/circleguard-promotion-service/Dockerfile -t circleguard/promotion:TAG .
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY services/circleguard-promotion-service/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]

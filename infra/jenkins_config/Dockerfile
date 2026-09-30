# Imagen de Jenkins con el CLI de Docker instalado, para que el propio
# controlador (montando /var/run/docker.sock) pueda construir y publicar
# las imágenes del backend y del frontend durante el pipeline.
#
# Los binarios se copian desde imágenes oficiales con versión fija (sin apt
# ni repositorios externos): Docker CLI + plugins compose/buildx y Maven.
FROM docker:27.5.1-cli AS dockercli
FROM maven:3.9.6-eclipse-temurin-17 AS mavendist

FROM jenkins/jenkins:lts-jdk17

USER root

# Solo el cliente Docker: el daemon es el del host, alcanzado por el socket montado
COPY --from=dockercli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=dockercli /usr/local/libexec/docker/cli-plugins/ /usr/local/libexec/docker/cli-plugins/

# Maven para test/package/deploy del backend (usa el JDK 17 de la imagen Jenkins)
COPY --from=mavendist /usr/share/maven /usr/share/maven
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER jenkins

FROM jenkins/jenkins:lts-jdk21

USER root

# Herramientas que el pipeline necesita dentro del contenedor de Jenkins:
# - docker CLI  -> construir las imagenes usando el socket montado del host
# - kubectl     -> desplegar en los namespaces dev/stage/master
# - kind        -> cargar las imagenes locales en el cluster
# - python3/pip -> pruebas E2E (pytest) y de rendimiento (locust)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates curl gnupg lsb-release git \
        python3 python3-pip python3-venv && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
        https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends docker-ce-cli && \
    rm -rf /var/lib/apt/lists/*

# kubectl
RUN KREL="$(curl -sL https://dl.k8s.io/release/stable.txt)" && \
    curl -fsSLo /usr/local/bin/kubectl \
        "https://dl.k8s.io/release/${KREL}/bin/linux/amd64/kubectl" && \
    chmod +x /usr/local/bin/kubectl

# kind
RUN curl -fsSLo /usr/local/bin/kind \
        https://kind.sigs.k8s.io/dl/v0.23.0/kind-linux-amd64 && \
    chmod +x /usr/local/bin/kind

# Dependencias de las pruebas E2E y de rendimiento
RUN pip3 install --no-cache-dir --break-system-packages \
        requests pytest pytest-html locust

# El usuario jenkins debe poder hablar con /var/run/docker.sock del host.
# El GID 999 es el habitual del grupo docker en Debian/Ubuntu; el script
# scripts/setup-entorno.sh lo corrige automaticamente si en la maquina difiere.
ARG DOCKER_GID=999
RUN groupadd -g ${DOCKER_GID} dockerhost || true && \
    usermod -aG ${DOCKER_GID} jenkins

# Instalacion desatendida de plugins y configuracion por codigo (JCasC)
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt --latest false

COPY casc.yaml /var/jenkins_home/casc/jenkins.yaml
COPY init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/
RUN chown -R jenkins:jenkins /var/jenkins_home/casc /usr/share/jenkins/ref/init.groovy.d

USER jenkins

# Sin asistente de primer arranque: el entorno queda listo al levantar
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc/jenkins.yaml

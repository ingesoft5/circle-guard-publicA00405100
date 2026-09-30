#!/usr/bin/env bash
# =====================================================================
# TALLER 2 - Preparacion del entorno completo desde cero
#
#   bash scripts/setup-entorno.sh
#
# Deja listo: cluster kind, namespaces, kubeconfig para Jenkins,
# infraestructura de desarrollo y el archivo .env del compose de Jenkins.
# =====================================================================
set -euo pipefail

CLUSTER="circleguard"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

echo "==> 1/6 Verificando requisitos"
for cmd in docker kind kubectl; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "FALTA: $cmd no esta instalado o no esta en el PATH." >&2
        echo "  kind:    curl -Lo ~/bin/kind https://kind.sigs.k8s.io/dl/v0.23.0/kind-linux-amd64 && chmod +x ~/bin/kind" >&2
        echo "  kubectl: curl -Lo ~/bin/kubectl https://dl.k8s.io/release/\$(curl -sL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl && chmod +x ~/bin/kubectl" >&2
        exit 1
    fi
done
docker info >/dev/null 2>&1 || { echo "Docker no esta corriendo." >&2; exit 1; }
echo "    ok"

echo "==> 2/6 Cluster kind '${CLUSTER}'"
if kind get clusters 2>/dev/null | grep -qx "${CLUSTER}"; then
    echo "    ya existe, se reutiliza"
else
    kind create cluster --name "${CLUSTER}"
fi

echo "==> 3/6 Namespaces dev, stage y master"
for ns in dev stage master; do
    kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f -
done

echo "==> 4/6 kubeconfig accesible desde el contenedor de Jenkins"
# --internal usa el nombre del contenedor del control-plane en la red 'kind',
# en vez de 127.0.0.1, que dentro de Jenkins no resolveria al cluster.
mkdir -p "${RAIZ}/.secretos"
kind get kubeconfig --name "${CLUSTER}" --internal > "${RAIZ}/.secretos/kubeconfig-kind"
chmod 600 "${RAIZ}/.secretos/kubeconfig-kind"
echo "    generado en .secretos/kubeconfig-kind"

echo "==> 5/6 Infraestructura de desarrollo (Postgres, Kafka, Neo4j, Redis)"
if [ -f docker-compose.dev.yml ]; then
    docker compose -f docker-compose.dev.yml up -d
else
    echo "    docker-compose.dev.yml no encontrado, se omite"
fi

echo "==> 6/6 Archivo .env para el compose de Jenkins"
DOCKER_GID="$(getent group docker | cut -d: -f3 || echo 999)"
if [ ! -f .env ]; then
    cat > .env <<EOF
# Complete estos valores antes de levantar Jenkins
DOCKER_GID=${DOCKER_GID}
JENKINS_ADMIN_ID=admin
JENKINS_ADMIN_PASSWORD=admin
GITHUB_USER=
GITHUB_TOKEN=
EOF
    echo "    creado .env - complete GITHUB_USER y GITHUB_TOKEN antes de continuar"
else
    sed -i "s/^DOCKER_GID=.*/DOCKER_GID=${DOCKER_GID}/" .env
    echo "    .env ya existia, se actualizo DOCKER_GID=${DOCKER_GID}"
fi

cat <<'FIN'

=====================================================================
Entorno base listo. Siguientes pasos:

  1. Edite .env y ponga GITHUB_USER y GITHUB_TOKEN
  2. docker compose -f docker-compose.jenkins.yml up -d --build
  3. docker cp .secretos/kubeconfig-kind jenkins:/var/jenkins_home/kubeconfig-kind
     docker restart jenkins
  4. Abra http://localhost:8080  (usuario y clave del .env)

Los tres jobs (circleguard-dev, circleguard-stage y circleguard-master)
aparecen creados solos y escanean el repositorio cada minuto.
=====================================================================
FIN

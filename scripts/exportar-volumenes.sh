#!/usr/bin/env bash
# =====================================================================
# TALLER 2 - Exportacion de los volumenes Docker para la entrega
#
#   bash scripts/exportar-volumenes.sh
#
# Genera entrega-volumenes/*.tgz con el estado de Jenkins y de las bases
# de datos, para que el evaluador restaure el entorno tal cual quedo.
# =====================================================================
set -euo pipefail

DESTINO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/entrega-volumenes"
mkdir -p "$DESTINO"

exportar() {
    local volumen="$1"
    local salida="$2"
    shift 2
    if ! docker volume inspect "$volumen" >/dev/null 2>&1; then
        echo "--  $volumen no existe, se omite"
        return
    fi
    echo "==> Exportando $volumen"
    docker run --rm \
        -v "${volumen}":/data:ro \
        -v "${DESTINO}":/backup \
        alpine tar czf "/backup/${salida}" -C /data "$@" .
    echo "    $(du -h "${DESTINO}/${salida}" | cut -f1)  ${salida}"
}

# Jenkins: se excluyen workspaces y cachés de Gradle, que pesan mucho y
# se regeneran solos en el primer build.
exportar jenkins_home jenkins_home.tgz \
    --exclude=./workspace \
    --exclude=./caches \
    --exclude=./.gradle \
    --exclude=./war

# Bases de datos de la infraestructura de desarrollo. Los nombres reales
# dependen del proyecto de compose; se detectan por patron.
for v in $(docker volume ls -q | grep -E 'postgres|neo4j|kafka' || true); do
    exportar "$v" "${v}.tgz"
done

echo
echo "Listo. Archivos en: $DESTINO"
ls -lh "$DESTINO"
cat <<'AVISO'

AVISO DE SEGURIDAD
  jenkins_home.tgz contiene las credenciales guardadas (token de GitHub y
  kubeconfig) en forma cifrada junto con la llave maestra de Jenkins.
  Publiquelo solo en un repositorio privado o en un enlace restringido, y
  revoque el token de GitHub cuando termine la calificacion.
AVISO

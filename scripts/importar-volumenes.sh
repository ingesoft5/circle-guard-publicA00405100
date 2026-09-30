#!/usr/bin/env bash
# =====================================================================
# TALLER 2 - Restauracion de los volumenes Docker entregados
#
#   bash scripts/importar-volumenes.sh
#
# Reconstruye los volumenes a partir de entrega-volumenes/*.tgz.
# Ejecutar con el contenedor de Jenkins detenido.
# =====================================================================
set -euo pipefail

ORIGEN="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/entrega-volumenes"

if [ ! -d "$ORIGEN" ]; then
    echo "No existe $ORIGEN" >&2
    exit 1
fi

docker stop jenkins >/dev/null 2>&1 || true

for archivo in "$ORIGEN"/*.tgz; do
    [ -e "$archivo" ] || { echo "No hay archivos .tgz en $ORIGEN" >&2; exit 1; }
    volumen="$(basename "$archivo" .tgz)"
    echo "==> Restaurando $volumen"
    docker volume create "$volumen" >/dev/null
    docker run --rm \
        -v "${volumen}":/data \
        -v "${ORIGEN}":/backup:ro \
        alpine sh -c "tar xzf /backup/$(basename "$archivo") -C /data"
done

echo
echo "Volumenes restaurados. Continue con:"
echo "  docker compose -f docker-compose.jenkins.yml up -d --build"

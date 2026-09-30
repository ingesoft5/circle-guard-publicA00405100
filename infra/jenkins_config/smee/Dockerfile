# Contenedor liviano que ejecuta smee-client: reenvía los eventos del
# webhook de GitHub (recibidos en un canal público de smee.io) hacia el
# endpoint /github-webhook/ de Jenkins, sin necesidad de exponer Jenkins
# a Internet.
FROM node:20-alpine

# Version fija (inmutabilidad: nada de "latest")
RUN npm install -g smee-client@5.0.0

# Valores por defecto (SMEE_URL se inyecta desde docker-compose / .env).
# smee-client 5.x recorta la "/" final del destino; Jenkins responde 302 hacia
# /github-webhook/ y el POST se pierde (405). El query string evita el recorte.
ENV SMEE_URL="" \
    SMEE_TARGET="http://jenkins:8080/github-webhook/?via=smee"

# No ejecutar como root: la imagen node trae el usuario "node"
USER node

ENTRYPOINT ["sh", "-c", "smee -u \"$SMEE_URL\" -t \"$SMEE_TARGET\""]

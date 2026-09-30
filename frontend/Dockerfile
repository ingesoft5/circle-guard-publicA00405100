# =============================================================================
# Taller Evaluativo 2 - Fase 2: Empaquetamiento Inmutable Multi-Stage
# Frontend: studytrack-frontend
# =============================================================================

# ---------- Stage 1: Builder ----------
FROM node:20-alpine AS builder
WORKDIR /app

# Cache de dependencias
COPY package.json ./
RUN npm install

COPY . .
# La URL del API se inyecta en tiempo de build (Vite la embebe en el bundle)
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=${VITE_API_URL}
# "npm run build" = tsc -b (chequeo de tipos) + vite build
RUN npm run build

# ---------- Stage 2: Runtime ----------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

# Ejecutar nginx sin root (puerto 3000 > 1024, no requiere privilegios)
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d \
 && touch /var/run/nginx.pid \
 && chown nginx:nginx /var/run/nginx.pid

USER nginx
EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]

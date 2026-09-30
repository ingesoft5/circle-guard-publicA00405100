# StudyTrack — Taller Evaluativo 2 (Jenkins + Nexus + Smee.io)

Ingeniería de Software V · Universidad Icesi · 202620 · Luis G. (A00405100)

> El proyecto anterior (CircleGuard) está preservado en la rama `circleguard-backup`
> y en el tag `circleguard-backup-v1`.

## Estructura

```
backend/     Spring Boot 3 (Java 17) · Dockerfile multi-stage · pom con distributionManagement
frontend/    React 18 + Vite · Dockerfile multi-stage · nginx.conf con fallback SPA
infra/
  jenkins_config/  Jenkins (Docker CLI + Maven) + smee-client · red cicd_network
  nexus_config/    Nexus 3 (maven-releases + docker-hosted) · healthcheck
deploy/      docker-compose que despliega las imágenes publicadas en Nexus
Jenkinsfile  Pipeline declarativo: Checkout & Test → Package & Tag → Publish → Deploy & Smoke
```

## Puertos

| Servicio | Host | Contenedor |
|---|---|---|
| Jenkins UI / agentes | 9082 / 50000 | 8080 / 50000 |
| Nexus UI / Docker registry | 9081 / 9080 | 8081 / 8082 |
| Backend / Frontend | 8080 / 3000 | 8080 / 3000 |

## Versionado inmutable

`IMAGE_TAG = <BUILD_NUMBER>-<hash corto del commit>` para las imágenes y
`1.0.0-<IMAGE_TAG>` para el JAR. Nunca se publica `:latest`.

## Levantar el ecosistema

```powershell
cd infra/jenkins_config; Copy-Item .env.example .env   # editar SMEE_CHANNEL_URL
docker compose up -d --build
cd ../nexus_config; Copy-Item .env.example .env
docker compose up -d
```

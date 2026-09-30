# StudyTrack — Taller Evaluativo 2 (Jenkins + Nexus + Smee.io)

Ingeniería de Software V · Universidad Icesi · 202620 · Luis G. (A00405100)

📄 **Documento de resultados y evidencias:**
https://docs.google.com/document/d/1oL_0hv4_brdzVSmcq_BEUquJKhjXHfaO9_-UpcfVkBg/edit?usp=sharing

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

## Cómo reproducir

Requisitos: Docker con el plugin `compose`, y puertos 8080, 3000, 9080–9082 y 50000 libres.

### 1. Levantar Jenkins + Smee (crea la red `cicd_network`) y luego Nexus

```bash
cd infra/jenkins_config
cp .env.example .env        # poner en SMEE_CHANNEL_URL un canal creado en https://smee.io/new
docker compose up -d --build

cd ../nexus_config
cp .env.example .env
docker compose up -d        # esperar a que el healthcheck quede "healthy" (~2 min)
```

### 2. Configurar Nexus (http://localhost:9081)

1. Clave inicial: `docker exec nexus cat /nexus-data/admin.password` → iniciar sesión como `admin` y cambiarla.
2. Crear repositorio **docker (hosted)** `docker-hosted` con conector HTTP en el puerto **8082**
   y *Deployment policy* = **Disable redeploy** (tags inmutables).
3. *Security → Realms*: activar **Docker Bearer Token Realm**.
4. (Recomendado) Crear un usuario de CI (p. ej. `jenkins-ci`) con un rol que solo tenga
   `nx-repository-view-maven2-maven-releases-*` y `nx-repository-view-docker-docker-hosted-*`.
5. Verificar: `docker login localhost:9080 -u jenkins-ci`.

### 3. Configurar Jenkins (http://localhost:9082)

1. Clave inicial: `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`.
2. *Manage Jenkins → Credentials → Global*, tipo *Username with password*:
   - `nexus-credentials`: usuario/clave de Nexus.
   - `github-credentials`: usuario de GitHub + token (permiso *Contents: read*).
3. *New Item → Pipeline* `studytrack-pipeline`:
   - Trigger: **GitHub hook trigger for GITScm polling**.
   - Definition: *Pipeline script from SCM* → Git → URL de este repo, credencial `github-credentials`,
     rama `*/main`, Script Path `Jenkinsfile`.

### 4. Webhook de GitHub

*Settings → Webhooks → Add webhook*: Payload URL = el mismo canal de Smee del `.env`,
Content type `application/json`, evento **push**.

### 5. Probar

Hacer `git push` a `main`: Smee reenvía el evento a Jenkins y el pipeline ejecuta las 4 etapas.
Al terminar, la app queda en http://localhost:3000 y el API en http://localhost:8080/api/tasks.

## Notas técnicas

- `maven:3.9.6-alpine` no existe en Docker Hub; se usa `maven:3.9.6-eclipse-temurin-17-alpine`.
- `plugins.txt` sin versiones fijas: las fijadas originalmente no son compatibles con el Jenkins LTS actual.
- Jenkins usa el daemon Docker del host vía `/var/run/docker.sock`; `group_add` le da acceso sin correr como root.
- `smee-client` 5.x recorta la `/` final del destino y Jenkins responde 302 (el POST se pierde → 405);
  por eso el destino es `http://jenkins:8080/github-webhook/?via=smee`.

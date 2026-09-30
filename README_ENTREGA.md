# Taller 2: pruebas y lanzamiento — CircleGuard

Guía de entrega y evaluación.
Repositorio: `https://github.com/ingesoft5/circle-guard-publicA00405100`
Ramas: `develop` (dev), `stage` (stage), `master` (producción).

---

## 1. Cómo levantar el entorno

Hay dos caminos. El **A** reconstruye el entorno desde cero en cualquier
máquina y es el recomendado. El **B** restaura los volúmenes tal como
quedaron en la máquina de desarrollo, con el historial de builds incluido.

### Requisitos previos
Docker, `kind` y `kubectl` instalados. El script verifica los tres y
muestra el comando de instalación si falta alguno.

### Camino A — reconstrucción automática (recomendado)

```bash
git clone https://github.com/ingesoft5/circle-guard-publicA00405100.git
cd circle-guard-publicA00405100
git checkout develop

bash scripts/setup-entorno.sh          # cluster kind, namespaces, kubeconfig, infra

# Editar .env y completar GITHUB_USER y GITHUB_TOKEN
docker compose -f docker-compose.jenkins.yml up -d --build

docker cp .secretos/kubeconfig-kind jenkins:/var/jenkins_home/kubeconfig-kind
docker restart jenkins
```

Jenkins queda en `http://localhost:8080`, con el usuario y la contraseña
del `.env`. **No hay asistente de primer arranque ni configuración manual.**
Al abrirlo ya están creados los tres jobs Multibranch, los plugins
instalados y las dos credenciales registradas.

### Camino B — restauración de los volúmenes entregados

```bash
bash scripts/setup-entorno.sh          # el cluster kind sí hay que crearlo
bash scripts/importar-volumenes.sh     # restaura jenkins_home y las bases
docker compose -f docker-compose.jenkins.yml up -d --build
```

El cluster de Kubernetes **no viaja dentro de un volumen**: lo crea el
script de setup. Todo lo demás (configuración de Jenkins, historial de
builds, credenciales y datos de Postgres y Neo4j) sí se restaura.

---

## 2. Qué se configura solo, y dónde está escrito

| Elemento | Archivo | Qué hace |
|---|---|---|
| Imagen de Jenkins | `jenkins/Dockerfile` | Jenkins LTS con JDK 21 más `docker`, `kubectl`, `kind`, `pytest` y `locust` |
| Plugins | `jenkins/plugins.txt` | Instalación desatendida, sin pasar por el asistente |
| Configuración | `jenkins/casc.yaml` | Usuario, credencial `github-creds` y los tres jobs Multibranch |
| Credencial de Kubernetes | `jenkins/init.groovy.d/10-kubeconfig-credential.groovy` | Registra la credencial de archivo `kubeconfig` |
| Servicio | `docker-compose.jenkins.yml` | Volumen `jenkins_home`, socket de Docker y red del cluster |
| Preparación | `scripts/setup-entorno.sh` | Cluster kind, namespaces y kubeconfig interno |
| Volúmenes | `scripts/exportar-volumenes.sh`, `scripts/importar-volumenes.sh` | Respaldo y restauración |

El detalle que hace que esto funcione dentro de un contenedor es el
kubeconfig: se genera con `kind get kubeconfig --internal`, de modo que
la dirección del API server sea el nombre del contenedor del
control-plane y no `127.0.0.1`, que dentro de Jenkins apuntaría al propio
contenedor. Por eso el compose también conecta Jenkins a la red `kind`.

---

## 3. Verificación de que el pipeline dispara solo

Los tres jobs escanean el repositorio **cada minuto**
(`periodicFolderTrigger` de un minuto en `casc.yaml`). Para comprobarlo:

```bash
git checkout develop
echo "// verificacion $(date)" >> README.md
git commit -am "test: verificacion de disparo automatico del pipeline"
git push origin develop
```

En menos de dos minutos aparece un build nuevo en `circleguard-dev` sin
tocar nada en la interfaz.

---

## 4. Los tres pipelines

| Rama | Archivo | Etapas | Punto del taller |
|---|---|---|---|
| `develop` | `Jenkinsfile.dev` | Build, pruebas unitarias, pruebas de integración, suite completa, imágenes Docker, carga en kind, despliegue en el namespace `dev`, verificación | 2 (15%) |
| `stage` | `Jenkinsfile.stage` | Build y pruebas, imágenes, despliegue en `stage`, pruebas E2E con pytest, pruebas de carga y estrés con Locust | 4 (15%) |
| `master` | `Jenkinsfile.master` | Build y pruebas unitarias, validación de las pruebas de sistema en `stage`, aprobación manual, versión semántica, despliegue en `master`, smoke tests, tag y Release Notes automáticas, rollback ante fallo | 5 (15%) |

---

## 5. Pruebas nuevas del punto 3

**Unitarias (5):**
`SymptomMapperAdvancedTest` (form), `QrTokenServiceTest` (auth),
`IdentityVaultServiceHashTest` (identity), `TemplateServiceContentTest`
(notification), `KAnonymityFilterTest` (dashboard).

**Integración (5):**
`SurveyEventPublishingIT`, `IdentityClientIT`, `PromotionStatsIT`,
`StatusChangeNotificationIT`, `SurveyEventConsumptionIT`.
Usan un broker Kafka embebido y servidores HTTP de prueba, así que
validan el contrato real entre servicios sin depender de la
infraestructura externa.

**Extremo a extremo (5):** `tests/e2e/test_flows.py`, sobre la aplicación
ya desplegada en Kubernetes.

**Rendimiento:** `tests/performance/locustfile.py`, con un escenario de
carga (50 usuarios) y uno de estrés (200 usuarios).

Para ejecutarlas fuera del pipeline:

```bash
./gradlew test                                    # unitarias e integración
python3 -m pytest tests/e2e -v                    # E2E (con los port-forward activos)
python3 -m locust -f tests/performance/locustfile.py --headless -u 50 -r 5 -t 3m
```

---

## 6. Seguridad

El archivo `jenkins_home.tgz` contiene las credenciales guardadas (token
de GitHub y kubeconfig) cifradas junto con la llave maestra de Jenkins.
Debe publicarse solo en un repositorio privado o en un enlace
restringido. El token de GitHub se revoca al terminar la calificación.

Los archivos `.env` y `.secretos/` **no se versionan**. Añadir al
`.gitignore`:

```
.env
.secretos/
entrega-volumenes/
```

## 7. Rearmar el volumen de Jenkins

El archivo `jenkins_home.tgz` viene partido en dos, porque GitHub no acepta
archivos de más de 100 MB. Antes de restaurarlo:

```bash
cat entrega-volumenes/jenkins_home.tgz.part_* > entrega-volumenes/jenkins_home.tgz
rm entrega-volumenes/jenkins_home.tgz.part_*
bash scripts/importar-volumenes.sh
```

## 8. Distribución de imágenes: por qué no se usa un registro

El pipeline no publica las imágenes en un registro privado (Nexus, Harbor,
ECR). Construye cada imagen con el demonio de Docker del host y la inyecta
en los nodos del cluster con `kind load docker-image`.

La razón es que Jenkins y el cluster kind comparten el mismo demonio de
Docker, así que un registro intermedio solo añadiría una copia de red y un
punto más de fallo. El resultado es equivalente: el `imagePullPolicy` de
los manifiestos es `IfNotPresent`, de modo que Kubernetes usa la imagen ya
cargada en el nodo y nunca intenta descargarla.

En un entorno real con varios nodos o con el cluster en otra máquina, este
paso se sustituiría por `docker push` a un registro y el `imagePullPolicy`
pasaría a `Always`. El cambio afecta solo a dos líneas del Jenkinsfile y
una del manifiesto, sin tocar la aplicación.

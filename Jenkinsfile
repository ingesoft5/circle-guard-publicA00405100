// =============================================================================
// Taller Evaluativo 2 - Fase 4: Pipeline Declarativo en Jenkins
//
//   - Versionado inmutable: IMAGE_TAG = <BUILD_NUMBER>-<hash corto del commit>
//     (JAR: 1.0.0-<IMAGE_TAG>). Nunca se publica ":latest".
//   - Las credenciales se inyectan con withCredentials usando el ID
//     "nexus-credentials" configurado en Jenkins > Credentials.
//   - El build lo dispara el webhook de GitHub reenviado por smee-client.
// =============================================================================

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }

    triggers {
        // Endpoint /github-webhook/ (plugin github), alimentado por Smee.io
        githubPush()
    }

    environment {
        // IMAGE_TAG y APP_VERSION se calculan en "Checkout & Test" (necesitan
        // el commit); por eso NO se declaran aquí (serían inmutables).

        // Registry Docker de Nexus visto por el daemon del host (socket montado)
        NEXUS_REGISTRY   = "localhost:9080"
        // Nexus visto desde el contenedor Jenkins (red cicd_network)
        NEXUS_URL        = "http://nexus:8081"
        NEXUS_MAVEN_REPO = "http://nexus:8081/repository/maven-releases/"

        NEXUS_CREDENTIALS_ID = "nexus-credentials"

        BACKEND_IMAGE  = "localhost:9080/studytrack-api"
        FRONTEND_IMAGE = "localhost:9080/studytrack-frontend"

        // Desde el contenedor Jenkins, los puertos publicados en el host
        // (Docker Desktop) se alcanzan por host.docker.internal
        SMOKE_HOST = "host.docker.internal"
        COMPOSE    = "docker compose -p studytrack -f deploy/docker-compose.yml"
    }

    stages {

        stage('Checkout & Test') {
            steps {
                checkout scm

                script {
                    def shortSha = sh(script: 'git rev-parse --short=7 HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG   = "${env.BUILD_NUMBER}-${shortSha}"
                    env.APP_VERSION = "1.0.0-${env.IMAGE_TAG}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} (${env.IMAGE_TAG})"
                    echo "Versión inmutable: imágenes=${env.IMAGE_TAG} | JAR=${env.APP_VERSION}"
                }

                dir('backend') {
                    sh 'mvn -B test'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package & Tag Inmutable') {
            steps {
                dir('backend') {
                    sh 'mvn -B -q versions:set -DnewVersion=$APP_VERSION -DgenerateBackupPoms=false'
                    sh 'mvn -B package -DskipTests'
                    sh 'docker build -t $BACKEND_IMAGE:$IMAGE_TAG .'
                }
                dir('frontend') {
                    // El build multi-stage ejecuta "npm run build" (tsc -b + vite build)
                    sh 'docker build --build-arg VITE_API_URL=http://localhost:8080 -t $FRONTEND_IMAGE:$IMAGE_TAG .'
                }
            }
        }

        stage('Publish to Nexus') {
            steps {
                withCredentials([usernamePassword(credentialsId: "${NEXUS_CREDENTIALS_ID}",
                                                  usernameVariable: 'NEXUS_USER',
                                                  passwordVariable: 'NEXUS_PASS')]) {
                    // JAR -> maven-releases (ci-settings.xml lee NEXUS_USER/NEXUS_PASS del entorno)
                    dir('backend') {
                        sh 'mvn -B deploy -DskipTests -s ci-settings.xml -Dnexus.url=$NEXUS_URL'
                    }
                    // Imágenes -> docker-hosted
                    sh 'echo "$NEXUS_PASS" | docker login $NEXUS_REGISTRY -u "$NEXUS_USER" --password-stdin'
                    sh 'docker push $BACKEND_IMAGE:$IMAGE_TAG'
                    sh 'docker push $FRONTEND_IMAGE:$IMAGE_TAG'
                }
            }
        }

        stage('Deploy & Smoke Test') {
            steps {
                // Descarga desde Nexus la versión recién publicada y despliega
                sh '$COMPOSE pull'
                sh '$COMPOSE up -d --remove-orphans'

                sh '''
                    for i in $(seq 1 20); do
                      if curl -fsS "http://$SMOKE_HOST:8080/api/tasks"; then
                        echo ""
                        echo "Smoke test backend OK (intento $i)"
                        curl -fsS -o /dev/null "http://$SMOKE_HOST:3000/" && echo "Smoke test frontend OK"
                        exit 0
                      fi
                      echo "Backend aún no responde (intento $i/20), reintentando en 5s..."
                      sleep 5
                    done
                    echo "Smoke test FALLIDO"
                    $COMPOSE logs --tail=50 backend
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            sh 'docker logout $NEXUS_REGISTRY || true'
        }
        success {
            echo "Pipeline finalizado en verde. Artefactos publicados con tag: ${env.IMAGE_TAG}"
        }
        failure {
            echo "El pipeline falló. Revise los logs de la etapa correspondiente antes de reintentar."
        }
    }
}

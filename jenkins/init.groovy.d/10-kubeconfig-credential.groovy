// =====================================================================
// Registra la credencial de tipo archivo "kubeconfig" que usan los tres
// Jenkinsfile en sus bloques withCredentials([file(credentialsId:
// 'kubeconfig', ...)]).
//
// El archivo lo genera scripts/setup-entorno.sh con
// `kind get kubeconfig --internal`, de modo que la URL del API server sea
// alcanzable desde dentro del contenedor de Jenkins y no 127.0.0.1.
// =====================================================================
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl
import hudson.util.Secret
import jenkins.model.Jenkins

def archivo = new File('/var/jenkins_home/kubeconfig-kind')

if (!archivo.exists()) {
    println '[taller2] kubeconfig-kind no existe todavia. Ejecute scripts/setup-entorno.sh y reinicie Jenkins.'
    return
}

def proveedor = SystemCredentialsProvider.getInstance()
def dominio = Domain.global()
def existentes = proveedor.getCredentials(dominio)

// Si ya estaba registrada la quitamos para dejar siempre la version vigente.
existentes.findAll { it.id == 'kubeconfig' }.each { proveedor.removeCredentials(dominio, it) }

def bytes = com.cloudbees.plugins.credentials.SecretBytes.fromBytes(archivo.bytes)
def credencial = new FileCredentialsImpl(
        CredentialsScope.GLOBAL,
        'kubeconfig',
        'kubeconfig del cluster kind "circleguard"',
        'kubeconfig',
        bytes
)

proveedor.addCredentials(dominio, credencial)
proveedor.save()
println '[taller2] Credencial "kubeconfig" registrada correctamente.'

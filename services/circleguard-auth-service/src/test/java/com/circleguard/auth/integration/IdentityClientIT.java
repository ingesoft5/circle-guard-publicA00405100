package com.circleguard.auth.integration;

import com.circleguard.auth.client.IdentityClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRUEBA DE INTEGRACION 2 - Taller 2
 *
 * Servicios involucrados: auth-service --REST--> identity-service
 *
 * Que valida: el contrato HTTP real que usa el login para anonimizar al
 * usuario. IdentityClient trae fija la URL http://localhost:8083/api/v1/
 * identities/map, asi que se levanta un servidor HTTP real en ese puerto
 * que hace el papel del identity-service. Se comprueba el metodo, la ruta,
 * el cuerpo enviado ({"realIdentity": ...}) y la lectura de "anonymousId"
 * en la respuesta, que son los tres puntos donde los dos servicios se
 * pueden desalinear sin que ninguna prueba unitaria lo note.
 */
class IdentityClientIT {

    private HttpServer identityStub;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();

    @BeforeEach
    void startStub() throws Exception {
        identityStub = HttpServer.create(new InetSocketAddress(8083), 0);
    }

    @AfterEach
    void stopStub() {
        identityStub.stop(0);
    }

    private void respond(int status, String json) {
        identityStub.createContext("/api/v1/identities/map", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        identityStub.start();
    }

    @Test
    @DisplayName("El login obtiene el anonymousId que devuelve identity-service")
    void resolvesAnonymousIdOverHttp() {
        UUID expected = UUID.randomUUID();
        respond(200, "{\"anonymousId\":\"" + expected + "\"}");

        UUID result = new IdentityClient().getAnonymousId("a00405100@u.icesi.edu.co");

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("La peticion usa POST a /api/v1/identities/map con la identidad real en el cuerpo")
    void sendsCorrectRequestContract() {
        respond(200, "{\"anonymousId\":\"" + UUID.randomUUID() + "\"}");

        new IdentityClient().getAnonymousId("estudiante@u.icesi.edu.co");

        assertEquals("POST", receivedMethod.get());
        assertEquals("/api/v1/identities/map", receivedPath.get());
        assertTrue(receivedBody.get().contains("\"realIdentity\""),
                "identity-service espera el campo realIdentity");
        assertTrue(receivedBody.get().contains("estudiante@u.icesi.edu.co"));
    }

    @Test
    @DisplayName("Si identity-service falla con 500, el error llega al llamador y no se inventa un ID")
    void propagatesServerErrors() {
        respond(500, "{\"error\":\"vault unavailable\"}");

        assertThrows(HttpServerErrorException.class,
                () -> new IdentityClient().getAnonymousId("cualquiera@u.icesi.edu.co"),
                "Un login sin anonymousId real romperia la anonimizacion");
    }
}

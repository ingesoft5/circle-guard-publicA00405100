package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRUEBA UNITARIA 2 - Taller 2
 *
 * Componente bajo prueba: QrTokenService (auth-service).
 *
 * Por que importa: el token QR es la credencial de entrada al campus. Debe
 * llevar el anonymousId como subject (nunca el nombre real, por FERPA), estar
 * firmado con HS256 y expirar rapido, porque un QR de larga vida se puede
 * compartir por captura de pantalla y rompe el control de acceso.
 */
class QrTokenServiceTest {

    private static final String SECRET = "clave-de-prueba-taller2-con-al-menos-32-bytes-de-longitud";

    @Test
    @DisplayName("El token generado no es nulo y tiene las tres partes de un JWT")
    void generatesWellFormedJwt() {
        QrTokenService service = new QrTokenService(SECRET, 60_000L);

        String token = service.generateQrToken(UUID.randomUUID());

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length,
                "Un JWT compacto debe tener header, payload y firma");
    }

    @Test
    @DisplayName("El subject del token es el anonymousId, nunca la identidad real")
    void subjectCarriesAnonymousId() {
        QrTokenService service = new QrTokenService(SECRET, 60_000L);
        UUID anonymousId = UUID.randomUUID();

        String token = service.generateQrToken(anonymousId);
        Claims claims = parse(token);

        assertEquals(anonymousId.toString(), claims.getSubject(),
                "Exponer otra cosa que el anonymousId violaria la anonimizacion del sistema");
    }

    @Test
    @DisplayName("El token expira dentro de la ventana configurada")
    void honoursConfiguredExpiration() {
        long ttlMs = 60_000L;
        QrTokenService service = new QrTokenService(SECRET, ttlMs);

        String token = service.generateQrToken(UUID.randomUUID());
        Claims claims = parse(token);

        Date now = new Date();
        assertTrue(claims.getExpiration().after(now),
                "Un token recien emitido debe seguir vigente");
        assertTrue(claims.getExpiration().getTime() - now.getTime() <= ttlMs + 1_000,
                "La expiracion no debe exceder el TTL configurado");
    }

    @Test
    @DisplayName("Un token ya expirado no se puede validar")
    void rejectsExpiredToken() {
        QrTokenService service = new QrTokenService(SECRET, -1_000L);

        String token = service.generateQrToken(UUID.randomUUID());

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> parse(token),
                "Un QR vencido debe ser rechazado en la puerta de acceso");
    }

    @Test
    @DisplayName("Dos usuarios distintos reciben tokens distintos")
    void tokensAreUserSpecific() {
        QrTokenService service = new QrTokenService(SECRET, 60_000L);

        String first = service.generateQrToken(UUID.randomUUID());
        String second = service.generateQrToken(UUID.randomUUID());

        assertNotEquals(first, second,
                "Tokens iguales permitirian suplantacion entre estudiantes");
    }

    @Test
    @DisplayName("Una clave debil es rechazada al construir el servicio")
    void rejectsWeakSecret() {
        assertThrows(WeakKeyException.class, () -> new QrTokenService("corta", 60_000L),
                "HS256 exige al menos 256 bits de clave");
    }

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

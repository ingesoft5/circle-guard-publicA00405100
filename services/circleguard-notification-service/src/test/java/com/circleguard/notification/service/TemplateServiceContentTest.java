package com.circleguard.notification.service;

import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRUEBA UNITARIA 4 - Taller 2
 *
 * Componente bajo prueba: TemplateService (notification-service).
 *
 * Por que importa: es el ultimo eslabon de la cadena de contencion. Cuando el
 * promotion-service emite promotion.status.changed, este componente compone el
 * mensaje que el estudiante ve. Si el texto no dice el estado correcto o el
 * deep link no llega, el usuario no sabe que debe aislarse y la cadena de
 * contencion se rompe aunque todo lo demas haya funcionado.
 *
 * Tambien se prueba la degradacion: generateEmailContent captura cualquier
 * excepcion de FreeMarker y cae a un texto plano. Ese camino de respaldo debe
 * seguir mencionando el estado.
 */
class TemplateServiceContentTest {

    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService(new Configuration(Configuration.getVersion()));
        ReflectionTestUtils.setField(service, "testingUrl", "https://circleguard.test/testing");
        ReflectionTestUtils.setField(service, "isolationUrl", "https://circleguard.test/isolation");
        ReflectionTestUtils.setField(service, "guidelinesDeepLink", "circleguard://guidelines");
    }

    @Test
    @DisplayName("El push de SUSPECT indica el estado y dirige a los pasos de aislamiento")
    void pushForSuspectIsActionable() {
        String content = service.generatePushContent("SUSPECT");

        assertTrue(content.contains("SUSPECT"), "El estudiante debe leer su estado");
        assertTrue(content.toLowerCase().contains("isolation"),
                "El push debe decirle que hacer, no solo que paso");
    }

    @Test
    @DisplayName("El push de PROBABLE explica que el origen es exposicion por area")
    void pushForProbableExplainsCause() {
        String content = service.generatePushContent("PROBABLE");

        assertTrue(content.contains("PROBABLE"));
        assertTrue(content.toLowerCase().contains("exposure"),
                "Sin la causa el usuario no entiende por que fue restringido");
    }

    @Test
    @DisplayName("Un estado no contemplado usa el mensaje generico sin perder el estado")
    void pushFallsBackGracefully() {
        String content = service.generatePushContent("RECOVERED");

        assertTrue(content.contains("RECOVERED"),
                "Aun sin plantilla especifica el estado debe viajar en el mensaje");
    }

    @Test
    @DisplayName("Solo los estados de riesgo llevan deep link a los lineamientos")
    void metadataCarriesDeepLinkOnlyWhenRelevant() {
        assertEquals(Map.of("url", "circleguard://guidelines"),
                service.generatePushMetadata("SUSPECT"));
        assertEquals(Map.of("url", "circleguard://guidelines"),
                service.generatePushMetadata("PROBABLE"));

        assertTrue(service.generatePushMetadata("ACTIVE").isEmpty(),
                "Un usuario ACTIVE no debe recibir lineamientos de aislamiento");
    }

    @Test
    @DisplayName("El SMS incluye el estado y remite al correo para el detalle")
    void smsIsShortAndPointsToEmail() {
        String sms = service.generateSmsContent("CONFIRMED");

        assertTrue(sms.contains("CONFIRMED"));
        assertTrue(sms.toLowerCase().contains("email"));
        assertTrue(sms.length() <= 320,
                "Un SMS largo se fragmenta y encarece el envio masivo");
    }

    @Test
    @DisplayName("Si la plantilla FreeMarker falla, el correo cae a texto plano con el estado")
    void emailDegradesToPlainText() {
        // La Configuration de prueba no tiene cargado health_alert.ftl, asi que
        // se ejercita deliberadamente el camino de respaldo del componente.
        String email = service.generateEmailContent("SUSPECT", "Luis");

        assertTrue(email.contains("SUSPECT"),
                "El respaldo nunca debe dejar al usuario sin saber su estado");
        assertFalse(email.isBlank());
    }
}

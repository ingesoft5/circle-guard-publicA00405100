package com.circleguard.dashboard.integration;

import com.circleguard.dashboard.client.PromotionClient;
import com.circleguard.dashboard.service.AnalyticsService;
import com.circleguard.dashboard.service.KAnonymityFilter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * PRUEBA DE INTEGRACION 3 - Taller 2
 *
 * Servicios involucrados: dashboard-service --REST--> promotion-service
 *
 * Que valida: la cadena completa AnalyticsService -> PromotionClient (HTTP
 * real) -> KAnonymityFilter. Un servidor HTTP hace de promotion-service. Se
 * comprueba que (1) las estadisticas viajan por la ruta correcta, (2) el
 * filtro de privacidad se aplica sobre datos reales recibidos por red y (3)
 * si promotion-service esta caido el dashboard degrada con un mensaje de
 * error en vez de propagar una excepcion.
 */
class PromotionStatsIT {

    private HttpServer promotionStub;
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private PromotionClient client;
    private AnalyticsService service;

    @BeforeEach
    void setUp() throws Exception {
        promotionStub = HttpServer.create(new InetSocketAddress(0), 0);
        client = new PromotionClient();
        service = new AnalyticsService(mock(JdbcTemplate.class), client, new KAnonymityFilter());
        pointClientTo("http://localhost:" + promotionStub.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        promotionStub.stop(0);
    }

    private void pointClientTo(String url) {
        ReflectionTestUtils.setField(client, "promotionServiceUrl", url);
    }

    private void serve(String path, String json) {
        promotionStub.createContext(path, exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        promotionStub.start();
    }

    @Test
    @DisplayName("El resumen del campus se lee de /api/v1/health-status/stats")
    void campusSummaryTravelsOverHttp() {
        serve("/api/v1/health-status/stats", "{\"totalUsers\":500,\"activeCount\":480,\"confirmedCount\":20}");

        Map<String, Object> summary = service.getCampusSummary();

        assertEquals("/api/v1/health-status/stats", requestedPath.get());
        assertEquals(500, ((Number) summary.get("totalUsers")).intValue());
        assertEquals(20, ((Number) summary.get("confirmedCount")).intValue());
    }

    @Test
    @DisplayName("Un departamento pequeno recibido de promotion llega enmascarado al dashboard")
    void smallDepartmentIsMaskedEndToEnd() {
        serve("/api/v1/health-status/stats/department/Musica",
                "{\"department\":\"Musica\",\"totalUsers\":3,\"confirmedCount\":1}");

        Map<String, Object> stats = service.getDepartmentStats("Musica");

        assertEquals("/api/v1/health-status/stats/department/Musica", requestedPath.get());
        assertEquals("<5", stats.get("totalUsers"),
                "Con menos de 5 personas no se puede publicar ningun dato");
        assertTrue(!stats.containsKey("confirmedCount"));
    }

    @Test
    @DisplayName("Con promotion-service caido el dashboard devuelve un error controlado")
    void degradesWhenPromotionIsDown() {
        promotionStub.start();
        pointClientTo("http://localhost:1");   // puerto cerrado

        Map<String, Object> summary = service.getCampusSummary();

        assertEquals("Service unavailable", summary.get("error"),
                "El dashboard no debe caerse por un servicio aguas abajo");
    }
}

package com.circleguard.dashboard.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRUEBA UNITARIA 5 - Taller 2
 *
 * Componente bajo prueba: KAnonymityFilter (dashboard-service).
 *
 * Por que importa: el dashboard publica estadisticas por departamento y
 * edificio. En un departamento de tres personas, decir "1 caso CONFIRMED"
 * identifica al individuo por descarte. Este filtro es el control tecnico que
 * hace cumplir la promesa de privacidad del README (FR-23), y es el unico
 * punto donde se puede fugar informacion personal de un grupo pequeno.
 *
 * Se prueban las dos rutas de enmascaramiento (grupo completo y campo
 * individual) y, sobre todo, que NO enmascare de mas: un dashboard que oculta
 * todo es tan inutil como uno que expone todo.
 */
class KAnonymityFilterTest {

    private final KAnonymityFilter filter = new KAnonymityFilter();

    @Test
    @DisplayName("Un grupo con menos de K usuarios se enmascara por completo")
    void masksEntireGroupBelowThreshold() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("department", "Ingenieria Telematica");
        stats.put("totalUsers", 3);
        stats.put("confirmedCount", 1);

        Map<String, Object> result = filter.apply(stats);

        assertEquals("<5", result.get("totalUsers"));
        assertTrue(result.containsKey("note"),
                "Debe explicarse por que no hay datos, en vez de devolver ceros enganosos");
        assertTrue(!result.containsKey("confirmedCount"),
                "Ningun conteo del grupo pequeno puede sobrevivir al enmascaramiento");
    }

    @Test
    @DisplayName("El nombre del departamento se conserva para que el dashboard siga navegable")
    void preservesGroupLabelWhenMasking() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("department", "Musica");
        stats.put("totalUsers", 2);

        Map<String, Object> result = filter.apply(stats);

        assertEquals("Musica", result.get("department"));
    }

    @Test
    @DisplayName("Un grupo grande con un conteo pequeno enmascara solo ese conteo")
    void masksIndividualCountsOnly() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 400);
        stats.put("confirmedCount", 2);
        stats.put("activeCount", 390);

        Map<String, Object> result = filter.apply(stats);

        assertEquals("<5", result.get("confirmedCount"),
                "Dos casos en un grupo grande siguen siendo identificables");
        assertEquals(390, result.get("activeCount"),
                "Los conteos por encima de K deben pasar intactos");
        assertEquals(400, result.get("totalUsers"));
    }

    @Test
    @DisplayName("Un conteo en cero no se enmascara, porque cero no identifica a nadie")
    void doesNotMaskZero() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 100);
        stats.put("confirmedCount", 0);

        Map<String, Object> result = filter.apply(stats);

        assertEquals(0, result.get("confirmedCount"),
                "Enmascarar el cero ocultaria la buena noticia sin ganar privacidad");
    }

    @Test
    @DisplayName("El umbral K es configurable para escenarios mas estrictos")
    void honoursCustomThreshold() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 100);
        stats.put("confirmedCount", 8);

        assertEquals(8, filter.apply(stats, 5).get("confirmedCount"));
        assertEquals("<10", filter.apply(stats, 10).get("confirmedCount"));
    }

    @Test
    @DisplayName("Una entrada nula devuelve un mapa vacio sin lanzar excepcion")
    void handlesNullInput() {
        assertTrue(filter.apply(null).isEmpty(),
                "El dashboard no debe caerse si el promotion-service responde vacio");
    }
}

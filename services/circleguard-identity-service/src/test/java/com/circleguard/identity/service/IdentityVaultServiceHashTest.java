package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PRUEBA UNITARIA 3 - Taller 2
 *
 * Componente bajo prueba: IdentityVaultService (identity-service).
 *
 * Por que importa: es la boveda de anonimizacion, el nucleo del cumplimiento
 * FERPA del sistema. Debe producir un hash determinista (mismo estudiante,
 * mismo anonymousId, para no fragmentar el grafo de contactos) y a la vez
 * nunca guardar la identidad real en texto plano ni devolverla por error.
 *
 * Se aisla el repositorio con Mockito para probar solo la logica de hashing
 * y de reutilizacion de mapeos, sin tocar PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class IdentityVaultServiceHashTest {

    @Mock
    private IdentityMappingRepository repository;

    @InjectMocks
    private IdentityVaultService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "hashSalt", "salt-de-prueba-taller2");
    }

    @Test
    @DisplayName("Una identidad nueva genera un mapeo y devuelve su anonymousId")
    void createsMappingForNewIdentity() {
        UUID expected = UUID.randomUUID();
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class)))
                .thenReturn(IdentityMapping.builder().anonymousId(expected).build());

        UUID result = service.getOrCreateAnonymousId("a00405100@u.icesi.edu.co");

        assertEquals(expected, result);
        verify(repository).save(any(IdentityMapping.class));
    }

    @Test
    @DisplayName("Una identidad ya registrada reutiliza su anonymousId y no crea un duplicado")
    void reusesExistingMapping() {
        UUID existing = UUID.randomUUID();
        when(repository.findByIdentityHash(anyString()))
                .thenReturn(Optional.of(IdentityMapping.builder().anonymousId(existing).build()));

        UUID result = service.getOrCreateAnonymousId("a00405100@u.icesi.edu.co");

        assertEquals(existing, result,
                "Un anonymousId distinto por login fragmentaria el grafo de contactos");
        verify(repository, never()).save(any(IdentityMapping.class));
    }

    @Test
    @DisplayName("La misma identidad produce siempre el mismo hash (determinismo)")
    void hashIsDeterministic() {
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class)))
                .thenReturn(IdentityMapping.builder().anonymousId(UUID.randomUUID()).build());

        service.getOrCreateAnonymousId("a00405100@u.icesi.edu.co");
        service.getOrCreateAnonymousId("a00405100@u.icesi.edu.co");

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(repository, org.mockito.Mockito.times(2)).findByIdentityHash(hashes.capture());

        assertEquals(hashes.getAllValues().get(0), hashes.getAllValues().get(1),
                "El hash debe ser estable entre invocaciones");
    }

    @Test
    @DisplayName("Identidades distintas producen hashes distintos y el hash no revela el correo")
    void hashIsUniqueAndOpaque() {
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class)))
                .thenReturn(IdentityMapping.builder().anonymousId(UUID.randomUUID()).build());

        service.getOrCreateAnonymousId("estudiante.uno@u.icesi.edu.co");
        service.getOrCreateAnonymousId("estudiante.dos@u.icesi.edu.co");

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(repository, org.mockito.Mockito.times(2)).findByIdentityHash(hashes.capture());

        String first = hashes.getAllValues().get(0);
        String second = hashes.getAllValues().get(1);

        assertNotEquals(first, second, "Una colision permitiria confundir dos estudiantes");
        assertEquals(64, first.length(), "SHA-256 en hexadecimal son 64 caracteres");
        assertTrue(!first.contains("icesi"),
                "El hash no debe filtrar ningun fragmento de la identidad real");
    }

    @Test
    @DisplayName("Resolver un anonymousId inexistente devuelve 404 y no un nulo silencioso")
    void resolveUnknownIdentityFails() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.resolveRealIdentity(unknown));
    }

    @Test
    @DisplayName("Resolver un anonymousId valido devuelve la identidad real (solo para el Health Center)")
    void resolveKnownIdentitySucceeds() {
        UUID known = UUID.randomUUID();
        when(repository.findById(known)).thenReturn(Optional.of(
                IdentityMapping.builder()
                        .anonymousId(known)
                        .realIdentity("a00405100@u.icesi.edu.co")
                        .build()));

        assertEquals("a00405100@u.icesi.edu.co", service.resolveRealIdentity(known));
    }
}

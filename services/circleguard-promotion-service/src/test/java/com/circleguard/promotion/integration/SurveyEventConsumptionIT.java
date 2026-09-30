package com.circleguard.promotion.integration;

import com.circleguard.promotion.listener.SurveyListener;
import com.circleguard.promotion.service.HealthStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * PRUEBA DE INTEGRACION 5 - Taller 2
 *
 * Servicios involucrados: form-service --Kafka--> promotion-service
 * Topicos: survey.submitted y certificate.validated
 *
 * Que valida: el otro extremo del contrato que prueba SurveyEventPublishingIT
 * en form-service. Aqui el evento se construye con la forma que emite
 * HealthSurveyService (anonymousId como UUID, hasSymptoms, timestamp), viaja
 * por un broker embebido y SurveyListener debe traducirlo en la transicion de
 * estado correcta: SUSPECT con sintomas, ACTIVE al aprobar un certificado, y
 * NINGUNA transicion para una encuesta sin sintomas.
 */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = {"survey.submitted", "certificate.validated"})
@DirtiesContext
class SurveyEventConsumptionIT {

    @Configuration
    static class TestConfig { }

    @Autowired
    private EmbeddedKafkaBroker broker;

    private HealthStatusService healthStatusService;
    private KafkaMessageListenerContainer<String, String> container;
    private KafkaTemplate<String, Object> producer;

    @BeforeEach
    void setUp() {
        healthStatusService = mock(HealthStatusService.class);
        SurveyListener listener = new SurveyListener(healthStatusService);
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps("promo-it-" + UUID.randomUUID(), "true", broker));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ContainerProperties props = new ContainerProperties("survey.submitted", "certificate.validated");
        props.setMessageListener((MessageListener<String, String>) record -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> event = mapper.readValue(record.value(), Map.class);
                if ("survey.submitted".equals(record.topic())) {
                    listener.onSurveySubmitted(event);
                } else {
                    listener.onCertificateValidated(event);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        container = new KafkaMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<String, String>(consumerProps), props);
        container.start();
        ContainerTestUtils.waitForAssignment(container, 2 * broker.getPartitionsPerTopic());

        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(broker));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @AfterEach
    void tearDown() {
        container.stop();
    }

    private void survey(UUID anonymousId, boolean hasSymptoms) {
        // Misma forma que HealthSurveyService: anonymousId es un UUID, no un String
        Map<String, Object> event = Map.of(
                "anonymousId", anonymousId,
                "hasSymptoms", hasSymptoms,
                "timestamp", System.currentTimeMillis());
        producer.send("survey.submitted", anonymousId.toString(), event);
    }

    @Test
    @DisplayName("Una encuesta con sintomas promueve al usuario a SUSPECT")
    void symptomsPromoteToSuspect() {
        UUID user = UUID.randomUUID();

        survey(user, true);

        verify(healthStatusService, timeout(15_000)).updateStatus(user.toString(), "SUSPECT");
    }

    @Test
    @DisplayName("Una encuesta sin sintomas no cambia el estado del usuario")
    void noSymptomsKeepsStatus() {
        UUID healthy = UUID.randomUUID();
        UUID marker = UUID.randomUUID();

        survey(healthy, false);
        survey(marker, true);   // centinela: llega despues de la encuesta sana

        verify(healthStatusService, timeout(15_000)).updateStatus(marker.toString(), "SUSPECT");
        verify(healthStatusService, never()).updateStatus(eq(healthy.toString()), anyString());
    }

    @Test
    @DisplayName("Un certificado APPROVED restaura al usuario a ACTIVE")
    void approvedCertificateRestoresActive() {
        UUID user = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "anonymousId", user,
                "status", "APPROVED",
                "adminId", UUID.randomUUID(),
                "timestamp", System.currentTimeMillis());

        producer.send("certificate.validated", user.toString(), event);

        verify(healthStatusService, timeout(15_000)).updateStatus(user.toString(), "ACTIVE");
    }
}

package com.circleguard.notification.integration;

import com.circleguard.notification.service.ExposureNotificationListener;
import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
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
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PRUEBA DE INTEGRACION 4 - Taller 2
 *
 * Servicios involucrados: promotion-service --Kafka--> notification-service
 * Topico: promotion.status.changed
 *
 * Que valida: que el evento con la forma EXACTA que emite HealthStatusService
 * (anonymousId, status, timestamp), serializado a JSON y transportado por un
 * broker Kafka embebido, sea interpretado por ExposureNotificationListener y
 * dispare el despacho multicanal y la sincronizacion con el LMS. Tambien que
 * un cambio a ACTIVE NO genere alertas: notificar una recuperacion como si
 * fuera un riesgo seria una falla de contencion.
 */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = {"promotion.status.changed"})
@DirtiesContext
class StatusChangeNotificationIT {

    @Configuration
    static class TestConfig { }

    private static final String TOPIC = "promotion.status.changed";

    @Autowired
    private EmbeddedKafkaBroker broker;

    private NotificationDispatcher dispatcher;
    private LmsService lmsService;
    private KafkaMessageListenerContainer<String, String> container;
    private KafkaTemplate<String, Object> producer;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        lmsService = mock(LmsService.class);
        when(lmsService.syncRemoteAttendance(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        ExposureNotificationListener listener =
                new ExposureNotificationListener(dispatcher, new ObjectMapper(), lmsService);

        Map<String, Object> consumerProps = new HashMap<>(
                KafkaTestUtils.consumerProps("notif-it-" + UUID.randomUUID(), "true", broker));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        ContainerProperties props = new ContainerProperties(TOPIC);
        props.setMessageListener((MessageListener<String, String>) record ->
                listener.handleStatusChange(record.value()));
        container = new KafkaMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<String, String>(consumerProps), props);
        container.start();
        ContainerTestUtils.waitForAssignment(container, broker.getPartitionsPerTopic());

        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(broker));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @AfterEach
    void tearDown() {
        container.stop();
    }

    private void publish(String anonymousId, String status) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("anonymousId", anonymousId);
        payload.put("status", status);
        payload.put("timestamp", System.currentTimeMillis());
        producer.send(TOPIC, anonymousId, payload);
    }

    @Test
    @DisplayName("Un usuario que pasa a SUSPECT recibe notificacion y se sincroniza con el LMS")
    void suspectTriggersDispatchAndLmsSync() {
        String user = UUID.randomUUID().toString();

        publish(user, "SUSPECT");

        verify(dispatcher, timeout(15_000)).dispatch(user, "SUSPECT");
        verify(lmsService, timeout(15_000)).syncRemoteAttendance(user, "SUSPECT");
    }

    @Test
    @DisplayName("Un cambio a ACTIVE no genera alertas de riesgo")
    void activeDoesNotNotify() {
        String recovered = UUID.randomUUID().toString();
        String marker = UUID.randomUUID().toString();

        publish(recovered, "ACTIVE");
        publish(marker, "PROBABLE");   // mensaje centinela: llega despues del ACTIVE

        verify(dispatcher, timeout(15_000)).dispatch(marker, "PROBABLE");
        verify(dispatcher, never()).dispatch(eq(recovered), anyString());
        verify(lmsService, never()).syncRemoteAttendance(eq(recovered), anyString());
    }
}

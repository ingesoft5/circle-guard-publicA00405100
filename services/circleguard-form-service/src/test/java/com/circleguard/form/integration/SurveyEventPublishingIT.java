package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.service.QuestionnaireService;
import com.circleguard.form.service.SymptomMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PRUEBA DE INTEGRACION 1 - Taller 2
 *
 * Servicios involucrados: form-service  --Kafka-->  promotion-service
 * Topicos: survey.submitted, certificate.validated
 *
 * Que valida: que el form-service publique realmente sobre un broker Kafka
 * (embebido, no un mock) el evento que el promotion-service espera, con el
 * nombre de topico, la clave de particion y el esquema de payload correctos.
 *
 * Por que no basta una prueba unitaria: un mock de KafkaTemplate acepta
 * cualquier objeto. Aqui el mensaje se serializa de verdad, viaja por el
 * broker y se deserializa del otro lado, que es donde aparecen los errores
 * reales de contrato (un UUID que no serializa, un campo renombrado, un
 * topico mal escrito).
 */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = {"survey.submitted", "certificate.validated"})
@DirtiesContext
class SurveyEventPublishingIT {

    @Configuration
    static class TestConfig {
    }

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HealthSurveyRepository repository;
    private QuestionnaireService questionnaireService;
    private HealthSurveyService service;
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(embeddedKafka));
        // KafkaTestUtils usa serializadores de entero por defecto; el sistema real
        // usa claves String (el anonymousId), asi que se fuerzan explicitamente.
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        KafkaTemplate<String, Object> kafkaTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        repository = mock(HealthSurveyRepository.class);
        questionnaireService = mock(QuestionnaireService.class);

        service = new HealthSurveyService(repository, questionnaireService, new SymptomMapper(), kafkaTemplate);

        Map<String, Object> consumerProps =
                new HashMap<>(KafkaTestUtils.consumerProps("taller2-it-group", "true", embeddedKafka));
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    /**
     * Los tests comparten el broker embebido, asi que se busca el mensaje por su
     * clave (anonymousId, unico por test) en vez de asumir que es el unico.
     */
    private ConsumerRecord<String, String> recordFor(String topic, String key) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> r : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2))) {
                if (topic.equals(r.topic()) && key.equals(r.key())) {
                    return r;
                }
            }
        }
        throw new AssertionError("No llego ningun mensaje a " + topic + " con clave " + key);
    }

    @Test
    @DisplayName("Una encuesta con fiebre publica survey.submitted con hasSymptoms=true")
    void publishesSurveySubmittedWithSymptoms() throws Exception {
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "survey.submitted");

        UUID anonymousId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(
                Questionnaire.builder()
                        .id(UUID.randomUUID())
                        .title("Encuesta diaria")
                        .version(1)
                        .isActive(true)
                        .questions(List.of(Question.builder()
                                .id(questionId)
                                .text("Do you have a fever?")
                                .type(QuestionType.YES_NO)
                                .orderIndex(0)
                                .build()))
                        .build()));

        HealthSurvey incoming = HealthSurvey.builder()
                .anonymousId(anonymousId)
                .responses(Map.of(questionId.toString(), "YES"))
                .build();

        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> {
            HealthSurvey s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        service.submitSurvey(incoming);

        ConsumerRecord<String, String> record =
                recordFor("survey.submitted", anonymousId.toString());

        assertEquals(anonymousId.toString(), record.key(),
                "La clave debe ser el anonymousId para garantizar orden por usuario en la particion");

        JsonNode payload = objectMapper.readTree(record.value());
        assertEquals(anonymousId.toString(), payload.get("anonymousId").asText());
        assertTrue(payload.get("hasSymptoms").asBoolean(),
                "El promotion-service solo promueve a SUSPECT si este campo llega en true");
        assertTrue(payload.has("timestamp"),
                "El timestamp es necesario para la ventana temporal de 14 dias del grafo");
    }

    @Test
    @DisplayName("Una encuesta sin sintomas publica el evento con hasSymptoms=false")
    void publishesSurveySubmittedWithoutSymptoms() throws Exception {
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "survey.submitted");

        UUID anonymousId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(
                Questionnaire.builder()
                        .id(UUID.randomUUID())
                        .title("Encuesta diaria")
                        .version(1)
                        .isActive(true)
                        .questions(List.of(Question.builder()
                                .id(questionId)
                                .text("Do you have a cough?")
                                .type(QuestionType.YES_NO)
                                .orderIndex(0)
                                .build()))
                        .build()));

        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> {
            HealthSurvey s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        service.submitSurvey(HealthSurvey.builder()
                .anonymousId(anonymousId)
                .responses(Map.of(questionId.toString(), "NO"))
                .build());

        ConsumerRecord<String, String> record =
                recordFor("survey.submitted", anonymousId.toString());

        JsonNode payload = objectMapper.readTree(record.value());
        assertFalse(payload.get("hasSymptoms").asBoolean(),
                "Un evento con hasSymptoms=true aqui pondria en cuarentena a un estudiante sano");
    }

    @Test
    @DisplayName("Validar un certificado como APPROVED publica certificate.validated")
    void publishesCertificateValidatedOnApproval() throws Exception {
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "certificate.validated");

        UUID anonymousId = UUID.randomUUID();
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(repository.findById(surveyId)).thenReturn(Optional.of(HealthSurvey.builder()
                .id(surveyId)
                .anonymousId(anonymousId)
                .validationStatus(ValidationStatus.PENDING)
                .build()));
        when(repository.save(any(HealthSurvey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        ConsumerRecord<String, String> record =
                recordFor("certificate.validated", anonymousId.toString());

        JsonNode payload = objectMapper.readTree(record.value());
        assertEquals(anonymousId.toString(), payload.get("anonymousId").asText());
        assertEquals("APPROVED", payload.get("status").asText(),
                "El promotion-service restaura al estado ACTIVE solo con este valor exacto");
    }
}

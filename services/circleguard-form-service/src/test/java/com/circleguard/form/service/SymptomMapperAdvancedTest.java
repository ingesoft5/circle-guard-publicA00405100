package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRUEBA UNITARIA 1 - Taller 2
 *
 * Componente bajo prueba: SymptomMapper (form-service).
 *
 * Por que importa: este componente decide si una encuesta de salud indica
 * sintomas. De esa decision depende que el form-service publique el evento
 * survey.submitted con hasSymptoms=true y que, aguas abajo, el
 * promotion-service promueva al usuario a SUSPECT. Un falso negativo aqui
 * significa un contagiado que nunca entra en cuarentena.
 *
 * Se aisla con instanciacion directa: no hay mocks porque el componente es
 * una funcion pura sobre sus dos argumentos.
 */
class SymptomMapperAdvancedTest {

    private final SymptomMapper mapper = new SymptomMapper();

    private Question question(UUID id, String text, QuestionType type) {
        return Question.builder()
                .id(id)
                .text(text)
                .type(type)
                .orderIndex(0)
                .build();
    }

    private Questionnaire questionnaire(Question... questions) {
        return Questionnaire.builder()
                .id(UUID.randomUUID())
                .title("Encuesta diaria de salud")
                .version(1)
                .isActive(true)
                .questions(List.of(questions))
                .build();
    }

    private HealthSurvey survey(Map<String, Object> responses) {
        return HealthSurvey.builder()
                .id(UUID.randomUUID())
                .anonymousId(UUID.randomUUID())
                .responses(responses)
                .build();
    }

    @Test
    @DisplayName("Responder YES a una pregunta de fiebre marca sintomas")
    void detectsFeverAnswer() {
        UUID qid = UUID.randomUUID();
        Questionnaire q = questionnaire(question(qid, "Do you have a fever?", QuestionType.YES_NO));
        HealthSurvey s = survey(Map.of(qid.toString(), "YES"));

        assertTrue(mapper.hasSymptoms(s, q),
                "Una respuesta afirmativa sobre fiebre debe activar la promocion a SUSPECT");
    }

    @Test
    @DisplayName("Responder NO a todas las preguntas no marca sintomas")
    void ignoresNegativeAnswers() {
        UUID qid = UUID.randomUUID();
        Questionnaire q = questionnaire(question(qid, "Do you have a cough?", QuestionType.YES_NO));
        HealthSurvey s = survey(Map.of(qid.toString(), "NO"));

        assertFalse(mapper.hasSymptoms(s, q),
                "Responder NO no debe generar una cuarentena injustificada");
    }

    @Test
    @DisplayName("Una pregunta no relacionada con sintomas no activa la alerta aunque sea YES")
    void ignoresUnrelatedQuestions() {
        UUID qid = UUID.randomUUID();
        Questionnaire q = questionnaire(question(qid, "Did you travel abroad?", QuestionType.YES_NO));
        HealthSurvey s = survey(Map.of(qid.toString(), "YES"));

        assertFalse(mapper.hasSymptoms(s, q),
                "Solo fever, cough y breathing deben disparar la alerta en preguntas YES_NO");
    }

    @Test
    @DisplayName("Una seleccion multiple de sintomas no vacia marca sintomas")
    void detectsMultiChoiceSymptoms() {
        UUID qid = UUID.randomUUID();
        Questionnaire q = questionnaire(question(qid, "Select your symptoms", QuestionType.MULTI_CHOICE));
        HealthSurvey s = survey(Map.of(qid.toString(), "[\"headache\"]"));

        assertTrue(mapper.hasSymptoms(s, q),
                "Seleccionar al menos un sintoma debe activar la alerta");
    }

    @Test
    @DisplayName("Una seleccion multiple vacia no marca sintomas")
    void ignoresEmptyMultiChoice() {
        UUID qid = UUID.randomUUID();
        Questionnaire q = questionnaire(question(qid, "Select your symptoms", QuestionType.MULTI_CHOICE));
        HealthSurvey s = survey(Map.of(qid.toString(), "[]"));

        assertFalse(mapper.hasSymptoms(s, q),
                "Una lista vacia significa que el usuario no reporto nada");
    }

    @Test
    @DisplayName("Responses nulo o cuestionario nulo se degradan a false sin lanzar excepcion")
    void handlesNullsDefensively() {
        UUID qid = UUID.randomUUID();
        Questionnaire q = questionnaire(question(qid, "Do you have a fever?", QuestionType.YES_NO));

        assertFalse(mapper.hasSymptoms(survey(null), q),
                "Una encuesta sin respuestas no debe romper el flujo de Kafka");
        assertFalse(mapper.hasSymptoms(survey(new HashMap<>()), null),
                "Si no hay cuestionario activo el mapper debe devolver false");
    }
}

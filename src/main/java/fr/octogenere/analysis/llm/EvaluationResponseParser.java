package fr.octogenere.analysis.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import fr.octogenere.analysis.config.EvaluationCriterion;
import fr.octogenere.analysis.model.CriterionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Valide la réponse globale du LLM avant qu'elle atteigne l'UI ou le rapport. */
public final class EvaluationResponseParser {
    private final ObjectMapper objectMapper;

    public EvaluationResponseParser() {
        JsonFactory jsonFactory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        objectMapper = JsonMapper.builder(jsonFactory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
    }

    public ParsedResponse parse(String json, List<EvaluationCriterion> expectedCriteria) {
        if (json == null || json.isBlank()) {
            throw new InvalidLlmResponseException("La réponse du LLM est vide.");
        }
        if (expectedCriteria == null || expectedCriteria.isEmpty()) {
            throw new IllegalArgumentException("Les critères attendus doivent être présents.");
        }

        RawResponse response;
        try {
            response = objectMapper.readValue(json, RawResponse.class);
        } catch (JsonProcessingException | IllegalArgumentException error) {
            throw new InvalidLlmResponseException(
                    "La réponse du LLM ne respecte pas le format d'évaluation attendu.", error);
        }
        if (response.overallSummary() == null || response.overallSummary().isBlank()) {
            throw new InvalidLlmResponseException("La synthèse générale renvoyée par le LLM est vide.");
        }
        if (response.criteria() == null) {
            throw new InvalidLlmResponseException("La réponse du LLM ne contient aucun critère.");
        }

        Map<String, CriterionResult> actual = new HashMap<>();
        for (CriterionResult result : response.criteria()) {
            if (actual.put(result.criterion(), result) != null) {
                throw new InvalidLlmResponseException(
                        "La réponse du LLM contient plusieurs fois le critère " + result.criterion() + ".");
            }
        }

        List<CriterionResult> ordered = new ArrayList<>();
        for (EvaluationCriterion expected : expectedCriteria) {
            CriterionResult result = actual.remove(expected.label());
            if (result == null) {
                throw new InvalidLlmResponseException(
                        "La réponse du LLM ne contient pas le critère " + expected.label() + ".");
            }
            if (result.maxScore() != expected.maxScore()) {
                throw new InvalidLlmResponseException(
                        "Le score maximal renvoyé pour " + expected.label() + " est incorrect.");
            }
            ordered.add(result);
        }
        if (!actual.isEmpty()) {
            throw new InvalidLlmResponseException("La réponse du LLM contient des critères non demandés.");
        }
        return new ParsedResponse(response.overallSummary().trim(), ordered);
    }

    private record RawResponse(
            @JsonProperty(value = "overallSummary", required = true) String overallSummary,
            @JsonProperty(value = "criteria", required = true) List<CriterionResult> criteria) {
    }

    public record ParsedResponse(String overallSummary, List<CriterionResult> criteria) {
        public ParsedResponse {
            criteria = List.copyOf(criteria);
        }
    }
}

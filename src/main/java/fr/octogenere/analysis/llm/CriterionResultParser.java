package fr.octogenere.analysis.llm;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import fr.octogenere.analysis.model.CriterionResult;

/** Transforme le JSON brut d'un LLM en résultat Java validé. */
public class CriterionResultParser {
    private final ObjectMapper objectMapper;

    public CriterionResultParser() {
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

    public CriterionResult parse(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidLlmResponseException("La réponse du LLM est vide.");
        }

        try {
            CriterionResult result = objectMapper.readValue(json, CriterionResult.class);
            if (result == null) {
                throw new InvalidLlmResponseException("La réponse du LLM ne contient aucun résultat.");
            }
            return result;
        } catch (JsonProcessingException | IllegalArgumentException error) {
            throw new InvalidLlmResponseException(
                    "La réponse du LLM ne respecte pas le format d'évaluation attendu.",
                    error);
        }
    }
}

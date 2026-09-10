package fr.octogenere.analysis.llm;

import fr.octogenere.analysis.model.CriterionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CriterionResultParserTest {
    private final CriterionResultParser parser = new CriterionResultParser();

    @Test
    void parsesAValidCriterionResult() {
        String json = """
                {
                  "criterion": "Architecture",
                  "score": 7,
                  "maxScore": 10,
                  "summary": "Architecture globalement claire.",
                  "strengths": ["Responsabilités séparées."],
                  "weaknesses": ["Couplage encore présent."],
                  "issues": ["Dépendance directe depuis l'interface."],
                  "recommendations": ["Introduire un service applicatif."]
                }
                """;

        CriterionResult result = parser.parse(json);

        assertEquals("Architecture", result.criterion());
        assertEquals(7, result.score());
        assertEquals(10, result.maxScore());
        assertEquals("Responsabilités séparées.", result.strengths().getFirst());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "not json",
            "```json\n{}\n```",
            "{}",
            "null"
    })
    void rejectsEmptyOrMalformedResponses(String json) {
        assertThrows(InvalidLlmResponseException.class, () -> parser.parse(json));
    }

    @Test
    void rejectsAnUnknownField() {
        String json = validJson().replace(
                "\"criterion\": \"Architecture\"",
                "\"criterion\": \"Architecture\", \"unexpected\": true");

        assertThrows(InvalidLlmResponseException.class, () -> parser.parse(json));
    }

    @Test
    void rejectsAMissingScore() {
        String json = validJson().replace("\"score\": 7,", "");

        assertThrows(InvalidLlmResponseException.class, () -> parser.parse(json));
    }

    @Test
    void rejectsAScoreOutsideItsBounds() {
        String json = validJson().replace("\"score\": 7", "\"score\": 12");

        assertThrows(InvalidLlmResponseException.class, () -> parser.parse(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"score\": \"7\"",
            "\"score\": 7.5",
            "\"score\": 7, \"score\": 8"
    })
    void rejectsAnInvalidScoreTypeOrADuplicate(String replacement) {
        String json = validJson().replace("\"score\": 7", replacement);

        assertThrows(InvalidLlmResponseException.class, () -> parser.parse(json));
    }

    @Test
    void rejectsTextAfterTheJsonObject() {
        assertThrows(InvalidLlmResponseException.class, () -> parser.parse(validJson() + "Texte ajouté"));
    }

    private String validJson() {
        return """
                {
                  "criterion": "Architecture",
                  "score": 7,
                  "maxScore": 10,
                  "summary": "Architecture globalement claire.",
                  "strengths": [],
                  "weaknesses": [],
                  "issues": [],
                  "recommendations": []
                }
                """;
    }
}

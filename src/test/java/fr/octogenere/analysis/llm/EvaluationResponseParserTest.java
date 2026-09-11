package fr.octogenere.analysis.llm;

import fr.octogenere.analysis.config.EvaluationCriterion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationResponseParserTest {
    private final EvaluationResponseParser parser = new EvaluationResponseParser();
    private final List<EvaluationCriterion> expected = List.of(
            new EvaluationCriterion("architecture", "Architecture", true, 0.6, 10),
            new EvaluationCriterion("tests", "Tests", true, 0.4, 5));

    @Test
    void validatesAndRestoresTheRequestedCriterionOrder() {
        EvaluationResponseParser.ParsedResponse response = parser.parse("""
                {
                  "overallSummary": "Synthèse générale.",
                  "criteria": [
                    {"criterion":"Tests","score":3,"maxScore":5,"summary":"Tests.",
                     "strengths":[],"weaknesses":[],"issues":[],"recommendations":[]},
                    {"criterion":"Architecture","score":8,"maxScore":10,"summary":"Architecture.",
                     "strengths":[],"weaknesses":[],"issues":[],"recommendations":[]}
                  ]
                }
                """, expected);

        assertEquals("Synthèse générale.", response.overallSummary());
        assertEquals(List.of("Architecture", "Tests"),
                response.criteria().stream().map(result -> result.criterion()).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"overallSummary\":\"Résumé\",\"criteria\":[],\"extra\":true}",
            "{\"overallSummary\":\"Résumé\",\"criteria\":[{\"criterion\":\"Architecture\",\"score\":8,\"maxScore\":9,\"summary\":\"x\",\"strengths\":[],\"weaknesses\":[],\"issues\":[],\"recommendations\":[]}]}"
    })
    void rejectsIncompleteOrInconsistentResponses(String json) {
        assertThrows(InvalidLlmResponseException.class, () -> parser.parse(json, expected));
    }
}

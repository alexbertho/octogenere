package fr.octogenere.analysis.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationModelsTest {
    @Test
    void rejectsAnInvalidScore() {
        assertThrows(IllegalArgumentException.class, () -> criterion(11, 10));
        assertThrows(IllegalArgumentException.class, () -> criterion(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> criterion(0, 0));
    }

    @Test
    void protectsTheListsFromExternalChanges() {
        List<String> strengths = new ArrayList<>();
        strengths.add("Structure claire");

        CriterionResult result = new CriterionResult(
                "Architecture", 8, 10, "Bonne structure.", strengths,
                List.of(), List.of(), List.of());
        strengths.add("Nouvel élément");

        assertEquals(1, result.strengths().size());
        assertThrows(UnsupportedOperationException.class, () -> result.strengths().add("Modification"));
    }

    @Test
    void requiresAtLeastOneCriterionInAReport() {
        assertThrows(IllegalArgumentException.class, () -> new EvaluationReport(
                "Projet", LocalDate.of(2026, 9, 10), "Configuration", "Modèle",
                List.of(), "Synthèse"));
    }

    private CriterionResult criterion(int score, int maxScore) {
        return new CriterionResult(
                "Architecture", score, maxScore, "Synthèse",
                List.of(), List.of(), List.of(), List.of());
    }
}

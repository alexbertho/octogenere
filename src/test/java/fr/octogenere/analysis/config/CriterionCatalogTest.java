package fr.octogenere.analysis.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CriterionCatalogTest {
    @Test
    void loadsTheSharedUiAndPromptConfiguration() {
        CriterionCatalog catalog = CriterionCatalog.loadDefault();

        assertEquals(5, catalog.criteria().size());
        assertFalse(catalog.criteria().get(3).enabled());
        assertEquals(List.of("Sécurité", "Qualité de l'Architecture (SOLID)"),
                catalog.resolveLabels(List.of("Sécurité", "Qualité de l'Architecture (SOLID)"))
                        .stream().map(EvaluationCriterion::label).toList());
        assertThrows(IllegalArgumentException.class,
                () -> catalog.resolveLabels(List.of("Critère absent")));
    }
}

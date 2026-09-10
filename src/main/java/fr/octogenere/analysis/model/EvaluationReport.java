package fr.octogenere.analysis.model;

import java.time.LocalDate;
import java.util.List;

/** Données Java nécessaires à la génération d'un rapport d'évaluation. */
public record EvaluationReport(
        String projectName,
        LocalDate analysisDate,
        String configuration,
        String modelName,
        List<CriterionResult> criterionResults,
        String overallSummary) {

    public EvaluationReport {
        projectName = requireText(projectName, "Le nom du projet");
        if (analysisDate == null) {
            throw new IllegalArgumentException("La date d'analyse ne doit pas être absente.");
        }
        configuration = requireText(configuration, "La configuration");
        modelName = requireText(modelName, "Le nom du modèle");
        overallSummary = requireText(overallSummary, "La synthèse générale");

        if (criterionResults == null || criterionResults.isEmpty()) {
            throw new IllegalArgumentException("Le rapport doit contenir au moins un résultat.");
        }
        criterionResults = List.copyOf(criterionResults);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " ne doit pas être vide.");
        }
        return value.trim();
    }
}

package fr.octogenere.application.report;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** Informations nécessaires au cas d'utilisation de génération d'un rapport. */
public record GenerateReportRequest(
        String projectName,
        LocalDate analysisDate,
        String configuration,
        String modelName,
        List<String> criterionJsonResponses,
        String overallSummary,
        Path outputDirectory) {

    public GenerateReportRequest {
        if (criterionJsonResponses == null || criterionJsonResponses.isEmpty()) {
            throw new IllegalArgumentException("Au moins une réponse JSON est nécessaire.");
        }
        criterionJsonResponses = List.copyOf(criterionJsonResponses);
    }
}

package fr.octogenere.application.report;

import fr.octogenere.analysis.llm.CriterionResultParser;
import fr.octogenere.analysis.model.CriterionResult;
import fr.octogenere.analysis.model.EvaluationReport;
import fr.octogenere.report.ReportGenerator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coordonne la transformation des réponses JSON en rapport. */
public class GenerateReportUseCase {
    private final CriterionResultParser parser;
    private final ReportGenerator reportGenerator;

    public GenerateReportUseCase(CriterionResultParser parser, ReportGenerator reportGenerator) {
        this.parser = Objects.requireNonNull(parser, "Le parseur ne doit pas être absent.");
        this.reportGenerator = Objects.requireNonNull(
                reportGenerator,
                "Le générateur de rapport ne doit pas être absent.");
    }

    public Path execute(GenerateReportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La demande de génération ne doit pas être absente.");
        }

        List<CriterionResult> results = new ArrayList<>();
        for (String json : request.criterionJsonResponses()) {
            results.add(parser.parse(json));
        }

        EvaluationReport report = new EvaluationReport(
                request.projectName(),
                request.analysisDate(),
                request.configuration(),
                request.modelName(),
                results,
                request.overallSummary());

        return reportGenerator.generate(report, request.outputDirectory());
    }
}

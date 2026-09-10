package fr.octogenere.application.report;

import fr.octogenere.analysis.llm.CriterionResultParser;
import fr.octogenere.report.latex.LatexReportGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateReportUseCaseTest {
    private static final Path PIPELINE_OUTPUT = Path.of("target", "generated-pipeline-example");

    @Test
    void transformsLlmJsonResponsesIntoALatexReport() throws IOException {
        GenerateReportUseCase useCase = new GenerateReportUseCase(
                new CriterionResultParser(),
                new LatexReportGenerator());

        GenerateReportRequest request = new GenerateReportRequest(
                "Projet exemple",
                LocalDate.of(2026, 9, 10),
                "Analyse statique et revue LLM",
                "modèle-local",
                List.of(criterionJson("Architecture", 8), criterionJson("Tests", 6)),
                "Le projet est bien structuré, mais sa couverture de tests doit progresser.",
                PIPELINE_OUTPUT);

        Path reportPath = useCase.execute(request);
        String document = Files.readString(reportPath, StandardCharsets.UTF_8);

        assertTrue(document.contains("Architecture & 8 & 10 & 80\\%"));
        assertTrue(document.contains("Tests & 6 & 10 & 60\\%"));
        assertTrue(document.contains("\\section{Architecture}"));
        assertTrue(document.contains("\\section{Tests}"));
    }

    private String criterionJson(String criterion, int score) {
        return """
                {
                  "criterion": "%s",
                  "score": %d,
                  "maxScore": 10,
                  "summary": "Synthèse du critère.",
                  "strengths": ["Un point fort."],
                  "weaknesses": ["Une faiblesse."],
                  "issues": [],
                  "recommendations": ["Une recommandation."]
                }
                """.formatted(criterion, score);
    }
}

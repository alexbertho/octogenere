package fr.octogenere.report.latex;

import fr.octogenere.analysis.model.CriterionResult;
import fr.octogenere.analysis.model.EvaluationReport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatexReportGeneratorTest {
    private static final Path EXAMPLE_OUTPUT = Path.of("target", "generated-report-example");

    @Test
    void generatesACompleteLatexReportAndCopiesTheLogo() throws IOException {
        EvaluationReport report = exampleReport();
        LatexReportGenerator generator = new LatexReportGenerator();

        Path reportPath = generator.generate(report, EXAMPLE_OUTPUT);
        String document = Files.readString(reportPath, StandardCharsets.UTF_8);

        assertEquals(EXAMPLE_OUTPUT.resolve("rapport-evaluation.tex"), reportPath);
        assertTrue(Files.exists(EXAMPLE_OUTPUT.resolve("uca-logo.png")));
        assertTrue(document.contains("Projet \\& démonstration"));
        assertTrue(document.contains("Architecture \\& conception & 8 & 10 & 80\\%"));
        assertTrue(document.contains("\\section{Architecture \\& conception}"));
        assertTrue(document.contains("\\textit{Aucun élément signalé.}"));
        assertFalse(document.contains("@@PROJECT_NAME@@"));
        assertFalse(document.contains("\\input{/etc/passwd}"));
    }

    private EvaluationReport exampleReport() {
        CriterionResult architecture = new CriterionResult(
                "Architecture & conception",
                8,
                10,
                "Les responsabilités sont clairement séparées.",
                List.of("Packages cohérents", "Interfaces compréhensibles"),
                List.of("Quelques dépendances directes"),
                List.of("Une chaîne contient \\input{/etc/passwd} et doit rester du texte."),
                List.of("Ajouter des tests d'intégration"));

        CriterionResult documentation = new CriterionResult(
                "Documentation",
                7,
                10,
                "La documentation couvre les choix principaux.",
                List.of("README présent"),
                List.of(),
                List.of(),
                List.of("Documenter les limites"));

        return new EvaluationReport(
                "Projet & démonstration",
                LocalDate.of(2026, 9, 10),
                "Analyse statique + LLM local",
                "modèle_test_v1",
                List.of(architecture, documentation),
                "Le projet possède une base saine, avec quelques améliorations à prévoir.");
    }
}

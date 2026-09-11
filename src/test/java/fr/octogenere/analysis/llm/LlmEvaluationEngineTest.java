package fr.octogenere.analysis.llm;

import fr.octogenere.analysis.AnalysisObserver;
import fr.octogenere.analysis.config.CriterionCatalog;
import fr.octogenere.analysis.config.EvaluationCriterion;
import fr.octogenere.analysis.model.EvaluationReport;
import fr.octogenere.application.report.GenerateReportUseCase;
import fr.octogenere.llm.LlmProvider;
import fr.octogenere.report.latex.LatexReportGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmEvaluationEngineTest {
    @TempDir
    Path directory;

    @Test
    void runsFromProjectFilesToAValidatedLatexReport() throws IOException {
        Path project = Files.createDirectory(directory.resolve("projet-llm"));
        Files.writeString(project.resolve("Hello.java"), "class Hello { int answer() { return 42; } }");
        Files.writeString(project.resolve(".env"), "GOOGLE_API_KEY=secret-value");

        EvaluationCriterion architecture =
                new EvaluationCriterion("architecture", "Architecture", true, 0.6, 10);
        EvaluationCriterion tests = new EvaluationCriterion("tests", "Tests", true, 0.4, 10);
        CriterionCatalog catalog = new CriterionCatalog(List.of(architecture, tests));
        AtomicReference<String> receivedPrompt = new AtomicReference<>();
        AtomicReference<String> receivedModel = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean();
        LlmProvider provider = new LlmProvider() {
            @Override
            public String ask(String prompt) {
                receivedPrompt.set(prompt);
                return responseJson();
            }

            @Override
            public String ask(String prompt, String model) {
                receivedModel.set(model);
                return ask(prompt);
            }

            @Override
            public String providerName() {
                return "Faux provider";
            }

            @Override
            public String modelName() {
                return "faux-modèle";
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
        List<Double> progress = new ArrayList<>();
        List<String> logs = new ArrayList<>();

        LlmEvaluationEngine engine = new LlmEvaluationEngine(provider, catalog);
        EvaluationReport report = engine.analyze(project, List.of("Architecture", "Tests"), "modèle-choisi",
                observer(progress, logs));

        assertEquals("projet-llm", report.projectName());
        assertEquals("Faux provider — modèle-choisi", report.modelName());
        assertEquals("modèle-choisi", receivedModel.get());
        assertEquals(List.of("Architecture", "Tests"), report.criterionResults().stream()
                .map(result -> result.criterion()).toList());
        assertEquals(1.0, progress.getLast());
        assertTrue(logs.stream().anyMatch(log -> log.contains("Réponse du LLM validée")));
        assertTrue(receivedPrompt.get().contains("class Hello"));
        assertTrue(receivedPrompt.get().contains("UNTRUSTED_PROJECT_CONTENT_BEGIN"));
        assertTrue(receivedPrompt.get().contains("--- START SANDBOX LOGS ---"));
        assertTrue(receivedPrompt.get().indexOf("  ]\n}")
                < receivedPrompt.get().indexOf("--- START SANDBOX LOGS ---"));
        assertFalse(receivedPrompt.get().contains("secret-value"));

        Path latex = new GenerateReportUseCase(new CriterionResultParser(), new LatexReportGenerator())
                .execute(report, directory.resolve("rapport"));
        String document = Files.readString(latex);
        assertTrue(document.contains("Architecture & 8 & 10 & 80\\%"));
        assertTrue(document.contains("Tests & 6 & 10 & 60\\%"));

        engine.close();
        assertTrue(closed.get());
    }

    private AnalysisObserver observer(List<Double> progress, List<String> logs) {
        return new AnalysisObserver() {
            @Override
            public void onProgressUpdate(double value, String message) {
                progress.add(value);
            }

            @Override
            public void onNewLogAdded(String message) {
                logs.add(message);
            }
        };
    }

    private String responseJson() {
        return """
                {
                  "overallSummary": "Le projet est court et lisible.",
                  "criteria": [
                    {
                      "criterion": "Architecture",
                      "score": 8,
                      "maxScore": 10,
                      "summary": "Structure simple.",
                      "strengths": ["Responsabilités lisibles."],
                      "weaknesses": [],
                      "issues": [],
                      "recommendations": ["Conserver cette séparation."]
                    },
                    {
                      "criterion": "Tests",
                      "score": 6,
                      "maxScore": 10,
                      "summary": "La base de tests peut progresser.",
                      "strengths": [],
                      "weaknesses": ["Peu de tests visibles."],
                      "issues": [],
                      "recommendations": ["Ajouter des tests unitaires."]
                    }
                  ]
                }
                """;
    }
}

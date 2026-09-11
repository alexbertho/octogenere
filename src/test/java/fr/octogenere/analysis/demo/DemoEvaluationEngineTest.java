package fr.octogenere.analysis.demo;

import fr.octogenere.analysis.AnalysisObserver;
import fr.octogenere.analysis.llm.CriterionResultParser;
import fr.octogenere.analysis.model.EvaluationReport;
import fr.octogenere.application.report.GenerateReportUseCase;
import fr.octogenere.report.latex.LatexReportGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemoEvaluationEngineTest {
    @TempDir
    Path directory;

    @Test
    void selectedCriteriaBecomeValidatedResultsAndAnActualReport() throws IOException {
        List<Double> progress = new ArrayList<>();
        AnalysisObserver observer = observer(progress);
        List<String> criteria = List.of("Architecture & conception", "Tests \"unitaires\"");

        EvaluationReport report = new DemoEvaluationEngine().analyze(directory, criteria, observer);

        assertEquals(directory.getFileName().toString(), report.projectName());
        assertEquals(2, report.criterionResults().size());
        assertEquals(criteria.get(1), report.criterionResults().get(1).criterion());
        assertEquals(List.of(0.5, 1.0), progress);
        assertEquals("Aucun LLM (démonstration)", report.modelName());
        assertTrue(report.overallSummary().contains("fictifs"));

        GenerateReportUseCase generator = new GenerateReportUseCase(
                new CriterionResultParser(), new LatexReportGenerator());
        Path output = directory.resolve("export");
        Path file = generator.execute(report, output);
        String latex = Files.readString(file);
        assertTrue(latex.contains("Architecture \\& conception & 7 & 10"));
        assertTrue(latex.contains("DÉMONSTRATION"));
        assertTrue(Files.isRegularFile(output.resolve("uca-logo.png")));
    }

    @Test
    void rejectsEmptyCriteriaAndMissingProject() {
        DemoEvaluationEngine engine = new DemoEvaluationEngine();
        assertThrows(IllegalArgumentException.class,
                () -> engine.analyze(directory, List.of(), observer(new ArrayList<>())));
        assertThrows(IllegalArgumentException.class,
                () -> engine.analyze(directory, List.of(" "), observer(new ArrayList<>())));
        assertThrows(IllegalArgumentException.class,
                () -> engine.analyze(directory.resolve("absent"), List.of("Tests"), observer(new ArrayList<>())));
    }

    @Test
    void interruptionCannotProduceASuccessfulReport() {
        List<Double> progress = new ArrayList<>();
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class,
                    () -> new DemoEvaluationEngine().analyze(directory, List.of("Tests"), observer(progress)));
            assertTrue(progress.isEmpty());
        } finally {
            Thread.interrupted();
        }
    }

    private AnalysisObserver observer(List<Double> progress) {
        return new AnalysisObserver() {
            @Override
            public void onProgressUpdate(double value, String message) {
                progress.add(value);
            }

            @Override
            public void onNewLogAdded(String message) {
                assertTrue(message.contains("DÉMO"));
            }
        };
    }
}

package fr.octogenere.ui;

import fr.octogenere.analysis.EvaluationEngine;
import fr.octogenere.analysis.demo.DemoEvaluationEngine;
import fr.octogenere.analysis.llm.CriterionResultParser;
import fr.octogenere.application.report.GenerateReportUseCase;
import fr.octogenere.llm.LlmException;
import fr.octogenere.project.ProjectExplorerService;
import fr.octogenere.report.latex.LatexReportGenerator;
import fr.octogenere.ui.components.AnalysisConfigPanel;
import fr.octogenere.ui.components.LogAndResultPanel;
import fr.octogenere.ui.components.ProjectSelectionBar;
import fr.octogenere.ui.components.ProjectTreePanel;
import fr.octogenere.ui.controller.UIController;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Tests avec une vraie fenêtre ; à activer explicitement sur une session graphique. */
@EnabledIfSystemProperty(named = "octogenere.ui.tests", matches = "true")
class UiWorkflowTest {
    @TempDir
    Path directory;
    private Stage stage;
    private UIController controller;

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    @AfterEach
    void closeWindow() throws Exception {
        onFx(() -> {
            if (controller != null) {
                controller.close();
            }
            if (stage != null) {
                stage.close();
            }
            return null;
        });
    }

    @AfterAll
    static void stopJavaFx() {
        Platform.exit();
    }

    @Test
    void buttonsRunDemoExportAndInvalidateOldResults() throws Exception {
        Path project = Files.createDirectory(directory.resolve("projet-test"));
        Files.writeString(project.resolve("Hello.java"), "class Hello {}");
        Path reports = directory.resolve("reports");
        openWindow(new DemoEvaluationEngine(), reports);

        onFx(() -> {
            assertTrue(button("generate-report").isDisabled());
            button("start-analysis").fire();
            assertTrue(log().getText().contains("Sélectionne d'abord"));
            assertFalse(button("start-analysis").isDisabled());
            controller.onProjectSelected(project.toFile());
            return null;
        });
        awaitFx(() -> !button("start-analysis").isDisabled());
        onFx(() -> {
            TreeView<?> tree = (TreeView<?>) stage.getScene().lookup("#project-tree");
            assertEquals("projet-test", tree.getRoot().getValue());
            button("start-analysis").fire();
            return null;
        });
        awaitFx(() -> !button("generate-report").isDisabled());
        onFx(() -> {
            assertTrue(log().getText().contains("7 / 10"));
            button("generate-report").fire();
            return null;
        });
        awaitFx(() -> log().getText().contains("[SUCCÈS] Rapport LaTeX"));

        try (var files = Files.walk(reports)) {
            Path latex = files.filter(path -> path.toString().endsWith(".tex")).findFirst().orElseThrow();
            String content = Files.readString(latex);
            assertTrue(content.contains("projet-test"));
            assertTrue(content.contains("DÉMONSTRATION"));
            assertTrue(content.contains("7 & 10 & 70"));
            assertTrue(Files.exists(latex.resolveSibling("uca-logo.png")));
        }
        onFx(() -> {
            saveScreenshot();
            button("generate-report").fire();
            return null;
        });
        awaitFx(() -> !button("generate-report").isDisabled());
        try (var exports = Files.list(reports)) {
            assertEquals(2, exports.count(), "Un second export ne doit pas écraser le premier.");
        }
        onFx(() -> {
            CheckBox criterion = (CheckBox) stage.getScene().lookup(".check-box");
            criterion.fire();
            assertTrue(button("generate-report").isDisabled());
            button("start-analysis").fire();
            return null;
        });
        awaitFx(() -> !button("generate-report").isDisabled());
        onFx(() -> {
            controller.onProjectSelected(directory.resolve("absent").toFile());
            assertTrue(button("generate-report").isDisabled());
            return null;
        });
        awaitFx(() -> !button("start-analysis").isDisabled());
        assertTrue(onFx(() -> log().getText().contains("[ERREUR] Le projet")));
        assertTrue(onFx(() -> button("generate-report").isDisabled()));
    }

    @Test
    void engineFailureReleasesControlsAndDoesNotEnableExport() throws Exception {
        openWindow((project, criteria, observer) -> {
            throw new IOException("Panne simulée du moteur");
        }, directory.resolve("reports"));
        onFx(() -> {
            controller.onProjectSelected(directory.toFile());
            return null;
        });
        awaitFx(() -> !button("start-analysis").isDisabled());
        onFx(() -> {
            button("start-analysis").fire();
            return null;
        });
        awaitFx(() -> log().getText().contains("Panne simulée"));
        assertFalse(onFx(() -> button("start-analysis").isDisabled()));
        assertTrue(onFx(() -> button("generate-report").isDisabled()));
    }

    @Test
    void llmFailureIsIdentifiedInTheInterfaceAndAllowsRetry() throws Exception {
        openWindow((project, criteria, observer) -> {
            throw new LlmException("Quota Gemini atteint (HTTP 429).");
        }, directory.resolve("reports"));
        onFx(() -> {
            controller.onProjectSelected(directory.toFile());
            return null;
        });
        awaitFx(() -> !button("start-analysis").isDisabled());
        onFx(() -> {
            button("start-analysis").fire();
            return null;
        });
        awaitFx(() -> log().getText().contains("[ERREUR IA]"));

        assertTrue(onFx(() -> log().getText().contains("HTTP 429")));
        assertFalse(onFx(() -> button("start-analysis").isDisabled()));
        assertTrue(onFx(() -> button("generate-report").isDisabled()));
    }

    @Test
    void exportFailureIsVisibleAndAllowsRetry() throws Exception {
        Path blockedOutput = Files.writeString(directory.resolve("not-a-directory"), "keep");
        openWindow(new DemoEvaluationEngine(), blockedOutput);
        onFx(() -> {
            controller.onProjectSelected(directory.toFile());
            return null;
        });
        awaitFx(() -> !button("start-analysis").isDisabled());
        onFx(() -> {
            button("start-analysis").fire();
            return null;
        });
        awaitFx(() -> !button("generate-report").isDisabled());
        onFx(() -> {
            button("generate-report").fire();
            return null;
        });
        awaitFx(() -> log().getText().contains("[ERREUR]"));
        assertFalse(onFx(() -> button("generate-report").isDisabled()));
        assertFalse(onFx(() -> log().getText().contains("[SUCCÈS]")));
        assertEquals("keep", Files.readString(blockedOutput));
    }

    @Test
    void actualApplicationLoadsItsWindowAndStylesheet() throws Exception {
        onFx(() -> {
            stage = new Stage();
            MainApp application = new MainApp();
            try {
                application.start(stage);
                assertTrue(stage.isShowing());
            assertTrue(stage.getTitle().contains("Analyse LLM"));
                assertTrue(stage.getScene().getStylesheets().getFirst().endsWith("ui/style.css"));
                assertNotNull(button("start-analysis"));
            } finally {
                application.stop();
            }
            return null;
        });
    }

    private void openWindow(EvaluationEngine engine, Path reports) throws Exception {
        onFx(() -> {
            stage = new Stage();
            controller = new UIController(engine, new ProjectExplorerService(),
                    new GenerateReportUseCase(new CriterionResultParser(), new LatexReportGenerator()), reports);
            ProjectSelectionBar selection = new ProjectSelectionBar(stage, controller);
            ProjectTreePanel tree = new ProjectTreePanel();
            AnalysisConfigPanel configuration = new AnalysisConfigPanel(controller);
            LogAndResultPanel logPanel = new LogAndResultPanel(controller);
            controller.attachViews(selection, tree, configuration, logPanel);
            BorderPane root = new BorderPane(configuration, selection, null, logPanel, tree);
            Scene scene = new Scene(root, 1000, 720);
            scene.getStylesheets().add(getClass().getResource("/ui/style.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
            return null;
        });
    }

    private Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    private TextArea log() {
        return (TextArea) stage.getScene().lookup("#analysis-log");
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

    private static void awaitFx(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (onFx(condition)) {
                return;
            }
            Thread.sleep(25);
        }
        fail("L'interface n'a pas atteint l'état attendu en dix secondes.");
    }

    private void saveScreenshot() throws IOException {
        WritableImage image = stage.getScene().snapshot(null);
        BufferedImage screenshot = new BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < screenshot.getHeight(); y++) {
            for (int x = 0; x < screenshot.getWidth(); x++) {
                screenshot.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            }
        }
        ImageIO.write(screenshot, "png", Path.of("target", "ui-integration.png").toFile());
    }
}

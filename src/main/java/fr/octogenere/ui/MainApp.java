package fr.octogenere.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import fr.octogenere.analysis.EvaluationEngine;
import fr.octogenere.analysis.config.CriterionCatalog;
import fr.octogenere.analysis.llm.CriterionResultParser;
import fr.octogenere.analysis.llm.LlmEvaluationEngine;
import fr.octogenere.application.report.GenerateReportUseCase;
import fr.octogenere.llm.LlmException;
import fr.octogenere.llm.LlmProvider;
import fr.octogenere.llm.LlmProviderFactory;
import fr.octogenere.project.ProjectExplorerService;
import fr.octogenere.report.latex.LatexReportGenerator;
import fr.octogenere.ui.controller.UIController;
import fr.octogenere.ui.components.*;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;

import java.nio.file.Path;

public class MainApp extends Application {
    private UIController controller;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("OctoGenere — Analyse LLM");

        CriterionCatalog catalog = CriterionCatalog.loadDefault();
        EngineSetup setup = configureEngine(catalog);

        controller = new UIController(setup.engine(), new ProjectExplorerService(),
                new GenerateReportUseCase(new CriterionResultParser(), new LatexReportGenerator()),
                Path.of("target", "reports"));

        ProjectSelectionBar topBar = new ProjectSelectionBar(primaryStage, controller);
        ProjectTreePanel leftPanel = new ProjectTreePanel();
        AnalysisConfigPanel centerPanel = new AnalysisConfigPanel(
                controller, catalog.criteria(), setup.modeDescription());
        LogAndResultPanel bottomPanel = new LogAndResultPanel(controller, setup.initialLog());

        controller.attachViews(topBar, leftPanel, centerPanel, bottomPanel);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(leftPanel);
        root.setCenter(centerPanel);
        root.setBottom(bottomPanel);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("main-scroll-pane");

        Scene scene = new Scene(scrollPane, 1000, 720);
        primaryStage.setMinWidth(850);
        primaryStage.setMinHeight(650);
        primaryStage.setScene(scene);
        String cssPath = getClass().getResource("/ui/style.css").toExternalForm();
        scene.getStylesheets().add(cssPath);

        primaryStage.show();
    }

    private EngineSetup configureEngine(CriterionCatalog catalog) {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            LlmProvider provider = LlmProviderFactory.from(dotenv);
            String description = "Analyse réelle avec " + provider.providerName()
                    + " — modèle " + provider.modelName() + ".";
            return new EngineSetup(new LlmEvaluationEngine(provider, catalog), description,
                    "[CONFIGURATION] " + description);
        } catch (DotenvException error) {
            return unavailable("Impossible de lire le fichier .env. Vérifie sa syntaxe.");
        } catch (LlmException error) {
            return unavailable(error.getMessage());
        } catch (RuntimeException error) {
            return unavailable("Impossible d'initialiser le fournisseur LLM configuré.");
        }
    }

    private EngineSetup unavailable(String message) {
        EvaluationEngine engine = (project, criteria, observer) -> {
            throw new LlmException(message);
        };
        return new EngineSetup(engine,
                "Configuration LLM incomplète. L'erreur détaillée apparaîtra au lancement de l'analyse.",
                "[CONFIGURATION] " + message);
    }

    private record EngineSetup(EvaluationEngine engine, String modeDescription, String initialLog) {
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

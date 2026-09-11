package fr.octogenere.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import fr.octogenere.analysis.demo.DemoEvaluationEngine;
import fr.octogenere.analysis.llm.CriterionResultParser;
import fr.octogenere.application.report.GenerateReportUseCase;
import fr.octogenere.project.ProjectExplorerService;
import fr.octogenere.report.latex.LatexReportGenerator;
import fr.octogenere.ui.controller.UIController;
import fr.octogenere.ui.components.*;

import java.nio.file.Path;

public class MainApp extends Application {
    private UIController controller;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("OctoGenere — Démonstration sans appel API");

        controller = new UIController(new DemoEvaluationEngine(), new ProjectExplorerService(),
                new GenerateReportUseCase(new CriterionResultParser(), new LatexReportGenerator()),
                Path.of("target", "reports"));

        ProjectSelectionBar topBar = new ProjectSelectionBar(primaryStage, controller);
        ProjectTreePanel leftPanel = new ProjectTreePanel();
        AnalysisConfigPanel centerPanel = new AnalysisConfigPanel(controller);
        LogAndResultPanel bottomPanel = new LogAndResultPanel(controller);

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

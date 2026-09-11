package fr.octogenere.ui.controller;

import fr.octogenere.analysis.AnalysisObserver;
import fr.octogenere.analysis.EvaluationEngine;
import fr.octogenere.analysis.model.CriterionResult;
import fr.octogenere.analysis.model.EvaluationReport;
import fr.octogenere.application.report.GenerateReportUseCase;
import fr.octogenere.project.ProjectExplorerService;
import fr.octogenere.project.ProjectExplorerService.FileNode;
import fr.octogenere.ui.components.AnalysisConfigPanel;
import fr.octogenere.ui.components.LogAndResultPanel;
import fr.octogenere.ui.components.ProjectSelectionBar;
import fr.octogenere.ui.components.ProjectTreePanel;
import javafx.application.Platform;
import javafx.concurrent.Task;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Coordonne les actions de la fenêtre et délègue l'analyse et l'export au cœur. */
public class UIController implements AnalysisObserver, AutoCloseable {
    private final EvaluationEngine evaluationEngine;
    private final ProjectExplorerService explorerService;
    private final GenerateReportUseCase reportUseCase;
    private final Path reportsDirectory;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "octogenere-ui-worker");
        thread.setDaemon(true);
        return thread;
    });

    private ProjectSelectionBar selectionBar;
    private ProjectTreePanel treePanel;
    private AnalysisConfigPanel configPanel;
    private LogAndResultPanel logPanel;
    private Path selectedProject;
    private EvaluationReport lastReport;
    private Task<?> currentTask;
    private boolean busy;
    private boolean closed;

    public UIController(EvaluationEngine evaluationEngine, ProjectExplorerService explorerService,
                        GenerateReportUseCase reportUseCase, Path reportsDirectory) {
        this.evaluationEngine = Objects.requireNonNull(evaluationEngine);
        this.explorerService = Objects.requireNonNull(explorerService);
        this.reportUseCase = Objects.requireNonNull(reportUseCase);
        this.reportsDirectory = Objects.requireNonNull(reportsDirectory);
    }

    public void attachViews(ProjectSelectionBar selectionBar, ProjectTreePanel treePanel,
                            AnalysisConfigPanel configPanel, LogAndResultPanel logPanel) {
        this.selectionBar = selectionBar;
        this.treePanel = treePanel;
        this.configPanel = configPanel;
        this.logPanel = logPanel;
    }

    public void onProjectSelected(File directory) {
        if (busy || closed || directory == null) {
            return;
        }
        Path project = directory.toPath().toAbsolutePath().normalize();
        selectedProject = null;
        invalidateResults();
        selectionBar.setProjectPath("Chargement...");
        treePanel.clear();
        configPanel.updateProgress(-1, "Lecture du dossier...");

        Task<FileNode> task = new Task<>() {
            @Override
            protected FileNode call() throws Exception {
                return explorerService.exploreProject(project);
            }
        };
        startTask(task, root -> {
            selectedProject = project;
            selectionBar.setProjectPath(project.toString());
            treePanel.loadProjectTree(root);
            configPanel.updateProgress(0, "Projet prêt pour la démonstration.");
            logPanel.appendLog("[INFO] Projet sélectionné : " + project);
        });
    }

    public void onStartAnalysis(List<String> criteria) {
        if (busy || closed) {
            return;
        }
        if (selectedProject == null) {
            logPanel.appendLog("[ERREUR] Sélectionne d'abord un projet.");
            return;
        }
        if (criteria == null || criteria.isEmpty()) {
            logPanel.appendLog("[ERREUR] Sélectionne au moins un critère.");
            return;
        }
        Path project = selectedProject;
        List<String> selectedCriteria = List.copyOf(criteria);
        invalidateResults();
        configPanel.updateProgress(0, "Démarrage...");

        Task<EvaluationReport> task = new Task<>() {
            @Override
            protected EvaluationReport call() throws Exception {
                return Objects.requireNonNull(
                        evaluationEngine.analyze(project, selectedCriteria, UIController.this),
                        "Le moteur n'a fourni aucun résultat.");
            }
        };
        startTask(task, report -> {
            lastReport = report;
            logPanel.appendLog(report.overallSummary());
            for (CriterionResult result : report.criterionResults()) {
                logPanel.appendLog(result.criterion() + " : " + result.score() + " / " + result.maxScore());
            }
            configPanel.updateProgress(1, "Résultats disponibles.");
        });
    }

    public void onGenerateReport() {
        if (busy || closed) {
            return;
        }
        if (lastReport == null) {
            logPanel.appendLog("[ERREUR] Lance d'abord une analyse pour obtenir des résultats.");
            return;
        }
        EvaluationReport report = lastReport;
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                Files.createDirectories(reportsDirectory);
                // Chaque export a son dossier : aucun rapport précédent n'est écrasé.
                Path output = Files.createTempDirectory(reportsDirectory, "evaluation-");
                return reportUseCase.execute(report, output);
            }
        };
        logPanel.appendLog("[INFO] Génération LaTeX...");
        startTask(task, file -> {
            logPanel.appendLog("[SUCCÈS] Rapport LaTeX : " + file.toAbsolutePath());
            configPanel.updateProgress(1, "Rapport LaTeX généré.");
        });
    }

    public void onConfigurationChanged() {
        if (!busy && !closed) {
            invalidateResults();
            configPanel.updateProgress(0, "Configuration modifiée : relance l'analyse.");
        }
    }

    private void invalidateResults() {
        lastReport = null;
        logPanel.setReportButtonEnabled(false);
    }

    private <T> void startTask(Task<T> task, Consumer<T> onSuccess) {
        currentTask = task;
        setBusy(true);
        // Ces callbacks sont exécutés par JavaFX sur le thread de la fenêtre.
        task.setOnSucceeded(event -> {
            if (!closed) {
                try {
                    onSuccess.accept(task.getValue());
                } finally {
                    setBusy(false);
                }
            }
        });
        task.setOnFailed(event -> {
            if (!closed) {
                Throwable error = task.getException();
                logPanel.appendLog("[ERREUR] " + error.getMessage());
                if (selectedProject == null) {
                    selectionBar.setProjectPath("Aucun projet sélectionné");
                }
                configPanel.updateProgress(0, "Échec de l'opération.");
                setBusy(false);
            }
        });
        task.setOnCancelled(event -> {
            if (!closed) {
                configPanel.updateProgress(0, "Opération annulée.");
                setBusy(false);
            }
        });
        worker.submit(task);
    }

    private void setBusy(boolean value) {
        busy = value;
        selectionBar.setDisable(value);
        configPanel.setBusy(value);
        logPanel.setReportButtonEnabled(!value && lastReport != null);
    }

    @Override
    public void onProgressUpdate(double progress, String statusMessage) {
        Platform.runLater(() -> {
            if (!closed) {
                configPanel.updateProgress(progress, statusMessage);
            }
        });
    }

    @Override
    public void onNewLogAdded(String message) {
        Platform.runLater(() -> {
            if (!closed) {
                logPanel.appendLog(message);
            }
        });
    }

    @Override
    public void close() {
        closed = true;
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        worker.shutdownNow();
    }
}

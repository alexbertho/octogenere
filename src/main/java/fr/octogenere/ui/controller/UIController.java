package fr.octogenere.ui.controller;

import fr.octogenere.analysis.AnalysisObserver;
import fr.octogenere.analysis.EvaluationEngine;
import fr.octogenere.analysis.model.CriterionResult;
import fr.octogenere.analysis.model.EvaluationReport;
import fr.octogenere.analysis.llm.InvalidLlmResponseException;
import fr.octogenere.application.report.GenerateReportUseCase;
import fr.octogenere.llm.LlmException;
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
    // Services métiers
    private final EvaluationEngine evaluationEngine;
    private final ProjectExplorerService explorerService;
    private final GenerateReportUseCase reportUseCase;
    private final Path reportsDirectory;

    // Pool de threads pour exécuter les tâches lourdes en arrière-plan
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "octogenere-ui-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService modelWorker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "octogenere-model-loader");
        thread.setDaemon(true);
        return thread;
    });

    // Vues
    private ProjectSelectionBar selectionBar;
    private ProjectTreePanel treePanel;
    private AnalysisConfigPanel configPanel;
    private LogAndResultPanel logPanel;

    // Etats de l'application
    private Path selectedProject;
    private EvaluationReport lastReport;
    private Task<?> currentTask;
    private Task<?> modelTask;
    private boolean busy;
    private boolean closed;

    public UIController(EvaluationEngine evaluationEngine, ProjectExplorerService explorerService,
                        GenerateReportUseCase reportUseCase, Path reportsDirectory) {
        this.evaluationEngine = Objects.requireNonNull(evaluationEngine);
        this.explorerService = Objects.requireNonNull(explorerService);
        this.reportUseCase = Objects.requireNonNull(reportUseCase);
        this.reportsDirectory = Objects.requireNonNull(reportsDirectory);
    }

    /**
     * Connecte les différents panneaux de l'interface au contrôleur.
     */
    public void attachViews(ProjectSelectionBar selectionBar, ProjectTreePanel treePanel,
                            AnalysisConfigPanel configPanel, LogAndResultPanel logPanel) {
        this.selectionBar = selectionBar;
        this.treePanel = treePanel;
        this.configPanel = configPanel;
        this.logPanel = logPanel;
        configPanel.setAvailableModels(List.of(), evaluationEngine.configuredModel());
        loadAvailableModels();
    }

    /** Charge les modèles du fournisseur sans bloquer le thread JavaFX. */
    private void loadAvailableModels() {
        String configuredModel = evaluationEngine.configuredModel();
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                List<String> models = evaluationEngine.availableModels();
                return models == null ? List.of() : List.copyOf(models);
            }
        };
        modelTask = task;
        task.setOnSucceeded(event -> {
            if (closed) {
                return;
            }
            List<String> models = task.getValue();
            configPanel.setAvailableModels(models, configuredModel);
            if (models.isEmpty()) {
                logPanel.appendLog("[ATTENTION] Aucun modèle distant reçu ; utilisation du modèle configuré : "
                        + configuredModel + ".");
            } else {
                logPanel.appendLog("[CONFIGURATION] " + models.size()
                        + " modèle(s) disponible(s) chargé(s).");
            }
        });
        task.setOnFailed(event -> {
            if (!closed) {
                configPanel.setAvailableModels(List.of(), configuredModel);
                logPanel.appendLog("[ATTENTION] Impossible de charger les modèles distants ; utilisation du modèle "
                        + "configuré : " + configuredModel + ".");
            }
        });
        modelWorker.submit(task);
    }

    /**
     * Déclenchée lors de la sélection d'un dossier de projet par l'utilisateur.
     * @param directory Le dossier racine du projet à analyser.
     */
    public void onProjectSelected(File directory) {
        if (busy || closed || directory == null) {
            return;
        }
        Path project = directory.toPath().toAbsolutePath().normalize();
        selectedProject = null;
        invalidateResults();

        // Mise à jour immédiate de l'UI avant le traitement lourd
        selectionBar.setProjectPath("Chargement...");
        treePanel.clear();
        configPanel.updateProgress(-1, "Lecture du dossier...");

        // Création d'une tâche asynchrone pour l'exploration du disque
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
            configPanel.updateProgress(0, "Projet prêt pour l'analyse.");
            logPanel.appendLog("[INFO] Projet sélectionné : " + project);
        }, Operation.PROJECT);
    }

    /**
     * Déclenchée pour lancer l'analyse du projet sélectionné par le LLM.
     * @param criteria La liste des critères d'évaluation sélectionnés.
     */
    public void onStartAnalysis(List<String> criteria) {
        onStartAnalysis(criteria, evaluationEngine.configuredModel());
    }

    /**
     * Déclenchée pour lancer l'analyse avec le modèle choisi dans l'interface.
     * @param criteria La liste des critères d'évaluation sélectionnés.
     * @param model Le modèle sélectionné.
     */
    public void onStartAnalysis(List<String> criteria, String model) {
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
        if (model == null || model.isBlank()) {
            logPanel.appendLog("[ERREUR] Sélectionne un modèle d'IA.");
            return;
        }
        Path project = selectedProject;
        List<String> selectedCriteria = List.copyOf(criteria);
        String selectedModel = model.trim();
        invalidateResults();
        configPanel.updateProgress(0, "Démarrage...");
        logPanel.appendLog("[INFO] Lancement de l'analyse.");

        // Lancement du moteur d'analyse en arrière-plan
        Task<EvaluationReport> task = new Task<>() {
            @Override
            protected EvaluationReport call() throws Exception {
                return Objects.requireNonNull(
                        evaluationEngine.analyze(project, selectedCriteria, selectedModel, UIController.this),
                        "Le moteur n'a fourni aucun résultat.");
            }
        };
        startTask(task, report -> {
            lastReport = report;
            logPanel.appendLog("[SYNTHÈSE] " + report.overallSummary());
            for (CriterionResult result : report.criterionResults()) {
                logPanel.appendLog(result.criterion() + " : " + result.score() + " / " + result.maxScore());
            }
            configPanel.updateProgress(1, "Résultats disponibles.");
        }, Operation.ANALYSIS);
    }

    /**
     * Déclenchée pour générer le document LaTeX à partir des résultats obtenus.
     */
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
        }, Operation.REPORT);
    }

    public void onConfigurationChanged() {
        if (!busy && !closed) {
            invalidateResults();
            configPanel.updateProgress(0, "Configuration modifiée : relance l'analyse.");
        }
    }

    /**
     * Réinitialise l'état des résultats (désactive la génération de rapport).
     */
    private void invalidateResults() {
        lastReport = null;
        logPanel.setReportButtonEnabled(false);
    }

    /**
     * Exécute une tâche de manière asynchrone et gère les retours sur le thread UI.
     */
    private <T> void startTask(Task<T> task, Consumer<T> onSuccess, Operation operation) {
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
                String message = error == null || error.getMessage() == null || error.getMessage().isBlank()
                        ? "Une erreur inattendue est survenue."
                        : error.getMessage();
                boolean llmFailure = operation == Operation.ANALYSIS
                        && (error instanceof LlmException || error instanceof InvalidLlmResponseException);
                String prefix = llmFailure ? "[ERREUR IA] "
                        : operation == Operation.REPORT ? "[ERREUR RAPPORT] " : "[ERREUR] ";
                logPanel.appendLog(prefix + message);
                if (selectedProject == null) {
                    selectionBar.setProjectPath("Aucun projet sélectionné");
                }
                String failureStatus = switch (operation) {
                    case PROJECT -> "Échec de la lecture du projet.";
                    case ANALYSIS -> "Échec de l'analyse. Corrige la configuration puis réessaie.";
                    case REPORT -> "Échec de la génération du rapport.";
                };
                configPanel.updateProgress(0, failureStatus);
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

    private enum Operation {
        PROJECT,
        ANALYSIS,
        REPORT
    }

    private void setBusy(boolean value) {
        busy = value;
        selectionBar.setDisable(value);
        configPanel.setBusy(value);
        logPanel.setReportButtonEnabled(!value && lastReport != null);
    }


// Implementation de l'Observer

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
        if (modelTask != null) {
            modelTask.cancel(true);
        }
        worker.shutdownNow();
        modelWorker.shutdownNow();
        evaluationEngine.close();
    }
}

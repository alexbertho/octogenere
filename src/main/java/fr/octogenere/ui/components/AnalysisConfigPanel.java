package fr.octogenere.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import fr.octogenere.ui.controller.UIController;
import fr.octogenere.analysis.config.CriterionCatalog;
import fr.octogenere.analysis.config.EvaluationCriterion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Panneau central permettant à l'utilisateur de configurer les paramètres de l'analyse.
 * Affiche la liste des critères évalués, le bouton de lancement et la barre de progression.
 * Délègue les actions utilisateur exclusivement au UIController.
 */
public class AnalysisConfigPanel extends VBox {

    private UIController controller;
    private List<CheckBox> criteriaBoxes;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button startBtn;
    private ComboBox<String> modelSelector;
    private boolean updatingModels;

    /**
     * Constructeur du panneau de configuration.
     * @param controller Le contrôleur central qui gèrera les actions de ce panneau.
     */
    public AnalysisConfigPanel(UIController controller) {
        this(controller, CriterionCatalog.loadDefault().criteria(),
                "Les fichiers du projet seront préparés puis analysés par le LLM configuré.");
    }

    public AnalysisConfigPanel(UIController controller, List<EvaluationCriterion> criteria,
                               String modeDescription) {
        this.controller = controller;
        this.criteriaBoxes = new ArrayList<>();
        setupUI(criteria, modeDescription);
    }

    /**
     * Initialise et assemble les composants graphiques du panneau.
     */
    private void setupUI(List<EvaluationCriterion> criteria, String modeDescription) {
        this.setSpacing(15);
        this.setPadding(new Insets(20));

        Label title = new Label("Configuration de l'Analyse");
        title.getStyleClass().add("title");
        Label modeNotice = new Label(modeDescription);
        modeNotice.setWrapText(true);

        VBox criteriaContainer = new VBox(10);
        for (EvaluationCriterion criterion : criteria) {
            addCriterion(criteriaContainer, criterion.label(), criterion.enabled());
        }

        // Menu déroulant pour choisir le modèle d'IA
        VBox modelContainer = new VBox(5);
        Label modelLabel = new Label("Modèle d'IA :");
        modelLabel.getStyleClass().add("title");

        modelSelector = new ComboBox<>();
        modelSelector.setId("model-selector");
        modelSelector.getStyleClass().add("combo-box");
        modelSelector.setPromptText("Chargement des modèles...");

        // Prévenir le contrôleur si on change de modèle
        modelSelector.setOnAction(e -> {
            if (!updatingModels) {
                controller.onConfigurationChanged();
            }
        });
        modelContainer.getChildren().addAll(modelLabel, modelSelector);

        // Assemblage horizontal
        HBox topSection = new HBox(20);
        HBox.setHgrow(criteriaContainer, Priority.ALWAYS);
        topSection.getChildren().addAll(criteriaContainer, modelContainer);

        // Bouton de lancement
        startBtn = new Button("LANCER L'ANALYSE");
        startBtn.setId("start-analysis");
        startBtn.setMaxWidth(Double.MAX_VALUE);
        startBtn.getStyleClass().add("bouton-classique");

        startBtn.setOnAction(e -> handleStartAnalysis());

        // Zone de progression
        Label progTitle = new Label("Progression :");
        progTitle.getStyleClass().add("title");
        progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        statusLabel = new Label("Statut : En attente...");

        // Ajout de tous les composants au conteneur principal
        this.getChildren().addAll(
                title, modeNotice, topSection, new Separator(),
                startBtn, new Separator(),
                progTitle, progressBar, statusLabel
        );
    }

    /**
     * Ajoute une case à cocher (critère) dans le conteneur spécifié.
     * @param container Le conteneur parent
     * @param text Le libellé du critère.
     * @param selectedByDefault Vrai si la case doit être cochée par défaut.
     */
    private void addCriterion(VBox container, String text, boolean selectedByDefault) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selectedByDefault);
        // Si l'utilisateur modifie une case, on prévient le contrôleur pour invalider les anciens résultats
        cb.setOnAction(event -> controller.onConfigurationChanged());
        criteriaBoxes.add(cb);
        container.getChildren().add(cb);
    }

    /**
     * Récupère les critères sélectionnés et demande au contrôleur de démarrer l'analyse.
     */
    private void handleStartAnalysis() {
        List<String> selectedCriteria = new ArrayList<>();
        for (CheckBox cb : criteriaBoxes) {
            if (cb.isSelected()) {
                selectedCriteria.add(cb.getText());
            }
        }

        // Déléguer l'action au contrôleur
        controller.onStartAnalysis(selectedCriteria, modelSelector.getValue());
    }

    /**
     * Remplit le sélecteur avec les modèles découverts. Le modèle configuré est
     * conservé comme choix initial et sert de repli si la liste est vide.
     */
    public void setAvailableModels(List<String> models, String configuredModel) {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        if (models != null) {
            models.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(model -> !model.isBlank())
                    .forEach(options::add);
        }
        if (options.isEmpty() && configuredModel != null && !configuredModel.isBlank()) {
            options.add(configuredModel.trim());
        }

        String previousSelection = modelSelector.getValue();
        updatingModels = true;
        try {
            modelSelector.getItems().setAll(options);
            if (previousSelection != null && options.contains(previousSelection)) {
                modelSelector.getSelectionModel().select(previousSelection);
            } else if (configuredModel != null && options.contains(configuredModel.trim())) {
                modelSelector.getSelectionModel().select(configuredModel.trim());
            } else {
                modelSelector.getSelectionModel().selectFirst();
            }
        } finally {
            updatingModels = false;
        }
    }

    /**
     * Met à jour la barre de progression et le texte de statut.
     * (Appelé par le contrôleur depuis le thread JavaFX).
     * @param progress Valeur entre 0.0 (début) et 1.0 (fin), ou -1.0 pour un état indéterminé.
     * @param statusMessage Le message expliquant l'action en cours.
     */    public void updateProgress(double progress, String statusMessage) {
        progressBar.setProgress(progress);
        statusLabel.setText("Statut : " + statusMessage);
    }

    /**
     * Active ou désactive les contrôles (cases à cocher, bouton) pendant un traitement.
     * @param busy Vrai si une tâche est en cours.
     */
    public void setBusy(boolean busy) {
        startBtn.setDisable(busy);
        modelSelector.setDisable(busy);
        for (CheckBox checkBox : criteriaBoxes) {
            checkBox.setDisable(busy);
        }
    }
}

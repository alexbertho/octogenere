package fr.octogenere.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import fr.octogenere.ui.controller.UIController;

import java.util.ArrayList;
import java.util.List;

public class AnalysisConfigPanel extends VBox {

    private UIController controller;
    private List<CheckBox> criteriaBoxes;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button startBtn;

    public AnalysisConfigPanel(UIController controller) {
        this.controller = controller;
        this.criteriaBoxes = new ArrayList<>();
        setupUI();
    }

    private void setupUI() {
        this.setSpacing(15);
        this.setPadding(new Insets(20));

        Label title = new Label("Configuration de l'Analyse");
        title.getStyleClass().add("title");
        Label demoNotice = new Label("Mode démonstration : résultats fictifs, sans appel API.");
        demoNotice.setWrapText(true);

        // Création des critères d'évaluation basés sur le sujet
        VBox criteriaContainer = new VBox(10);
        addCriterion(criteriaContainer, "Qualité de l'Architecture (SOLID)", true);
        addCriterion(criteriaContainer, "Lisibilité et Qualité du code", true);
        addCriterion(criteriaContainer, "Gestion des exceptions", true);
        addCriterion(criteriaContainer, "Qualité de la documentation", false);
        addCriterion(criteriaContainer, "Sécurité", true);

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

        this.getChildren().addAll(
                title, demoNotice, criteriaContainer, new Separator(),
                startBtn, new Separator(),
                progTitle, progressBar, statusLabel
        );
    }

    private void addCriterion(VBox container, String text, boolean selectedByDefault) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selectedByDefault);
        cb.setOnAction(event -> controller.onConfigurationChanged());
        criteriaBoxes.add(cb);
        container.getChildren().add(cb);
    }

    private void handleStartAnalysis() {
        // Récupérer la liste des critères cochés
        List<String> selectedCriteria = new ArrayList<>();
        for (CheckBox cb : criteriaBoxes) {
            if (cb.isSelected()) {
                selectedCriteria.add(cb.getText());
            }
        }

        // Déléguer l'action au contrôleur
        controller.onStartAnalysis(selectedCriteria);
    }

    // Méthodes pour permettre au contrôleur (Observer) de mettre à jour l'UI
    public void updateProgress(double progress, String statusMessage) {
        progressBar.setProgress(progress);
        statusLabel.setText("Statut : " + statusMessage);
    }

    public void setBusy(boolean busy) {
        startBtn.setDisable(busy);
        for (CheckBox checkBox : criteriaBoxes) {
            checkBox.setDisable(busy);
        }
    }
}

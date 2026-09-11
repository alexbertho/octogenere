package fr.octogenere.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import fr.octogenere.ui.controller.UIController;

public class LogAndResultPanel extends VBox {

    private UIController controller;
    private TextArea logArea;
    private Button generateReportBtn;

    public LogAndResultPanel(UIController controller) {
        this(controller, "Sélectionne un projet puis lance l'analyse.\n");
    }

    public LogAndResultPanel(UIController controller, String initialMessage) {
        this.controller = controller;
        setupUI(initialMessage);
    }

    private void setupUI(String initialMessage) {
        this.setSpacing(10);
        this.setPadding(new Insets(10));

        Label title = new Label("Résultats & Logs");
        title.getStyleClass().add("title");

        logArea = new TextArea();
        logArea.setId("analysis-log");
        logArea.setEditable(false);
        logArea.setPrefRowCount(5);
        logArea.setText(initialMessage.endsWith("\n") ? initialMessage : initialMessage + "\n");
        logArea.getStyleClass().add("log-area");

        // Bouton de génération du rapport
        generateReportBtn = new Button("GÉNÉRER LE RAPPORT LATEX");
        generateReportBtn.setId("generate-report");
        generateReportBtn.setMaxWidth(Double.MAX_VALUE);
        generateReportBtn.getStyleClass().add("bouton-classique");
        generateReportBtn.setDisable(true); // Désactivé tant que l'analyse n'est pas finie

        generateReportBtn.setOnAction(e -> controller.onGenerateReport());

        this.getChildren().addAll(title, logArea, generateReportBtn);
    }

    // Méthode pour ajouter du texte à la console depuis le contrôleur
    public void appendLog(String message) {
        logArea.appendText(message + "\n");
    }

    // Méthode pour activer/désactiver le bouton de génération
    public void setReportButtonEnabled(boolean enabled) {
        generateReportBtn.setDisable(!enabled);
    }
}

package fr.octogenere.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import fr.octogenere.ui.controller.UIController;

/**
 * Panneau inférieur dédié à l'affichage des retours visuels (logs, statut, erreurs)
 * et au déclenchement de la génération du rapport final (LaTeX).
 */
public class LogAndResultPanel extends VBox {

    private UIController controller;
    private TextArea logArea;
    private Button generateReportBtn;

    /**
     * Constructeur du panneau de logs.
     * @param controller Le contrôleur recevant l'action de génération de rapport.
     */
    public LogAndResultPanel(UIController controller) {
        this(controller, "Sélectionne un projet puis lance l'analyse.\n");
    }

    public LogAndResultPanel(UIController controller, String initialMessage) {
        this.controller = controller;
        setupUI(initialMessage);
    }

    /**
     * Initialise les composants graphiques du panneau.
     */
    private void setupUI(String initialMessage) {
        this.setSpacing(10);
        this.setPadding(new Insets(10));

        Label title = new Label("Résultats & Logs");
        title.getStyleClass().add("title");

        // Configuration de la console de logs en lecture seule
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

        // Désactivé par défaut tant qu'il n'y a pas de résultat
        generateReportBtn.setDisable(true);
        generateReportBtn.setOnAction(e -> controller.onGenerateReport());

        this.getChildren().addAll(title, logArea, generateReportBtn);
    }

    /**
     * Ajoute une nouvelle ligne de texte dans la zone de logs.
     * @param message Le texte à afficher.
     */
    public void appendLog(String message) {
        logArea.appendText(message + "\n");
    }

    /**
     * Active ou désactive le bouton de génération du rapport.
     * @param enabled Vrai pour autoriser la génération du rapport.
     */
    public void setReportButtonEnabled(boolean enabled) {
        generateReportBtn.setDisable(!enabled);
    }
}

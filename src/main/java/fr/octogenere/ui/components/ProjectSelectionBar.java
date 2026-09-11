package fr.octogenere.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import fr.octogenere.ui.controller.UIController;
import java.io.File;

/**
 * Barre supérieure de l'application gérant la sélection du répertoire source.
 * Utilise le composant JavaFX DirectoryChooser pour ouvrir l'explorateur natif du système.
 */
public class ProjectSelectionBar extends HBox {

    private Label pathLabel;
    private UIController controller;
    private Stage parentStage;

    /**
     * Constructeur de la barre de sélection.
     * @param stage La fenêtre parente (nécessaire pour l'explorateur de fichiers natif).
     * @param controller Le contrôleur à notifier.
     */
    public ProjectSelectionBar(Stage stage, UIController controller) {
        this.parentStage = stage;
        this.controller = controller;

        setupUI();
    }

    /**
     * Configure le layout horizontal et les boutons.
     */
    private void setupUI() {
        this.setSpacing(15);
        this.setPadding(new Insets(10));
        this.getStyleClass().add("top-bar");
        this.setAlignment(Pos.CENTER_LEFT);

        Button selectBtn = new Button("Sélectionner le projet");
        selectBtn.setId("select-project");
        selectBtn.getStyleClass().add("bouton-classique");
        pathLabel = new Label("Chemin : Aucun projet sélectionné");

        selectBtn.setOnAction(e -> handleProjectSelection());

        this.getChildren().addAll(selectBtn, pathLabel);
    }

    /**
     * Ouvre une boîte de dialogue permettant à l'utilisateur de choisir un dossier.
     */
    private void handleProjectSelection() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choisir le dossier du projet Java");
        // Bloque l'interface principale pendant que la fenêtre de sélection est ouverte
        File selectedDirectory = chooser.showDialog(parentStage);

        if (selectedDirectory != null) {
            // Délégation de la logique au contrôleur
            controller.onProjectSelected(selectedDirectory);
        }
    }

    /**
     * Met à jour le texte affichant le chemin du projet sélectionné.
     * @param path Le chemin absolu ou un message de statut ("Chargement...").
     */
    public void setProjectPath(String path) {
        pathLabel.setText("Chemin : " + path);
    }
}

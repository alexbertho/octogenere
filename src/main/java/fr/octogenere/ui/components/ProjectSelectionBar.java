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

public class ProjectSelectionBar extends HBox {

    private Label pathLabel;
    private UIController controller;
    private Stage parentStage;

    public ProjectSelectionBar(Stage stage, UIController controller) {
        this.parentStage = stage;
        this.controller = controller;

        setupUI();
    }

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

    private void handleProjectSelection() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choisir le dossier du projet Java");
        File selectedDirectory = chooser.showDialog(parentStage);

        if (selectedDirectory != null) {
            // Délégation de la logique au contrôleur
            controller.onProjectSelected(selectedDirectory);
        }
    }

    public void setProjectPath(String path) {
        pathLabel.setText("Chemin : " + path);
    }
}

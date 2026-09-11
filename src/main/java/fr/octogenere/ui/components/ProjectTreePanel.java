package fr.octogenere.ui.components;


import fr.octogenere.project.ProjectExplorerService.FileNode;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ProjectTreePanel extends VBox {

    private TreeView<String> treeView;

    public ProjectTreePanel() {
        setupUI();
    }

    private void setupUI() {
        this.setSpacing(5);
        this.setPadding(new Insets(10));
        this.setPrefWidth(250);
        this.getStyleClass().add("tree-panel");
        BorderPane.setMargin(this, new Insets(15));

        Label title = new Label("Arborescence");
        title.getStyleClass().add("title");

        treeView = new TreeView<>();
        treeView.setId("project-tree");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.setCellFactory(tv -> new TreeCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("java-file", "hidden-file", "other-file");
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.endsWith(".java")) {
                        getStyleClass().add("java-file");
                    } else if (item.startsWith(".")) {
                        getStyleClass().add("hidden-file");
                    } else {
                        getStyleClass().add("other-file");
                    }
                }
            }
        });

        this.getChildren().addAll(title, treeView);
    }

    // Méthode appelée par le contrôleur lorsqu'un projet est sélectionné
    public void loadProjectTree(FileNode rootDirectory) {
        TreeItem<String> rootItem = createTreeItem(rootDirectory);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
    }

    public void clear() {
        treeView.setRoot(null);
    }

    // Parcours récursif du dossier
    private TreeItem<String> createTreeItem(FileNode file) {
        TreeItem<String> item = new TreeItem<>(file.name());

        if (file.isDirectory()) {
            for (FileNode child : file.children()) {
                item.getChildren().add(createTreeItem(child));
            }
        }
        return item;
    }
}

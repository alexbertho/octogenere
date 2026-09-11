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

/**
 * Panneau latéral gauche affichant l'arborescence des fichiers du projet analysé.
 * Utilise un TreeView JavaFX avec un rendu personnalisé des cellules (CellFactory).
 */
public class ProjectTreePanel extends VBox {

    private TreeView<String> treeView;

    public ProjectTreePanel() {
        setupUI();
    }

    /**
     * Initialise l'interface utilisateur et configure le style personnalisé de l'arbre.
     */
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

        // Personnalisation des cellules pour appliquer les couleurs selon l'extension du fichier
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
                    // Application des classes CSS définies dans style.css
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

    /**
     * Charge une nouvelle arborescence de fichiers dans le panneau.
     * @param rootDirectory Le nœud racine généré par le ProjectExplorerService.
     */
    public void loadProjectTree(FileNode rootDirectory) {
        TreeItem<String> rootItem = createTreeItem(rootDirectory);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
    }

    /**
     * Vide le contenu de l'arbre (utilisé lors du chargement d'un nouveau projet).
     */
    public void clear() {
        treeView.setRoot(null);
    }

    /**
     * Construit récursivement les éléments de l'arbre JavaFX (TreeItem)
     * à partir de la structure de données métier (FileNode).
     */
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

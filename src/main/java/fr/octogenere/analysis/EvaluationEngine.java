package fr.octogenere.analysis;

import fr.octogenere.analysis.model.EvaluationReport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Un moteur produit des résultats ; l'interface choisit comment les afficher. */
public interface EvaluationEngine extends AutoCloseable {
    EvaluationReport analyze(Path projectDirectory, List<String> criteria,
                             AnalysisObserver observer) throws IOException;

    /** Exécute l'analyse avec le modèle choisi dans l'interface. */
    default EvaluationReport analyze(Path projectDirectory, List<String> criteria,
                                     String model, AnalysisObserver observer) throws IOException {
        return analyze(projectDirectory, criteria, observer);
    }

    /** Modèles proposés par le fournisseur courant. */
    default List<String> availableModels() {
        return List.of(configuredModel());
    }

    /** Modèle de repli lu dans la configuration locale. */
    default String configuredModel() {
        return "modèle configuré";
    }

    @Override
    default void close() {
        // Les moteurs sans ressource, comme le mode démonstration, n'ont rien à fermer.
    }
}

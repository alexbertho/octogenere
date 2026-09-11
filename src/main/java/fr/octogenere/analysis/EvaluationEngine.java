package fr.octogenere.analysis;

import fr.octogenere.analysis.model.EvaluationReport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Un moteur produit des résultats ; l'interface choisit comment les afficher. */
public interface EvaluationEngine extends AutoCloseable {
    EvaluationReport analyze(Path projectDirectory, List<String> criteria,
                             AnalysisObserver observer) throws IOException;

    @Override
    default void close() {
        // Les moteurs sans ressource, comme le mode démonstration, n'ont rien à fermer.
    }
}

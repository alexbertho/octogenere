package fr.octogenere.report;

import fr.octogenere.analysis.model.EvaluationReport;

import java.nio.file.Path;

/** Contrat commun aux différents formats de rapport. */
public interface ReportGenerator {
    /** Génère un rapport et renvoie le chemin du fichier créé. */
    Path generate(EvaluationReport report, Path outputDirectory);
}

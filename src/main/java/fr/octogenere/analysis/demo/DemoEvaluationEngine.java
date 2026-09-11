package fr.octogenere.analysis.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.octogenere.analysis.AnalysisObserver;
import fr.octogenere.analysis.EvaluationEngine;
import fr.octogenere.analysis.llm.CriterionResultParser;
import fr.octogenere.analysis.model.CriterionResult;
import fr.octogenere.analysis.model.EvaluationReport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fournit des résultats fictifs pour essayer l'UI et l'export sans appel API. */
public class DemoEvaluationEngine implements EvaluationEngine {
    private final CriterionResultParser parser = new CriterionResultParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public EvaluationReport analyze(Path projectDirectory, List<String> criteria,
                                    AnalysisObserver observer) throws IOException {
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            throw new IllegalArgumentException("Sélectionne un dossier de projet existant.");
        }
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException("Sélectionne au moins un critère.");
        }
        Objects.requireNonNull(observer, "L'observateur doit être présent.");
        List<String> selectedCriteria = List.copyOf(criteria);
        List<CriterionResult> results = new ArrayList<>();
        observer.onNewLogAdded("[DÉMO] Résultats fictifs : aucun fichier envoyé à un LLM.");

        for (String criterion : selectedCriteria) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("La démonstration a été interrompue.");
            }
            if (criterion.isBlank()) {
                throw new IllegalArgumentException("Un critère ne doit pas être vide.");
            }
            results.add(parser.parse(exampleJson(criterion)));
            observer.onProgressUpdate((double) results.size() / selectedCriteria.size(),
                    "Démonstration : " + criterion);
            observer.onNewLogAdded("[DÉMO] JSON validé pour : " + criterion);
        }

        Path project = projectDirectory.toAbsolutePath().normalize();
        String projectName = project.getFileName() == null ? project.toString() : project.getFileName().toString();
        return new EvaluationReport(projectName, LocalDate.now(),
                "Démonstration sans analyse du code ; critères : " + String.join(", ", selectedCriteria),
                "Aucun LLM (démonstration)", results,
                "DÉMONSTRATION : ces résultats sont fictifs et servent uniquement à tester "
                        + "l'interface et la génération LaTeX. Ils ne constituent pas une évaluation du projet.");
    }

    private String exampleJson(String criterion) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/analysis/demo/criterion-result.json")) {
            if (input == null) {
                throw new IOException("Le JSON de démonstration est introuvable.");
            }
            ObjectNode example = (ObjectNode) objectMapper.readTree(input);
            // Jackson protège notamment les guillemets présents dans un nom de critère.
            example.put("criterion", criterion);
            return objectMapper.writeValueAsString(example);
        }
    }
}

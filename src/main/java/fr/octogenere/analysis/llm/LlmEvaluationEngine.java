package fr.octogenere.analysis.llm;

import fr.octogenere.analysis.AnalysisObserver;
import fr.octogenere.analysis.EvaluationEngine;
import fr.octogenere.analysis.config.CriterionCatalog;
import fr.octogenere.analysis.config.EvaluationCriterion;
import fr.octogenere.analysis.model.EvaluationReport;
import fr.octogenere.llm.LlmProvider;
import fr.octogenere.prompt.PromptBuilder;
import fr.octogenere.prompt.xml.ProjectContext;
import fr.octogenere.prompt.xml.XmlFileTreeBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Pipeline d'analyse réelle : projet, prompt, provider, validation puis rapport métier. */
public final class LlmEvaluationEngine implements EvaluationEngine {
    private final LlmProvider provider;
    private final CriterionCatalog criterionCatalog;
    private final XmlFileTreeBuilder contextBuilder;
    private final PromptBuilder promptBuilder;
    private final EvaluationResponseParser responseParser;

    public LlmEvaluationEngine(LlmProvider provider, CriterionCatalog criterionCatalog) {
        this(provider, criterionCatalog, new XmlFileTreeBuilder(), new PromptBuilder(),
                new EvaluationResponseParser());
    }

    LlmEvaluationEngine(LlmProvider provider, CriterionCatalog criterionCatalog,
                        XmlFileTreeBuilder contextBuilder, PromptBuilder promptBuilder,
                        EvaluationResponseParser responseParser) {
        this.provider = Objects.requireNonNull(provider);
        this.criterionCatalog = Objects.requireNonNull(criterionCatalog);
        this.contextBuilder = Objects.requireNonNull(contextBuilder);
        this.promptBuilder = Objects.requireNonNull(promptBuilder);
        this.responseParser = Objects.requireNonNull(responseParser);
    }

    @Override
    public EvaluationReport analyze(Path projectDirectory, List<String> criteria,
                                    AnalysisObserver observer) throws IOException {
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            throw new IllegalArgumentException("Sélectionne un dossier de projet existant.");
        }
        Objects.requireNonNull(observer, "L'observateur doit être présent.");
        List<EvaluationCriterion> selectedCriteria = criterionCatalog.resolveLabels(criteria);
        Path project = projectDirectory.toAbsolutePath().normalize();
        String projectName = project.getFileName() == null ? project.toString() : project.getFileName().toString();

        checkInterrupted();
        observer.onProgressUpdate(0.05, "Préparation du projet...");
        observer.onNewLogAdded("[INFO] Préparation des fichiers pour l'analyse LLM.");
        ProjectContext context = contextBuilder.build(project);
        observer.onProgressUpdate(0.30, "Contexte du projet construit.");
        observer.onNewLogAdded("[INFO] " + context.includedFiles() + " fichier(s) inclus, "
                + context.omittedFiles() + " omis.");
        if (context.truncated()) {
            observer.onNewLogAdded("[ATTENTION] Le contexte a été limité pour respecter la taille maximale.");
        }
        if (!context.suspiciousFiles().isEmpty()) {
            observer.onNewLogAdded("[SÉCURITÉ] Contenu potentiellement injecté détecté dans : "
                    + String.join(", ", context.suspiciousFiles()));
        }

        checkInterrupted();
        String prompt = promptBuilder.buildReviewPrompt(projectName, selectedCriteria, context);
        observer.onProgressUpdate(0.40, "Prompt d'analyse prêt.");
        observer.onNewLogAdded("[INFO] Appel de " + provider.providerName()
                + " avec le modèle " + provider.modelName() + ".");
        String json = provider.ask(prompt);

        checkInterrupted();
        observer.onProgressUpdate(0.80, "Réponse reçue, validation en cours...");
        EvaluationResponseParser.ParsedResponse parsed = responseParser.parse(json, selectedCriteria);
        observer.onNewLogAdded("[INFO] Réponse du LLM validée.");
        observer.onProgressUpdate(1.0, "Analyse terminée.");

        String configuration = "Analyse LLM du code source ; critères : "
                + selectedCriteria.stream().map(EvaluationCriterion::label).reduce((a, b) -> a + ", " + b).orElse("");
        return new EvaluationReport(projectName, LocalDate.now(), configuration,
                provider.providerName() + " — " + provider.modelName(), parsed.criteria(),
                parsed.overallSummary());
    }

    private void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("L'analyse a été interrompue.");
        }
    }

    @Override
    public void close() {
        provider.close();
    }
}

package fr.octogenere.analysis.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import fr.octogenere.analysis.AnalysisObserver;
import fr.octogenere.analysis.EvaluationEngine;
import fr.octogenere.analysis.config.CriterionCatalog;
import fr.octogenere.analysis.config.EvaluationCriterion;
import fr.octogenere.analysis.model.EvaluationReport;
import fr.octogenere.llm.LlmProvider;
import fr.octogenere.prompt.PromptBuilder;
import fr.octogenere.prompt.xml.ProjectContext;
import fr.octogenere.prompt.xml.XmlFileTreeBuilder;
import fr.octogenere.security.injection.PromptInjectionGuard;
import fr.octogenere.security.sandbox.SandboxException;
import fr.octogenere.security.sandbox.SandboxResult;
import fr.octogenere.security.sandbox.SandboxService;

/**
 * Pipeline d'analyse réelle : projet, prompt, provider, validation puis rapport
 * métier.
 */
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
        return analyze(projectDirectory, criteria, provider.modelName(), observer);
    }

    @Override
    public EvaluationReport analyze(Path projectDirectory, List<String> criteria,
            String model, AnalysisObserver observer) throws IOException {
        if (projectDirectory == null || !Files.isDirectory(projectDirectory)) {
            throw new IllegalArgumentException("Sélectionne un dossier de projet existant.");
        }
        Objects.requireNonNull(observer, "L'observateur doit être présent.");
        String selectedModel = requireModel(model);
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
        observer.onProgressUpdate(0.35, "Exécution des tests dans la sandbox...");
        observer.onNewLogAdded("[INFO] Compilation et tests dans la sandbox.");

        String sandboxLogs;
        try {
            SandboxService sandbox = SandboxService.createDefault();
            SandboxResult sandboxResult = sandbox.run(project, List.of("mvn", "-q", "clean", "test"));

            PromptInjectionGuard guard = PromptInjectionGuard.createDefault();
            PromptInjectionGuard.GuardedContent guardedStdout =
                    guard.protect("sandbox-stdout", sandboxResult.stdout());
            PromptInjectionGuard.GuardedContent guardedStderr =
                    guard.protect("sandbox-stderr", sandboxResult.stderr());
            if (guardedStdout.scanResult().suspicious() || guardedStderr.scanResult().suspicious()) {
                observer.onNewLogAdded("[SÉCURITÉ] Contenu potentiellement injecté détecté "
                        + "dans la sortie de la sandbox.");
            }

            sandboxLogs = "Code de sortie : " + sandboxResult.exitCode()
                    + "\nSortie standard :\n" + guardedStdout.safePromptFragment()
                    + "\nErreurs :\n" + guardedStderr.safePromptFragment();
            observer.onNewLogAdded("[INFO] Tests sandbox terminés avec le code "
                    + sandboxResult.exitCode() + ".");
        } catch (SandboxException error) {
            sandboxLogs = "Sandbox indisponible : analyse statique uniquement.";
            observer.onNewLogAdded("[ATTENTION] Sandbox indisponible ; poursuite de l'analyse statique.");
        }

        String prompt = promptBuilder.buildReviewPrompt(projectName, selectedCriteria, context, sandboxLogs);

        observer.onProgressUpdate(0.40, "Prompt d'analyse prêt.");
        observer.onNewLogAdded("[INFO] Appel de " + provider.providerName()
                + " avec le modèle " + selectedModel + ".");
        String json = provider.ask(prompt, selectedModel);

        checkInterrupted();
        observer.onProgressUpdate(0.80, "Réponse reçue, validation en cours...");
        EvaluationResponseParser.ParsedResponse parsed = responseParser.parse(json, selectedCriteria);
        observer.onNewLogAdded("[INFO] Réponse du LLM validée.");
        observer.onProgressUpdate(1.0, "Analyse terminée.");

        String configuration = "Analyse LLM du code source ; critères : "
                + selectedCriteria.stream().map(EvaluationCriterion::label).reduce((a, b) -> a + ", " + b).orElse("");
        return new EvaluationReport(projectName, LocalDate.now(), configuration,
                provider.providerName() + " — " + selectedModel, parsed.criteria(),
                parsed.overallSummary());
    }

    @Override
    public List<String> availableModels() {
        return provider.availableModels();
    }

    @Override
    public String configuredModel() {
        return provider.modelName();
    }

    private String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Sélectionne un modèle d'IA.");
        }
        return model.trim();
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

package fr.octogenere.security.injection;

/**
 * Looks for prompt injection attempts in a piece of content (Strategy
 * pattern): e.g. a code comment asking the model to ignore its previous
 * instructions. {@link HeuristicPromptInjectionScanner} is the current
 * implementation; other approaches (LLM-based, or driven by an externalized
 * rule list) could be added later without touching the callers.
 */
public interface PromptInjectionScanner {
    ScanResult scan(String content);
}

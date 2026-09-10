package fr.octogenere.analysis.llm;

/** Signale qu'une réponse du LLM ne respecte pas le format d'évaluation attendu. */
public class InvalidLlmResponseException extends RuntimeException {
    public InvalidLlmResponseException(String message) {
        super(message);
    }

    public InvalidLlmResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}

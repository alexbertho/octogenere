package fr.octogenere.llm;

/** Erreur commune aux fournisseurs LLM, indépendante de leurs SDK. */
public class LlmException extends RuntimeException {
    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}

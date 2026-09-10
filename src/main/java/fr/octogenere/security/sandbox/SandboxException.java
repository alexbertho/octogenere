package fr.octogenere.security.sandbox;

/** Erreur liée à l'exécution isolée : Docker absent, conteneur inaccessible, etc. */
public class SandboxException extends RuntimeException {
    public SandboxException(String message) {
        super(message);
    }

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}

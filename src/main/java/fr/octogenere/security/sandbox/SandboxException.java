package fr.octogenere.security.sandbox;

/** Error related to isolated execution: Docker missing, container unreachable, etc. */
public class SandboxException extends RuntimeException {
    public SandboxException(String message) {
        super(message);
    }

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}

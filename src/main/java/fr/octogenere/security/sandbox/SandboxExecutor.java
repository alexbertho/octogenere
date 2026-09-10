package fr.octogenere.security.sandbox;

/**
 * Runs a command in an isolated environment (Strategy pattern).
 * {@link DockerSandboxExecutor} is the real implementation; tests for other
 * modules can provide their own implementation (see {@code FakeSandboxExecutor}
 * in this package's tests) without ever launching Docker.
 */
public interface SandboxExecutor {
    SandboxResult execute(ExecutionRequest request);
}

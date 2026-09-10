package fr.octogenere.security.sandbox;

import java.nio.file.Path;
import java.util.List;

/**
 * Simple entry point for the rest of the application (Facade pattern): the
 * analysis module doesn't need to know about Docker, containers, or default
 * limits, only "run this command on this project, isolated".
 */
public final class SandboxService {
    private final SandboxExecutor executor;

    public SandboxService(SandboxExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor cannot be null");
        }
        this.executor = executor;
    }

    public static SandboxService createDefault() {
        return new SandboxService(new DockerSandboxExecutor());
    }

    /** Runs a command with the default limits ({@link SandboxLimits#defaults()}). */
    public SandboxResult run(Path projectDirectory, List<String> command) {
        return run(ExecutionRequest.builder(projectDirectory, command).build());
    }

    public SandboxResult run(ExecutionRequest request) {
        return executor.execute(request);
    }
}

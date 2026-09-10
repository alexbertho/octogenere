package fr.octogenere.security.sandbox;

import java.nio.file.Path;
import java.util.List;

/**
 * Point d'entrée simple pour le reste de l'application (pattern Facade) : le
 * module analyse n'a pas besoin de connaître Docker, les conteneurs ou les
 * limites par défaut, seulement "exécute cette commande sur ce projet, isolée".
 */
public final class SandboxService {
    private final SandboxExecutor executor;

    public SandboxService(SandboxExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor ne peut pas être nul");
        }
        this.executor = executor;
    }

    public static SandboxService createDefault() {
        return new SandboxService(new DockerSandboxExecutor());
    }

    /** Exécute une commande avec les limites par défaut ({@link SandboxLimits#defaults()}). */
    public SandboxResult run(Path projectDirectory, List<String> command) {
        return run(ExecutionRequest.builder(projectDirectory, command).build());
    }

    public SandboxResult run(ExecutionRequest request) {
        return executor.execute(request);
    }
}

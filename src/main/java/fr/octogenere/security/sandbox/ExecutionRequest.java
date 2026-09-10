package fr.octogenere.security.sandbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes a command to run in an isolated environment: the project
 * directory to mount (read-only), the command itself, and the limits to
 * apply. Immutable, built via {@link #builder(Path, List)}.
 */
public final class ExecutionRequest {
    private final Path projectDirectory;
    private final List<String> command;
    private final SandboxLimits limits;
    private final Map<String, String> environment;

    private ExecutionRequest(Builder builder) {
        this.projectDirectory = builder.projectDirectory;
        this.command = List.copyOf(builder.command);
        this.limits = builder.limits;
        this.environment = Map.copyOf(builder.environment);
    }

    public Path projectDirectory() {
        return projectDirectory;
    }

    public List<String> command() {
        return command;
    }

    public SandboxLimits limits() {
        return limits;
    }

    public Map<String, String> environment() {
        return environment;
    }

    public static Builder builder(Path projectDirectory, List<String> command) {
        return new Builder(projectDirectory, command);
    }

    public static final class Builder {
        private final Path projectDirectory;
        private final List<String> command;
        private SandboxLimits limits = SandboxLimits.defaults();
        private final Map<String, String> environment = new LinkedHashMap<>();

        private Builder(Path projectDirectory, List<String> command) {
            if (projectDirectory == null) {
                throw new IllegalArgumentException("projectDirectory cannot be null");
            }
            if (!Files.isDirectory(projectDirectory)) {
                throw new IllegalArgumentException("projectDirectory must be an existing directory: " + projectDirectory);
            }
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("command cannot be empty");
            }
            this.projectDirectory = projectDirectory;
            this.command = new ArrayList<>(command);
        }

        public Builder limits(SandboxLimits limits) {
            if (limits == null) {
                throw new IllegalArgumentException("limits cannot be null");
            }
            this.limits = limits;
            return this;
        }

        public Builder networkEnabled(boolean enabled) {
            this.limits = this.limits.withNetworkEnabled(enabled);
            return this;
        }

        public Builder env(String key, String value) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("environment variable name cannot be blank");
            }
            this.environment.put(key, value);
            return this;
        }

        public ExecutionRequest build() {
            return new ExecutionRequest(this);
        }
    }
}

package fr.octogenere.security.sandbox;

import java.time.Duration;

/** Result of a container execution: separate stdout/stderr, exit code, duration. */
public record SandboxResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        Duration duration
) {
    public boolean succeeded() {
        return !timedOut && exitCode == 0;
    }
}

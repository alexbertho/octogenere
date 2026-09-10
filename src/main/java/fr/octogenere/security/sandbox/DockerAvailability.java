package fr.octogenere.security.sandbox;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Checks whether the Docker CLI and its daemon respond, so integration tests
 * can cleanly disable themselves (skip, not fail) on a machine without Docker.
 */
public final class DockerAvailability {
    private static Boolean cached;

    private DockerAvailability() {
    }

    public static synchronized boolean isDockerAvailable() {
        if (cached == null) {
            cached = probe();
        }
        return cached;
    }

    private static boolean probe() {
        try {
            Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}

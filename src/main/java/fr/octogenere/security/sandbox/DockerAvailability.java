package fr.octogenere.security.sandbox;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Vérifie si le CLI Docker et le démon associé répondent, pour permettre aux
 * tests d'intégration de se désactiver proprement (au lieu d'échouer) sur une
 * machine sans Docker.
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

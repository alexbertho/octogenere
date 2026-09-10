package fr.octogenere.security.sandbox;

import java.time.Duration;

/** Résultat d'une exécution en conteneur : sortie standard/erreur séparées, code de retour, durée. */
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

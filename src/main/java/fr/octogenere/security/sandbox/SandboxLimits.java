package fr.octogenere.security.sandbox;

import java.time.Duration;

/**
 * Limites de ressources appliquées à une exécution en conteneur.
 * Les valeurs par défaut ({@link #defaults()}) correspondent à ce qui suffit pour
 * compiler / tester un petit projet Java (mvn compile, mvn test) sans laisser un
 * projet malveillant consommer toute la machine.
 */
public record SandboxLimits(
        double cpus,
        int memoryMb,
        int pidsLimit,
        Duration timeout,
        boolean networkEnabled,
        int tmpfsSizeMb
) {
    public SandboxLimits {
        if (cpus <= 0) {
            throw new IllegalArgumentException("cpus doit être strictement positif");
        }
        if (memoryMb <= 0) {
            throw new IllegalArgumentException("memoryMb doit être strictement positif");
        }
        if (pidsLimit <= 0) {
            throw new IllegalArgumentException("pidsLimit doit être strictement positif");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout doit être strictement positif");
        }
        if (tmpfsSizeMb <= 0) {
            throw new IllegalArgumentException("tmpfsSizeMb doit être strictement positif");
        }
    }

    public static SandboxLimits defaults() {
        return new SandboxLimits(1.0, 512, 128, Duration.ofSeconds(120), false, 256);
    }

    public SandboxLimits withTimeout(Duration newTimeout) {
        return new SandboxLimits(cpus, memoryMb, pidsLimit, newTimeout, networkEnabled, tmpfsSizeMb);
    }

    public SandboxLimits withNetworkEnabled(boolean enabled) {
        return new SandboxLimits(cpus, memoryMb, pidsLimit, timeout, enabled, tmpfsSizeMb);
    }
}

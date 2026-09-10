package fr.octogenere.security.sandbox;

import java.time.Duration;

/**
 * Resource limits applied to a container execution.
 * The default values ({@link #defaults()}) are enough to compile / test a
 * small Java project (mvn compile, mvn test) without letting a malicious
 * project consume the whole machine.
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
            throw new IllegalArgumentException("cpus must be strictly positive");
        }
        if (memoryMb <= 0) {
            throw new IllegalArgumentException("memoryMb must be strictly positive");
        }
        if (pidsLimit <= 0) {
            throw new IllegalArgumentException("pidsLimit must be strictly positive");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be strictly positive");
        }
        if (tmpfsSizeMb <= 0) {
            throw new IllegalArgumentException("tmpfsSizeMb must be strictly positive");
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

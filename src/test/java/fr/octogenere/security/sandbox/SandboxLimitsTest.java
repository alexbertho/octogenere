package fr.octogenere.security.sandbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SandboxLimitsTest {

    @Test
    void defaultsAreSaneForASmallJavaBuild() {
        SandboxLimits limits = SandboxLimits.defaults();

        assertEquals(1.0, limits.cpus());
        assertEquals(512, limits.memoryMb());
        assertEquals(128, limits.pidsLimit());
        assertEquals(Duration.ofSeconds(120), limits.timeout());
        assertFalse(limits.networkEnabled());
    }

    @Test
    void witherMethodsLeaveOtherFieldsUntouched() {
        SandboxLimits base = SandboxLimits.defaults();

        SandboxLimits withTimeout = base.withTimeout(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30), withTimeout.timeout());
        assertEquals(base.cpus(), withTimeout.cpus());
        assertEquals(base.networkEnabled(), withTimeout.networkEnabled());

        SandboxLimits withNetwork = base.withNetworkEnabled(true);
        assertEquals(true, withNetwork.networkEnabled());
        assertEquals(base.timeout(), withNetwork.timeout());
    }

    @Test
    void rejectsNonPositiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxLimits(0, 512, 128, Duration.ofSeconds(60), false, 256));
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxLimits(1.0, -1, 128, Duration.ofSeconds(60), false, 256));
        assertThrows(IllegalArgumentException.class,
                () -> new SandboxLimits(1.0, 512, 128, Duration.ZERO, false, 256));
    }
}

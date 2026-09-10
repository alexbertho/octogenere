package fr.octogenere.security.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRequestTest {

    @Test
    void rejectsMissingProjectDirectory(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionRequest.builder(missing, List.of("echo", "hi")));
    }

    @Test
    void rejectsEmptyCommand(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class,
                () -> ExecutionRequest.builder(tempDir, List.of()));
    }

    @Test
    void appliesDefaultLimitsWhenNoneSpecified(@TempDir Path tempDir) {
        ExecutionRequest request = ExecutionRequest.builder(tempDir, List.of("echo", "hi")).build();

        assertEquals(SandboxLimits.defaults(), request.limits());
    }

    @Test
    void isImmutableAgainstModificationsOfTheOriginalCommandList(@TempDir Path tempDir) {
        List<String> command = new java.util.ArrayList<>(List.of("mvn", "test"));
        ExecutionRequest request = ExecutionRequest.builder(tempDir, command).build();

        command.add("should-not-appear");

        assertEquals(List.of("mvn", "test"), request.command());
        assertThrows(UnsupportedOperationException.class, () -> request.command().add("nope"));
    }

    @Test
    void networkEnabledOverridesOnlyTheNetworkFlag(@TempDir Path tempDir) {
        ExecutionRequest request = ExecutionRequest.builder(tempDir, List.of("mvn", "test"))
                .networkEnabled(true)
                .build();

        assertTrue(request.limits().networkEnabled());
        assertEquals(SandboxLimits.defaults().cpus(), request.limits().cpus());
    }
}

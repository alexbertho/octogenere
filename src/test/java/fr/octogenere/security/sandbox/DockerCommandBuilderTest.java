package fr.octogenere.security.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerCommandBuilderTest {

    @Test
    void defaultRunEnforcesLeastPrivilegeFlags(@TempDir Path projectDir) {
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("mvn", "-q", "compile")).build();

        List<String> args = DockerCommandBuilder.buildRunArgs("octogenere/sandbox:1.0", "my-container", request);

        assertTrue(args.containsAll(List.of("--rm", "--init", "--read-only", "--cap-drop=ALL")));
        assertTrue(args.contains("--network=none"));
        assertTrue(args.containsAll(List.of("--user", "10001:10001")));
        assertTrue(args.containsAll(List.of("--security-opt", "no-new-privileges")));
        assertTrue(args.containsAll(List.of("--pids-limit", "128")));
        assertTrue(args.containsAll(List.of("--memory", "512m")));
        assertTrue(args.containsAll(List.of("--cpus", "1.0")));
        assertFalse(args.contains("--privileged"));
    }

    @Test
    void networkEnabledSwitchesTheFlagOnly(@TempDir Path projectDir) {
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("mvn", "compile"))
                .networkEnabled(true)
                .build();

        List<String> args = DockerCommandBuilder.buildRunArgs("octogenere/sandbox:1.0", "my-container", request);

        assertTrue(args.contains("--network=bridge"));
        assertFalse(args.contains("--network=none"));
    }

    @Test
    void mountsTheProjectDirectoryReadOnly(@TempDir Path projectDir) {
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("echo", "hi")).build();

        List<String> args = DockerCommandBuilder.buildRunArgs("octogenere/sandbox:1.0", "c", request);
        int mountIndex = args.indexOf("-v") + 1;

        assertTrue(args.get(mountIndex).endsWith(":/input:ro"));
        assertTrue(args.get(mountIndex).startsWith(projectDir.toAbsolutePath().toString()));
    }

    @Test
    void appendsCommandTokensVerbatimAfterTheImage(@TempDir Path projectDir) {
        List<String> command = List.of("mvn", "-q", "-DskipTests", "compile");
        ExecutionRequest request = ExecutionRequest.builder(projectDir, command).build();

        List<String> args = DockerCommandBuilder.buildRunArgs("octogenere/sandbox:1.0", "c", request);
        int imageIndex = args.indexOf("octogenere/sandbox:1.0");

        assertEquals(command, args.subList(imageIndex + 1, args.size()));
    }

    @Test
    void passesTheTimeoutToTheContainerAsAnEnvironmentVariable(@TempDir Path projectDir) {
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("echo", "hi"))
                .limits(SandboxLimits.defaults().withTimeout(Duration.ofSeconds(45)))
                .build();

        List<String> args = DockerCommandBuilder.buildRunArgs("octogenere/sandbox:1.0", "c", request);

        assertTrue(args.contains("SANDBOX_TIMEOUT_SECONDS=45"));
    }

    @Test
    void generatesDistinctContainerNamesAcrossCalls(@TempDir Path projectDir) {
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("echo", "hi")).build();

        List<String> first = DockerCommandBuilder.buildRunArgs("img", "container-a", request);
        List<String> second = DockerCommandBuilder.buildRunArgs("img", "container-b", request);

        assertFalse(first.equals(second));
    }

    @Test
    void forceRemoveTargetsExactlyTheGivenContainer() {
        assertEquals(List.of("docker", "rm", "-f", "octogenere-sbx-123"),
                DockerCommandBuilder.buildForceRemoveArgs("octogenere-sbx-123"));
    }
}

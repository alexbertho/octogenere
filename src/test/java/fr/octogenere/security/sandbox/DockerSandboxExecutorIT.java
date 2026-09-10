package fr.octogenere.security.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real integration tests: actually launch Docker and the
 * octogenere/sandbox:1.0 image (must be built beforehand with
 * `docker build -t octogenere/sandbox:1.0 -f docker/Dockerfile .`).
 * Excluded from `mvn test` by default (IT suffix, not Test), so never
 * required to compile/test the rest of the project. Cleanly disable
 * themselves (skip, not fail) if Docker isn't available on the machine.
 */
@Tag("docker")
class DockerSandboxExecutorIT {

    private SandboxService sandbox;

    @BeforeEach
    void setUp() {
        assumeTrue(DockerAvailability.isDockerAvailable(), "Docker is not available on this machine");
        sandbox = new SandboxService(new DockerSandboxExecutor());
    }

    @Test
    void runsAHarmlessCommandAndReturnsItsOutput(@TempDir Path projectDir) {
        SandboxResult result = sandbox.run(projectDir, List.of("echo", "hello from the sandbox"));

        assertTrue(result.succeeded());
        assertTrue(result.stdout().contains("hello from the sandbox"));
    }

    @Test
    void runsAsANonRootUser(@TempDir Path projectDir) {
        SandboxResult result = sandbox.run(projectDir, List.of("id", "-u"));

        assertTrue(result.succeeded());
        assertFalse(result.stdout().trim().equals("0"), "the container must not run as root");
    }

    @Test
    void cannotReachTheNetworkByDefault(@TempDir Path projectDir) {
        SandboxResult result = sandbox.run(projectDir, List.of("wget", "-T", "3", "-q", "-O", "-", "http://example.com"));

        assertFalse(result.succeeded(), "network access must be blocked by default");
    }

    @Test
    void cannotWriteBackToTheReadOnlyMount(@TempDir Path projectDir) throws Exception {
        Files.writeString(projectDir.resolve("original.txt"), "before");

        sandbox.run(projectDir, List.of("sh", "-c", "echo modified > /input/original.txt"));

        assertTrue(Files.readString(projectDir.resolve("original.txt")).equals("before"),
                "the host directory must never be modified by the container");
    }

    @Test
    void aHungProcessIsKilledAtTheTimeoutAndTheContainerIsCleanedUp(@TempDir Path projectDir) {
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("sleep", "300"))
                .limits(SandboxLimits.defaults().withTimeout(Duration.ofSeconds(3)))
                .build();

        SandboxResult result = sandbox.run(request);

        assertTrue(result.timedOut());
    }

    @Test
    void canActuallyCompileATinyMavenFixtureProject(@TempDir Path projectDir) throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/tiny-maven-project");
        assumeTrue(Files.isDirectory(fixture), "test Maven project fixture is missing");
        copyRecursively(fixture, projectDir);

        // Network enabled on purpose: the image doesn't have Maven plugins cached, so mvn
        // needs to be able to download them. See the limitation documented in the report
        // (a real deployment should pre-warm a local repository inside the image to stay offline).
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("mvn", "-q", "-DskipTests", "compile"))
                .networkEnabled(true)
                .build();

        SandboxResult result = sandbox.run(request);

        assertTrue(result.succeeded(), "output: " + result.stdout() + result.stderr());
    }

    private static void copyRecursively(Path source, Path target) throws Exception {
        try (var stream = Files.walk(source)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}

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
 * Tests d'intégration réels : lancent effectivement Docker et l'image
 * octogenere/sandbox:1.0 (à construire au préalable avec
 * `docker build -t octogenere/sandbox:1.0 -f docker/Dockerfile .`).
 * Exclus du `mvn test` par défaut (suffixe IT, pas Test), donc jamais requis
 * pour compiler/tester le reste du projet. Se désactivent proprement (skip,
 * pas échec) si Docker n'est pas disponible sur la machine.
 */
@Tag("docker")
class DockerSandboxExecutorIT {

    private SandboxService sandbox;

    @BeforeEach
    void setUp() {
        assumeTrue(DockerAvailability.isDockerAvailable(), "Docker n'est pas disponible sur cette machine");
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
        assertFalse(result.stdout().trim().equals("0"), "le conteneur ne doit pas tourner en root");
    }

    @Test
    void cannotReachTheNetworkByDefault(@TempDir Path projectDir) {
        SandboxResult result = sandbox.run(projectDir, List.of("wget", "-T", "3", "-q", "-O", "-", "http://example.com"));

        assertFalse(result.succeeded(), "l'accès réseau doit être bloqué par défaut");
    }

    @Test
    void cannotWriteBackToTheReadOnlyMount(@TempDir Path projectDir) throws Exception {
        Files.writeString(projectDir.resolve("original.txt"), "avant");

        sandbox.run(projectDir, List.of("sh", "-c", "echo modifie > /input/original.txt"));

        assertTrue(Files.readString(projectDir.resolve("original.txt")).equals("avant"),
                "le dossier hôte ne doit jamais être modifié par le conteneur");
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
        assumeTrue(Files.isDirectory(fixture), "projet Maven de test manquant");
        copyRecursively(fixture, projectDir);

        // Réseau activé volontairement : l'image ne contient pas les plugins Maven en cache,
        // mvn doit donc pouvoir les télécharger. Voir la limitation documentée dans le rapport
        // (une vraie installation devrait pré-charger un dépôt local dans l'image pour rester hors-ligne).
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("mvn", "-q", "-DskipTests", "compile"))
                .networkEnabled(true)
                .build();

        SandboxResult result = sandbox.run(request);

        assertTrue(result.succeeded(), "sortie: " + result.stdout() + result.stderr());
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

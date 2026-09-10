package fr.octogenere.security.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie le comportement de DockerSandboxExecutor sans jamais lancer Docker,
 * grâce au seam ProcessLauncher. Le point le plus important testé ici : le
 * nettoyage ("docker rm -f") doit avoir lieu dans tous les cas (succès, timeout,
 * échec de lancement), pas seulement quand tout se passe bien.
 */
class DockerSandboxExecutorTest {

    @Test
    void returnsTheProcessOutputOnNormalCompletion(@TempDir Path projectDir) {
        List<List<String>> invocations = new ArrayList<>();
        DockerSandboxExecutor.ProcessLauncher launcher = argv -> {
            invocations.add(argv);
            if (argv.contains("rm")) {
                return new FakeProcess(0, "", "", false);
            }
            return new FakeProcess(0, "hello\n", "", false);
        };
        DockerSandboxExecutor executor = new DockerSandboxExecutor(DockerSandboxExecutor.DEFAULT_IMAGE, launcher);
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("echo", "hello")).build();

        SandboxResult result = executor.execute(request);

        assertEquals(0, result.exitCode());
        assertEquals("hello\n", result.stdout());
        assertFalse(result.timedOut());
        assertTrue(result.succeeded());
        assertTrue(invocations.stream().anyMatch(argv -> argv.contains("rm")),
                "le nettoyage docker rm -f doit avoir lieu même quand tout se passe bien");
    }

    @Test
    void forcesDestroyAndReportsTimeoutWhenTheProcessNeverFinishes(@TempDir Path projectDir) {
        List<List<String>> invocations = new ArrayList<>();
        FakeProcess hangingProcess = new FakeProcess(0, "", "", true);
        DockerSandboxExecutor.ProcessLauncher launcher = argv -> {
            invocations.add(argv);
            if (argv.contains("rm")) {
                return new FakeProcess(0, "", "", false);
            }
            return hangingProcess;
        };
        DockerSandboxExecutor executor = new DockerSandboxExecutor(DockerSandboxExecutor.DEFAULT_IMAGE, launcher);
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("sleep", "999"))
                .limits(SandboxLimits.defaults().withTimeout(java.time.Duration.ofMillis(1)))
                .build();

        SandboxResult result = executor.execute(request);

        assertTrue(result.timedOut());
        assertTrue(hangingProcess.wasDestroyedForcibly());
        assertTrue(invocations.stream().anyMatch(argv -> argv.contains("rm")),
                "le nettoyage doit aussi avoir lieu après un timeout");
    }

    @Test
    void detectsTheContainerInternalTimeoutEvenWhenTheJavaSideWaitCompletesNormally(@TempDir Path projectDir) {
        // Cas le plus courant en pratique : le `timeout --signal=KILL` de l'entrypoint déclenche
        // avant le timeout côté Java, docker run se termine donc "normalement" (du point de vue
        // de Process.waitFor) mais avec le code de sortie 137 propre à coreutils en mode KILL.
        DockerSandboxExecutor.ProcessLauncher launcher = argv ->
                argv.contains("rm") ? new FakeProcess(0, "", "", false) : new FakeProcess(137, "", "", false);
        DockerSandboxExecutor executor = new DockerSandboxExecutor(DockerSandboxExecutor.DEFAULT_IMAGE, launcher);
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("sleep", "300")).build();

        SandboxResult result = executor.execute(request);

        assertTrue(result.timedOut());
        assertEquals(137, result.exitCode());
    }

    @Test
    void cleansUpEvenWhenDockerCannotBeLaunched(@TempDir Path projectDir) {
        List<List<String>> invocations = new ArrayList<>();
        DockerSandboxExecutor.ProcessLauncher launcher = argv -> {
            invocations.add(argv);
            if (argv.contains("rm")) {
                return new FakeProcess(0, "", "", false);
            }
            throw new IOException("docker introuvable");
        };
        DockerSandboxExecutor executor = new DockerSandboxExecutor(DockerSandboxExecutor.DEFAULT_IMAGE, launcher);
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("echo", "hi")).build();

        assertThrows(SandboxException.class, () -> executor.execute(request));
        assertTrue(invocations.stream().anyMatch(argv -> argv.contains("rm")),
                "même si docker run échoue à démarrer, le nettoyage doit être tenté");
    }

    /** Simule un java.lang.Process sans jamais lancer de vrai process. */
    private static final class FakeProcess extends Process {
        private final int exitCode;
        private final ByteArrayInputStream stdout;
        private final ByteArrayInputStream stderr;
        private final boolean hangs;
        private final AtomicBoolean destroyedForcibly = new AtomicBoolean(false);

        FakeProcess(int exitCode, String stdout, String stderr, boolean hangs) {
            this.exitCode = exitCode;
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
            this.hangs = hangs;
        }

        boolean wasDestroyedForcibly() {
            return destroyedForcibly.get();
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return !hangs || destroyedForcibly.get();
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyedForcibly.set(true);
        }

        @Override
        public Process destroyForcibly() {
            destroyedForcibly.set(true);
            return this;
        }

        @Override
        public boolean isAlive() {
            return hangs && !destroyedForcibly.get();
        }
    }
}

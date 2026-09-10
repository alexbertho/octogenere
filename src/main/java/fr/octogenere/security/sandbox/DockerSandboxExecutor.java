package fr.octogenere.security.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Adapts the {@code docker} CLI to the {@link SandboxExecutor} contract
 * (Adapter pattern, same idea as {@code OpenAiProvider} adapting the OpenAI
 * SDK to {@code LlmProvider}).
 * <p>
 * Each call to {@link #execute(ExecutionRequest)} corresponds to a disposable
 * container: named, started, waited on (with a Java-side timeout on top of
 * the container's own internal timeout), then explicitly removed in a finally
 * block. {@code --rm} alone is not enough: if the client-side {@code docker
 * run} process is force-killed after a timeout, the container can keep
 * running server-side without {@code --rm} ever getting the chance to apply.
 */
public final class DockerSandboxExecutor implements SandboxExecutor {

    static final String DEFAULT_IMAGE = "octogenere/sandbox:1.0";

    private final String image;
    private final ProcessLauncher processLauncher;

    public DockerSandboxExecutor() {
        this(DEFAULT_IMAGE, DockerSandboxExecutor::launchRealProcess);
    }

    public DockerSandboxExecutor(String image) {
        this(image, DockerSandboxExecutor::launchRealProcess);
    }

    // Package-private constructor: lets tests inject a fake process launcher.
    DockerSandboxExecutor(String image, ProcessLauncher processLauncher) {
        this.image = image;
        this.processLauncher = processLauncher;
    }

    @Override
    public SandboxResult execute(ExecutionRequest request) {
        String containerName = "octogenere-sbx-" + UUID.randomUUID();
        List<String> runArgs = DockerCommandBuilder.buildRunArgs(image, containerName, request);
        Instant start = Instant.now();

        try {
            return runAndCollect(runArgs, request.limits().timeout(), start);
        } finally {
            forceRemoveContainer(containerName);
        }
    }

    private SandboxResult runAndCollect(List<String> runArgs, Duration timeout, Instant start) {
        Process process;
        try {
            process = processLauncher.launch(runArgs);
        } catch (IOException e) {
            throw new SandboxException("Could not launch Docker. Check that it is installed and running, "
                    + "and that the image " + image + " has been built (docker build).", e);
        }

        ExecutorService streamReaders = Executors.newFixedThreadPool(2);
        Future<String> stdoutFuture = streamReaders.submit(() -> readFully(process.getInputStream()));
        Future<String> stderrFuture = streamReaders.submit(() -> readFully(process.getErrorStream()));

        // Margin on top of the container's internal timeout: that one should normally fire
        // first and produce clean output; this Java-side timeout is just a safety net.
        Duration javaSideTimeout = timeout.plusSeconds(10);
        boolean timedOut;
        try {
            timedOut = !process.waitFor(javaSideTimeout.toSeconds(), TimeUnit.SECONDS);
            if (timedOut) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new SandboxException("Interrupted while waiting for the execution to finish.", e);
        }

        String stdout = collect(stdoutFuture);
        String stderr = collect(stderrFuture);
        streamReaders.shutdownNow();

        int exitCode = timedOut ? -1 : process.exitValue();
        // The container-internal timeout (coreutils `timeout --signal=KILL`, in the entrypoint)
        // is expected to fire before the Java-side one in the normal case; it shows up as exit
        // code 137 (killed by SIGKILL), not as the wait above actually timing out. Known
        // limitation: 137 is also what a process killed by the kernel OOM-killer (--memory
        // exceeded) exits with - we don't distinguish the two cases, both mean the command
        // couldn't run to completion anyway.
        if (!timedOut && exitCode == 137) {
            timedOut = true;
        }
        return new SandboxResult(exitCode, stdout, stderr, timedOut, Duration.between(start, Instant.now()));
    }

    private void forceRemoveContainer(String containerName) {
        try {
            Process cleanup = processLauncher.launch(DockerCommandBuilder.buildForceRemoveArgs(containerName));
            cleanup.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Best-effort cleanup: a failure here must not hide the execution result.
        }
    }

    private static String collect(Future<String> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return "";
        }
    }

    private static String readFully(InputStream input) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            input.transferTo(buffer);
        } catch (IOException e) {
            // The stream can close abruptly if the container is killed (destroyForcibly);
            // keep whatever was read so far.
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static Process launchRealProcess(List<String> argv) throws IOException {
        return new ProcessBuilder(argv).start();
    }

    @FunctionalInterface
    interface ProcessLauncher {
        Process launch(List<String> argv) throws IOException;
    }
}

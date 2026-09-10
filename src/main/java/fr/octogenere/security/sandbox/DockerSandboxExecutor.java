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
 * Adapte le CLI {@code docker} au contrat {@link SandboxExecutor} (pattern Adapter,
 * comme {@code OpenAiProvider} adapte le SDK OpenAI à {@code LlmProvider}).
 * <p>
 * Chaque appel à {@link #execute(ExecutionRequest)} correspond à un conteneur
 * jetable : nommé, lancé, attendu (avec timeout côté Java en plus du timeout
 * interne au conteneur), puis supprimé explicitement dans un bloc finally.
 * {@code --rm} seul ne suffit pas : si le process client {@code docker run} est
 * tué de force après un dépassement de délai, le conteneur peut continuer de
 * tourner côté démon sans que {@code --rm} n'ait eu l'occasion de s'appliquer.
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

    // Constructeur package-privé : permet aux tests d'injecter un lanceur de process simulé.
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
            throw new SandboxException("Impossible de lancer Docker. Vérifie qu'il est installé et démarré, "
                    + "et que l'image " + image + " a bien été construite (docker build).", e);
        }

        ExecutorService streamReaders = Executors.newFixedThreadPool(2);
        Future<String> stdoutFuture = streamReaders.submit(() -> readFully(process.getInputStream()));
        Future<String> stderrFuture = streamReaders.submit(() -> readFully(process.getErrorStream()));

        // Marge par rapport au timeout interne au conteneur : celui-ci doit normalement déclencher en premier
        // et produire une sortie propre ; ce timeout côté Java n'est qu'un filet de sécurité.
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
            throw new SandboxException("Attente de l'exécution interrompue.", e);
        }

        String stdout = collect(stdoutFuture);
        String stderr = collect(stderrFuture);
        streamReaders.shutdownNow();

        int exitCode = timedOut ? -1 : process.exitValue();
        // Le timeout interne au conteneur (coreutils `timeout --signal=KILL`, dans l'entrypoint)
        // est censé déclencher avant celui côté Java dans le cas normal ; il se traduit par le
        // code de sortie 137 (tué par SIGKILL), pas par un dépassement du délai d'attente
        // ci-dessus. Limite connue : le code 137 est aussi celui d'un process tué par
        // l'OOM-killer du noyau (--memory dépassé) - on ne distingue pas les deux cas, les deux
        // indiquent de toute façon "la commande n'a pas pu aller à son terme normalement".
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
            // Nettoyage best-effort : un échec ici ne doit pas masquer le résultat de l'exécution.
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
            // Le flux peut se fermer brutalement si le conteneur est tué (destroyForcibly) ; on garde ce qu'on a lu.
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

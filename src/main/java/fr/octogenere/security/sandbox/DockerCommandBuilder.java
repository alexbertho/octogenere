package fr.octogenere.security.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Construit les arguments de la commande {@code docker run} appliquant le
 * principe de moindre privilège. Fonction pure (aucun appel système) afin de
 * pouvoir vérifier chaque option sans avoir Docker installé.
 * <p>
 * On construit toujours une liste d'arguments, jamais une chaîne à passer à un
 * shell : la commande demandée par l'appelant ({@link ExecutionRequest#command()})
 * est transmise telle quelle en fin de liste, sans jamais être interprétée par
 * un shell côté hôte.
 */
final class DockerCommandBuilder {

    private DockerCommandBuilder() {
    }

    static List<String> buildRunArgs(String image, String containerName, ExecutionRequest request) {
        SandboxLimits limits = request.limits();
        List<String> args = new ArrayList<>();

        args.add("docker");
        args.add("run");
        args.add("--rm");
        args.add("--name");
        args.add(containerName);
        args.add("--init");
        args.add("--user");
        args.add("10001:10001");
        args.add("--read-only");
        args.add(limits.networkEnabled() ? "--network=bridge" : "--network=none");
        args.add("--cap-drop=ALL");
        args.add("--security-opt");
        args.add("no-new-privileges");
        args.add("--pids-limit");
        args.add(String.valueOf(limits.pidsLimit()));
        args.add("--memory");
        args.add(limits.memoryMb() + "m");
        args.add("--memory-swap");
        args.add(limits.memoryMb() + "m");
        args.add("--cpus");
        args.add(String.valueOf(limits.cpus()));
        args.add("--tmpfs");
        args.add("/workspace:rw,exec,size=" + limits.tmpfsSizeMb() + "m,uid=10001,gid=10001,mode=0700");
        // /tmp en écriture : des outils comme Maven (jansi) ou des JVM lancées dans le conteneur
        // s'attendent à pouvoir y écrire un fichier de verrou, même sans rapport avec /workspace.
        args.add("--tmpfs");
        args.add("/tmp:rw,exec,size=64m,uid=10001,gid=10001,mode=1777");
        args.add("-v");
        args.add(request.projectDirectory().toAbsolutePath() + ":/input:ro");
        args.add("-e");
        args.add("SANDBOX_TIMEOUT_SECONDS=" + limits.timeout().toSeconds());
        args.add("--stop-timeout");
        args.add("5");

        for (Map.Entry<String, String> entry : request.environment().entrySet()) {
            args.add("-e");
            args.add(entry.getKey() + "=" + entry.getValue());
        }

        args.add(image);
        args.addAll(request.command());

        return args;
    }

    static List<String> buildForceRemoveArgs(String containerName) {
        return List.of("docker", "rm", "-f", containerName);
    }
}

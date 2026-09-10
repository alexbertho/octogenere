package fr.octogenere.security.sandbox;

/**
 * Exécute une commande dans un environnement isolé (pattern Strategy).
 * {@link DockerSandboxExecutor} est l'implémentation réelle ; les tests des
 * autres modules peuvent fournir leur propre implémentation (voir
 * {@code FakeSandboxExecutor} dans les tests de ce paquet) sans jamais lancer Docker.
 */
public interface SandboxExecutor {
    SandboxResult execute(ExecutionRequest request);
}

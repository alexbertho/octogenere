package fr.octogenere.security.sandbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Double de test pour {@link SandboxExecutor}, réutilisable par les autres
 * modules (ex: le futur moteur d'analyse) pour tester leur code sans jamais
 * lancer Docker. Par défaut, une requête non configurée échoue plutôt que de
 * renvoyer un résultat silencieusement incorrect.
 */
public final class FakeSandboxExecutor implements SandboxExecutor {

    private final List<StubbedResponse> stubs = new ArrayList<>();
    private final List<ExecutionRequest> receivedRequests = new ArrayList<>();
    private SandboxResult defaultResult;

    public void whenCalled(Predicate<ExecutionRequest> matcher, SandboxResult result) {
        stubs.add(new StubbedResponse(matcher, result));
    }

    public void alwaysReturn(SandboxResult result) {
        this.defaultResult = result;
    }

    public List<ExecutionRequest> receivedRequests() {
        return List.copyOf(receivedRequests);
    }

    @Override
    public SandboxResult execute(ExecutionRequest request) {
        receivedRequests.add(request);

        for (StubbedResponse stub : stubs) {
            if (stub.matcher.test(request)) {
                return stub.result;
            }
        }
        if (defaultResult != null) {
            return defaultResult;
        }
        throw new SandboxException("Aucun résultat configuré dans FakeSandboxExecutor pour cette requête : "
                + request.command());
    }

    /** Résultat pratique pour les tests qui n'ont pas besoin de simuler un échec. */
    public static SandboxResult success(String stdout) {
        return new SandboxResult(0, stdout, "", false, Duration.ZERO);
    }

    private record StubbedResponse(Predicate<ExecutionRequest> matcher, SandboxResult result) {
    }
}

package fr.octogenere.security.sandbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Test double for {@link SandboxExecutor}, reusable by other modules (e.g.
 * the future analysis engine) to test their code without ever launching
 * Docker. By default, an unstubbed request fails instead of silently
 * returning an incorrect result.
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
        throw new SandboxException("No result configured in FakeSandboxExecutor for this request: "
                + request.command());
    }

    /** Convenience result for tests that don't need to simulate a failure. */
    public static SandboxResult success(String stdout) {
        return new SandboxResult(0, stdout, "", false, Duration.ZERO);
    }

    private record StubbedResponse(Predicate<ExecutionRequest> matcher, SandboxResult result) {
    }
}

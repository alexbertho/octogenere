package fr.octogenere.security.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SandboxServiceTest {

    @Test
    void convenienceRunAppliesDefaultLimits(@TempDir Path projectDir) {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.alwaysReturn(FakeSandboxExecutor.success("ok"));
        SandboxService sandbox = new SandboxService(fake);

        SandboxResult result = sandbox.run(projectDir, List.of("mvn", "test"));

        assertEquals("ok", result.stdout());
        assertEquals(SandboxLimits.defaults(), fake.receivedRequests().get(0).limits());
    }

    @Test
    void customExecutionRequestIsPassedThroughUnchanged(@TempDir Path projectDir) {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.alwaysReturn(FakeSandboxExecutor.success("ok"));
        SandboxService sandbox = new SandboxService(fake);
        ExecutionRequest request = ExecutionRequest.builder(projectDir, List.of("mvn", "test"))
                .networkEnabled(true)
                .build();

        sandbox.run(request);

        assertEquals(request, fake.receivedRequests().get(0));
    }

    @Test
    void propagatesSandboxExceptionFromTheExecutor(@TempDir Path projectDir) {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        SandboxService sandbox = new SandboxService(fake);

        assertThrows(SandboxException.class,
                () -> sandbox.run(projectDir, List.of("mvn", "test")));
    }
}

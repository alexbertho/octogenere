package fr.octogenere.security.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FakeSandboxExecutorTest {

    @Test
    void returnsTheMatchingStub(@TempDir Path dir) {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.whenCalled(r -> r.command().contains("test"), FakeSandboxExecutor.success("tests ok"));
        fake.whenCalled(r -> r.command().contains("compile"), FakeSandboxExecutor.success("compiled"));

        SandboxResult testResult = fake.execute(ExecutionRequest.builder(dir, List.of("mvn", "test")).build());
        SandboxResult compileResult = fake.execute(ExecutionRequest.builder(dir, List.of("mvn", "compile")).build());

        assertEquals("tests ok", testResult.stdout());
        assertEquals("compiled", compileResult.stdout());
    }

    @Test
    void throwsWhenNothingIsStubbedForTheRequest(@TempDir Path dir) {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();

        assertThrows(SandboxException.class,
                () -> fake.execute(ExecutionRequest.builder(dir, List.of("mvn", "test")).build()));
    }

    @Test
    void recordsEveryReceivedRequest(@TempDir Path dir) {
        FakeSandboxExecutor fake = new FakeSandboxExecutor();
        fake.alwaysReturn(FakeSandboxExecutor.success("ok"));

        fake.execute(ExecutionRequest.builder(dir, List.of("mvn", "test")).build());
        fake.execute(ExecutionRequest.builder(dir, List.of("mvn", "compile")).build());

        assertEquals(2, fake.receivedRequests().size());
    }
}

package fr.octogenere.llm.google;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import fr.octogenere.llm.LlmException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiProviderTest {
    @Test
    void requestsJsonAndReturnsTrimmedText() {
        AtomicReference<String> receivedModel = new AtomicReference<>();
        AtomicReference<String> receivedPrompt = new AtomicReference<>();
        GeminiProvider provider = new GeminiProvider((model, prompt, config) -> {
            receivedModel.set(model);
            receivedPrompt.set(prompt);
            assertEquals("application/json", config.responseMimeType().orElseThrow());
            assertEquals(4096, config.maxOutputTokens().orElseThrow());
            return "  {\"ok\":true}  ";
        }, "test-model");

        assertEquals("{\"ok\":true}", provider.ask("Analyse ce projet"));
        assertEquals("test-model", receivedModel.get());
        assertEquals("Analyse ce projet", receivedPrompt.get());
        assertEquals("Gemini", provider.providerName());
    }

    @Test
    void translatesQuotaErrorsWithoutExposingProviderDetails() {
        GeminiProvider provider = new GeminiProvider((model, prompt, config) -> {
            throw new ClientException(429, "RESOURCE_EXHAUSTED", "private-provider-detail");
        }, "test-model");

        LlmException error = assertThrows(LlmException.class, () -> provider.ask("Bonjour"));

        assertTrue(error.getMessage().contains("HTTP 429"));
        assertFalse(error.getMessage().contains("private-provider-detail"));
    }

    @Test
    void translatesNetworkFailuresAndRejectsEmptyResponses() {
        GeminiProvider offline = new GeminiProvider((model, prompt, config) -> {
            throw new GenAiIOException("timeout");
        }, "test-model");
        GeminiProvider silent = new GeminiProvider((model, prompt, config) -> " ", "test-model");

        assertTrue(assertThrows(LlmException.class, () -> offline.ask("Bonjour"))
                .getMessage().contains("délai d'attente"));
        assertThrows(LlmException.class, () -> silent.ask("Bonjour"));
        assertThrows(LlmException.class, () -> silent.ask(" "));
    }
}

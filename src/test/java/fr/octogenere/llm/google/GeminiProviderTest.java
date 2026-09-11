package fr.octogenere.llm.google;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Model;
import fr.octogenere.llm.LlmException;
import org.junit.jupiter.api.Test;

import java.util.List;
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

        assertEquals("{\"ok\":true}", provider.ask("Analyse ce projet", "selected-model"));
        assertEquals("selected-model", receivedModel.get());
        assertEquals("Analyse ce projet", receivedPrompt.get());
        assertEquals("Gemini", provider.providerName());
    }

    @Test
    void listsOnlyModelsThatCanGenerateContent() {
        GeminiProvider provider = new GeminiProvider(
                (model, prompt, config) -> "{}",
                () -> List.of(
                        model("models/gemini-pro", "generateContent"),
                        model("models/text-embedding", "embedContent"),
                        model("gemini-flash", "generateContent"),
                        model("models/gemini-pro", "generateContent")),
                "fallback-model");

        assertEquals(List.of("gemini-flash", "gemini-pro"), provider.availableModels());
    }

    @Test
    void reportsModelListingFailuresWithoutExposingProviderDetails() {
        GeminiProvider provider = new GeminiProvider(
                (model, prompt, config) -> "{}",
                () -> {
                    throw new ClientException(403, "PERMISSION_DENIED", "private-provider-detail");
                },
                "fallback-model");

        LlmException error = assertThrows(LlmException.class, provider::availableModels);
        assertTrue(error.getMessage().contains("HTTP 403"));
        assertFalse(error.getMessage().contains("private-provider-detail"));
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

    private Model model(String name, String... actions) {
        return Model.builder()
                .name(name)
                .supportedActions(List.of(actions))
                .build();
    }
}

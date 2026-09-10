package fr.octogenere.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import fr.octogenere.llm.LlmException;
import fr.octogenere.llm.LlmProvider;

import fr.octogenere.llm.LlmException;
import fr.octogenere.llm.LlmProvider;
import fr.octogenere.llm.openai.OpenAiProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérifie le vrai SDK contre un serveur local, sans clé réelle ni appel à OpenAI. */
class OpenAiProviderTest {
    private HttpServer server;
    private OpenAiProvider provider;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile String receivedBody;
    private volatile String receivedMethod;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        provider = createProvider(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheQuestionAndReturnsAllTextParts() throws IOException {
        respond(200, response("completed", """
                [
                  {"type":"output_text","text":"Première partie.","annotations":[]},
                  {"type":"output_text","text":"Deuxième partie.","annotations":[]}
                ]
                """));

        LlmProvider llm = provider;
        String question = "Explique une interface \"Java\".\nEn français.";
        assertEquals("Première partie.\nDeuxième partie.", llm.ask(question));

        JsonNode request = new ObjectMapper().readTree(receivedBody);
        assertEquals("POST", receivedMethod);
        assertEquals(question, request.get("input").asText());
        assertEquals("test-model", request.get("model").asText());
        assertFalse(request.get("store").asBoolean());
        assertEquals(1024, request.get("max_output_tokens").asInt());
        assertEquals(1, requestCount.get());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " \t\n"})
    void rejectsEmptyQuestionsWithoutSendingARequest(String question) {
        assertThrows(LlmException.class, () -> provider.ask(question));
        assertEquals(0, requestCount.get());
    }

    @Test
    void rejectsAMissingApiKey() {
        assertThrows(LlmException.class, () -> new OpenAiProvider((String) null, "test-model"));
        assertThrows(LlmException.class, () -> new OpenAiProvider(" ", "test-model"));
    }

    @Test
    void rejectsAMissingModel() {
        assertThrows(LlmException.class, () -> new OpenAiProvider("test-key", " "));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void translatesHttpErrorsWithoutExposingTheirBodyOrRetrying(int status) {
        respond(status, """
                {"error":{"message":"private-server-detail","type":"test_error"}}
                """);

        LlmException error = assertThrows(LlmException.class, () -> provider.ask("Bonjour"));

        assertTrue(error.getMessage().contains("HTTP " + status));
        assertFalse(error.getMessage().contains("private-server-detail"));
        assertNotNull(error.getCause());
        assertEquals(1, requestCount.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "null"})
    void rejectsMalformedResponses(String body) {
        respond(200, body);
        assertThrows(LlmException.class, () -> provider.ask("Bonjour"));
    }

    @Test
    void rejectsAResponseWithoutText() {
        respond(200, response("completed", "[]"));
        assertThrows(LlmException.class, () -> provider.ask("Bonjour"));
    }

    @Test
    void reportsARefusal() {
        respond(200, response("completed", """
                [{"type":"refusal","refusal":"Refus simulé."}]
                """));

        LlmException error = assertThrows(LlmException.class, () -> provider.ask("Bonjour"));
        assertTrue(error.getMessage().contains("refusé"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"incomplete", "failed", "in_progress"})
    void doesNotReturnPartialTextAsASuccess(String status) {
        respond(200, response(status, """
                [{"type":"output_text","text":"Texte partiel.","annotations":[]}]
                """));

        assertThrows(LlmException.class, () -> provider.ask("Bonjour"));
    }

    @Test
    void rejectsAnErrorEvenWhenTheStatusSaysCompleted() {
        String body = response("completed", "[]").replace(
                "\"error\":null",
                "\"error\":{\"code\":\"server_error\",\"message\":\"Erreur simulée.\"}");
        respond(200, body);

        LlmException error = assertThrows(LlmException.class, () -> provider.ask("Bonjour"));
        assertTrue(error.getMessage().contains("échec de génération"));
    }

    @Test
    void translatesANetworkTimeout() {
        server.createContext("/v1/responses", exchange -> {
            // Le serveur accepte la requête mais ne répond pas avant le délai du client.
            try {
                Thread.sleep(500);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        provider.close();
        provider = createProvider(Duration.ofMillis(100));

        LlmException error = assertThrows(LlmException.class, () -> provider.ask("Bonjour"));
        assertTrue(error.getMessage().contains("délai d'attente"));
    }

    private OpenAiProvider createProvider(Duration timeout) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .timeout(timeout)
                .maxRetries(0)
                .build();
        return new OpenAiProvider(client, "test-model");
    }

    private void respond(int status, String body) {
        server.createContext("/v1/responses", exchange -> {
            requestCount.incrementAndGet();
            receivedMethod = exchange.getRequestMethod();
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (exchange) {
                exchange.getResponseBody().write(bytes);
            }
        });
    }

    private String response(String status, String content) {
        return """
                {
                  "id":"resp_test",
                  "object":"response",
                  "created_at":1,
                  "status":"%s",
                  "error":null,
                  "model":"test-model",
                  "output":[{
                    "id":"msg_test",
                    "type":"message",
                    "role":"assistant",
                    "status":"completed",
                    "content":%s
                  }]
                }
                """.formatted(status, content);
    }
}

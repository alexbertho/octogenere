package fr.octogenere.llm.google;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.ListModelsConfig;
import com.google.genai.types.Model;
import fr.octogenere.llm.LlmException;
import fr.octogenere.llm.LlmProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Adapte le SDK Google Gen AI au contrat commun LlmProvider. */
public final class GeminiProvider implements LlmProvider {
    private static final int TIMEOUT_MILLISECONDS = 60_000;

    private final String model;
    private final ContentGenerator generator;
    private final ModelLister modelLister;
    private final Runnable closeAction;

    public GeminiProvider(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("La clé GOOGLE_API_KEY est absente. Renseigne-la dans le fichier .env.");
        }
        if (model == null || model.isBlank()) {
            throw new LlmException("Le modèle GOOGLE_MODEL doit être renseigné.");
        }
        this.model = model.trim();
        Client client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout(TIMEOUT_MILLISECONDS).build())
                .build();
        this.generator = (requestedModel, prompt, config) -> {
            GenerateContentResponse response =
                    client.models.generateContent(requestedModel, prompt, config);
            if (response == null) {
                return null;
            }
            response.checkFinishReason();
            return response.text();
        };
        this.modelLister = () -> client.models.list(
                ListModelsConfig.builder().pageSize(1000).build());
        this.closeAction = client::close;
    }

    GeminiProvider(ContentGenerator generator, String model) {
        this(generator, List::of, model);
    }

    GeminiProvider(ContentGenerator generator, ModelLister modelLister, String model) {
        this.model = requireModel(model);
        this.generator = Objects.requireNonNull(generator);
        this.modelLister = Objects.requireNonNull(modelLister);
        this.closeAction = () -> { };
    }

    @Override
    public String ask(String prompt) {
        return ask(prompt, model);
    }

    @Override
    public String ask(String prompt, String selectedModel) {
        if (prompt == null || prompt.isBlank()) {
            throw new LlmException("La demande envoyée à Gemini ne doit pas être vide.");
        }
        String requestedModel = requireModel(selectedModel);
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .temperature(0.2f)
                .maxOutputTokens(4096)
                .build();

        String output;
        try {
            output = generator.generate(requestedModel, prompt, config);
        } catch (ApiException error) {
            throw translateApiError(error);
        } catch (GenAiIOException error) {
            throw new LlmException("Impossible de joindre Gemini ou délai d'attente dépassé.", error);
        } catch (RuntimeException error) {
            throw new LlmException("Gemini n'a pas pu terminer la génération.", error);
        }
        if (output == null || output.isBlank()) {
            throw new LlmException("Gemini n'a renvoyé aucune réponse textuelle exploitable.");
        }
        return output.trim();
    }

    @Override
    public List<String> availableModels() {
        try {
            List<String> models = new ArrayList<>();
            for (Model availableModel : modelLister.list()) {
                boolean supportsGeneration = availableModel.supportedActions()
                        .orElse(List.of())
                        .contains("generateContent");
                availableModel.name()
                        .filter(name -> supportsGeneration)
                        .map(GeminiProvider::normalizeModelName)
                        .filter(name -> !name.isBlank())
                        .ifPresent(models::add);
            }
            return models.stream()
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (ApiException error) {
            throw translateApiError(error);
        } catch (GenAiIOException error) {
            throw new LlmException("Impossible de charger la liste des modèles Gemini.", error);
        } catch (RuntimeException error) {
            throw new LlmException("Gemini n'a pas pu fournir la liste des modèles.", error);
        }
    }

    private static String normalizeModelName(String name) {
        String normalized = name.trim();
        return normalized.startsWith("models/") ? normalized.substring("models/".length()) : normalized;
    }

    private LlmException translateApiError(ApiException error) {
        int code = error.code();
        if (code == 401 || code == 403) {
            return new LlmException("Gemini a refusé la clé API (HTTP " + code + ").", error);
        }
        if (code == 404) {
            return new LlmException("Le modèle Gemini configuré est introuvable ou inaccessible (HTTP 404).", error);
        }
        if (code == 429) {
            return new LlmException("Limite de requêtes ou quota Gemini atteint (HTTP 429).", error);
        }
        if (code >= 500) {
            return new LlmException("Le service Gemini est temporairement indisponible (HTTP " + code + ").", error);
        }
        return new LlmException("L'appel Gemini a échoué (HTTP " + code + ").", error);
    }

    private static String requireModel(String model) {
        if (model == null || model.isBlank()) {
            throw new LlmException("Le modèle GOOGLE_MODEL doit être renseigné.");
        }
        return model.trim();
    }

    @Override
    public String providerName() {
        return "Gemini";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public void close() {
        closeAction.run();
    }

    @FunctionalInterface
    interface ContentGenerator {
        String generate(String model, String prompt, GenerateContentConfig config);
    }

    @FunctionalInterface
    interface ModelLister {
        Iterable<Model> list();
    }
}

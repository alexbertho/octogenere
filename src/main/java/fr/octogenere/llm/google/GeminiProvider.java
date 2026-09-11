package fr.octogenere.llm.google;

import fr.octogenere.llm.LlmProvider;
import fr.octogenere.llm.LlmException;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

public class GeminiProvider extends LlmProvider {
    private final Client client;

    public GeminiProvider(String apiKey, String apiModel) {
        super(apiKey, apiModel);

        if (getApiKey() == null || getApiKey().isEmpty()) {
            throw new LlmException("Environment variable problem : GOOGLE_API_KEY (missing or null)");
        }
        if (getApiModel() == null || getApiModel().isEmpty()) {
            throw new LlmException("Environment variable problem : GOOGLE_API_MODEL (missing or null)");
        }

        this.client = Client.builder().apiKey(getApiKey()).build();
    }

    @Override
    public String ask(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            throw new LlmException("Prompt missing");
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .build();

        GenerateContentResponse reponse =
                client.models.generateContent(getApiModel(), getSCHEMA() + "\n\n" + prompt, config);

        String output = reponse.text();
        if (output == null || output.isEmpty()) {
            throw new LlmException("LLM Gemini don't speak");
        }
        return output;
    }
}
package fr.octogenere.llm.google;

import fr.octogenere.llm.LlmProvider;
import fr.octogenere.llm.LlmException;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

public class GeminiProvider implements LlmProvider {
    private String apiKey;
    private String model;

    private final Client client;

    private static final String SCHEMA = """
            Réponse demandée sous un format précis JSON à respecter absolument (pas de bonus ou de texte explicatif) :
            {
                "projectName": "string",
                "analysisDate": "YYYY-MM-DD",
                "configuration": "string",
                "modelName": "string",
                "overallSummary": "string",
                "criteria": [
                    {
                    "criterion": "string",
                    "score": 0,
                    "maxScore": 10,
                    "summary": "string",
                    "strengths": ["string"],
                    "weaknesses": ["string"],
                    "issues": ["string"],
                    "recommendations": ["string"]
                    }
                ]
            }
            """;

    public GeminiProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;

        if (apiKey == null || apiKey.isEmpty()) {
            throw new LlmException("Problème au niveau de la variable environnement GOOGLE_API_KEY (manquant ou null)");
        }
        if (model == null || model.isEmpty()) {
            throw new LlmException("Problème au niveau de la variable environnement GOOGLE_MODEL (manquant ou null)");
        }

        this.client = Client.builder().apiKey(apiKey).build();
    }

    @Override
    public String ask(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            throw new LlmException("Il ne faut pas oublier le prompt");
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .build();

        GenerateContentResponse reponse =
                client.models.generateContent(model, SCHEMA + "\n\n" + prompt, config);

        String output = reponse.text();
        if (output == null || output.isEmpty()) {
            throw new LlmException("LLM Gemini n'a pas été bavard");
        }
        return output;
    }
}
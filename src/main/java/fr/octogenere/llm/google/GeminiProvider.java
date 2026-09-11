package fr.octogenere.llm.google;

import fr.octogenere.llm.LlmProvider;
import fr.octogenere.llm.LlmException;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import io.github.cdimascio.dotenv.Dotenv;

public class GeminiProvider implements LlmProvider {
    private final Client client;
    private final String model;

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

    public GeminiProvider() {
        
        //Récupérer le fichier environnement configuré à la racine avec la clé API et le model
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String apiKey = dotenv.get("GOOGLE_API_KEY");
        this.model = dotenv.get("GOOGLE_MODEL");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new LlmException("Fichier .env manquant ou variable GOOGLE_API_KEY null");
        }
        if (model == null || model.isEmpty()) {
            throw new LlmException("Fichier .env manquant ou variable GOOGLE_MODEL null");
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
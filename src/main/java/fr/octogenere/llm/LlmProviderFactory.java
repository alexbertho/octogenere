package fr.octogenere.llm;

import fr.octogenere.llm.google.GeminiProvider;
import fr.octogenere.llm.openai.OpenAiProvider;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Locale;
import java.util.Objects;

/** Crée le provider choisi dans .env sans exposer sa configuration à l'UI. */
public final class LlmProviderFactory {
    private LlmProviderFactory() {
    }

    public static LlmProvider from(Dotenv dotenv) {
        Objects.requireNonNull(dotenv, "La configuration d'environnement doit être présente.");
        String configured = dotenv.get("LLM_PROVIDER", "gemini");
        String provider = configured == null ? "gemini" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "gemini", "google" -> new GeminiProvider(
                    dotenv.get("GOOGLE_API_KEY"), dotenv.get("GOOGLE_MODEL"));
            case "openai" -> new OpenAiProvider(
                    dotenv.get("OPENAI_API_KEY"), dotenv.get("OPENAI_MODEL", "gpt-4.1-mini"));
            default -> throw new LlmException(
                    "LLM_PROVIDER doit valoir gemini ou openai, et non " + configured + ".");
        };
    }
}

package fr.octogenere;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;

import fr.octogenere.llm.LlmException;
import fr.octogenere.llm.LlmProvider;
import fr.octogenere.llm.google.GeminiProvider;


import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.octogenere.prompt.PromptBuilder;

/** Point d'entrée de la première démonstration en console. */
public class Main {
    public static void main(String[] args) {

        String folderPath = ".";
        if (args.length > 0) {
            folderPath = String.join(" ", args);
        }

        String apiKey = null;
        String model = null;
        String configFile = null;
        try {
            // Java ne lit pas les fichiers .env automatiquement.
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            apiKey = dotenv.get("GOOGLE_API_KEY");
            model = dotenv.get("GOOGLE_MODEL");
            configFile = dotenv.get("CRITERIA_FILE");
        }  catch (DotenvException error) {
            System.err.println("Impossible to read .env file (it is very important)");
            System.exit(1);
        }

        JsonObject criteria = null;
        try (FileReader reader = new FileReader(configFile)) {
            criteria = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (FileNotFoundException e) {
            System.err.println(configFile + " not found");
        } catch (IOException e) {
            System.err.println(configFile + " cant be read");
        }

        String prompt = null;
        try {
            prompt = PromptBuilder.BuildReviewPrompt(folderPath, criteria);
            System.out.println("Prompt generated : \n" + prompt);
        } catch (Exception e) {
            System.err.println("Fatal error. Unable to generate review prompt : " + e.getMessage());
            e.printStackTrace();
        }

        try {
            LlmProvider provider = new GeminiProvider(apiKey, model);

            String json = provider.ask(prompt);

            System.out.println("Réponse test :" + json);

        } catch (LlmException e) {
            System.err.println("Erreur côté IA : " + e.getMessage());
        }

    }
}

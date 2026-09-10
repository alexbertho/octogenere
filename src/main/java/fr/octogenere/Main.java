package fr.octogenere;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.octogenere.prompt.PromptBuilder;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;

/** Point d'entrée de la première démonstration en console. */
public class Main {
    public static void main(String[] args) {
        String folderPath = ".";
        if (args.length > 0) {
            folderPath = String.join(" ", args);
        }

        String apiKey = null;
        String configFile = null;
        try {
            // Java ne lit pas les fichiers .env automatiquement.
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            apiKey = dotenv.get("OPENAI_API_KEY");
            configFile = dotenv.get("CRITERIA_CFG");
        }  catch (DotenvException error) {
            System.err.println("Impossible de lire le fichier .env. Vérifie sa syntaxe.");
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

        try {
            String prompt = PromptBuilder.BuildReviewPrompt(folderPath, criteria);
            System.out.println("Prompt generated : \n" + prompt);
        } catch (Exception e) {
            System.err.println("Fatal error. Unable to generate review prompt : " + e.getMessage());
            e.printStackTrace();
        }

        /*
        try {
            String apiKey = dotenv.get("OPENAI_API_KEY");
            String model = dotenv.get("OPENAI_MODEL", "gpt-4.1-mini");

            String prompt = "Explique en une phrase à quoi sert une interface en Java.";
            if (args.length > 0) {
                prompt = String.join(" ", args);
            }

            // Le client réseau sera fermé à la sortie du bloc, même en cas d'erreur.
            try (OpenAiProvider provider = new OpenAiProvider(apiKey, model)) {
                String answer = provider.ask(prompt);
                System.out.println(answer);
            }
        } catch (LlmException error) {
            System.err.println("Erreur : " + error.getMessage());
            System.exit(1);
        }
        */

    }
}

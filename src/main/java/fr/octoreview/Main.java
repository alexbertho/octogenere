package fr.octoreview;

import fr.octoreview.llm.LlmException;
import fr.octoreview.llm.openai.OpenAiProvider;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;

/** Point d'entrée de la première démonstration en console. */
public class Main {
    public static void main(String[] args) {
        try {
            // Java ne lit pas les fichiers .env automatiquement.
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
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
        } catch (DotenvException error) {
            System.err.println("Impossible de lire le fichier .env. Vérifie sa syntaxe.");
            System.exit(1);
        } catch (LlmException error) {
            System.err.println("Erreur : " + error.getMessage());
            System.exit(1);
        }
    }
}

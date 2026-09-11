package fr.octogenere;

import fr.octogenere.llm.LlmException;
import fr.octogenere.llm.LlmProvider;
import fr.octogenere.llm.LlmProviderFactory;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;

/** Point d'entrée console pour envoyer une demande au fournisseur configuré. */
public class Main {
    public static void main(String[] args) {
        try {
            // Java ne lit pas les fichiers .env automatiquement.
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            String prompt = "Explique en une phrase à quoi sert une interface en Java.";
            if (args.length > 0) {
                prompt = String.join(" ", args);
            }

            // Le provider choisi par LLM_PROVIDER sera fermé même en cas d'erreur.
            try (LlmProvider provider = LlmProviderFactory.from(dotenv)) {
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

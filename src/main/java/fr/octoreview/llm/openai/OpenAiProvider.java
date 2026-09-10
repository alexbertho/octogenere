package fr.octoreview.llm.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseStatus;
import fr.octoreview.llm.LlmException;
import fr.octoreview.llm.LlmProvider;

import java.time.Duration;

/** Adapte le SDK OpenAI au contrat commun LlmProvider (pattern Adapter). */
public class OpenAiProvider implements LlmProvider, AutoCloseable {
    private final OpenAIClient client;
    private final String model;

    public OpenAiProvider(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("La clé OPENAI_API_KEY est absente. Renseigne-la dans le fichier .env.");
        }
        if (model == null || model.isBlank()) {
            throw new LlmException("Le modèle OPENAI_MODEL doit être renseigné.");
        }

        this.model = model;
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0) // Une seule tentative pour ce premier échange.
                .build();
    }

    // Les tests fournissent un client relié à un serveur simulé local.
    OpenAiProvider(OpenAIClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String ask(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new LlmException("La question ne doit pas être vide.");
        }

        ResponseCreateParams request = ResponseCreateParams.builder()
                .model(model)
                .input(prompt)
                .maxOutputTokens(1024)
                .store(false)
                .build();

        try {
            Response response = client.responses().create(request);
            if (response == null) {
                throw new LlmException("OpenAI n'a renvoyé aucune réponse exploitable.");
            }
            if (response.error().isPresent()) {
                throw new LlmException("OpenAI a signalé un échec de génération.");
            }
            if (response.status().isEmpty() || !response.status().get().equals(ResponseStatus.COMPLETED)) {
                throw new LlmException("OpenAI n'a pas renvoyé de réponse complète.");
            }
            return extractText(response);
        } catch (OpenAIServiceException error) {
            // Ne pas afficher le corps de l'erreur API : il peut contenir des données sensibles.
            if (error.statusCode() == 401) {
                throw new LlmException("OpenAI a refusé la clé API (HTTP 401).", error);
            }
            if (error.statusCode() == 429) {
                throw new LlmException("Limite de requêtes ou quota OpenAI atteint (HTTP 429).", error);
            }
            throw new LlmException("L'appel OpenAI a échoué (HTTP " + error.statusCode() + ").", error);
        } catch (OpenAIIoException error) {
            throw new LlmException("Impossible de joindre OpenAI ou délai d'attente dépassé.", error);
        } catch (OpenAIException error) {
            throw new LlmException("La réponse reçue d'OpenAI est invalide ou illisible.", error);
        }
    }

    private String extractText(Response response) {
        StringBuilder text = new StringBuilder();

        // Une réponse peut contenir plusieurs éléments, pas uniquement du texte.
        for (ResponseOutputItem item : response.output()) {
            if (item.isMessage()) {
                for (ResponseOutputMessage.Content content : item.asMessage().content()) {
                    if (content.isRefusal()) {
                        throw new LlmException("Le modèle a refusé de répondre à cette demande.");
                    }
                    if (content.isOutputText()) {
                        if (!text.isEmpty()) {
                            text.append('\n');
                        }
                        text.append(content.asOutputText().text());
                    }
                }
            }
        }

        String answer = text.toString().trim();
        if (answer.isEmpty()) {
            throw new LlmException("OpenAI n'a renvoyé aucune réponse textuelle.");
        }
        return answer;
    }

    @Override
    public void close() {
        client.close();
    }
}

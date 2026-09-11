package fr.octogenere.llm;

/** Contrat commun pour envoyer une demande textuelle à un modèle de langage. */
public interface LlmProvider extends AutoCloseable {
    /**
     * Envoie une demande et renvoie la réponse textuelle complète.
     *
     * @param prompt demande non nulle et non vide
     * @return réponse non vide du modèle
     * @throws LlmException si la demande est invalide ou si aucune réponse exploitable n'est obtenue
     */
    String ask(String prompt);

    /** Nom affichable du fournisseur, sans information sensible. */
    default String providerName() {
        return "LLM";
    }

    /** Nom du modèle utilisé dans le rapport. */
    default String modelName() {
        return "modèle configuré";
    }

    @Override
    default void close() {
        // La plupart des faux fournisseurs et certains SDK n'ont rien à fermer.
    }
}

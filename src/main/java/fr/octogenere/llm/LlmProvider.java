package fr.octogenere.llm;

/** Contrat commun pour envoyer une demande textuelle à un modèle de langage. */
public interface LlmProvider {
    /**
     * Envoie une demande et renvoie la réponse textuelle complète.
     *
     * @param prompt demande non nulle et non vide
     * @return réponse non vide du modèle
     * @throws LlmException si la demande est invalide ou si aucune réponse exploitable n'est obtenue
     */
    String ask(String prompt);
}

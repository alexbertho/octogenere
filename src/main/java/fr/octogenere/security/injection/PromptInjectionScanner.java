package fr.octogenere.security.injection;

/**
 * Recherche des tentatives d'injection de prompt dans un contenu (pattern
 * Strategy) : ex. un commentaire de code demandant au modèle d'ignorer ses
 * instructions précédentes. {@link HeuristicPromptInjectionScanner} est
 * l'implémentation actuelle ; d'autres approches (basées sur un LLM, ou sur
 * une liste de règles externalisée) pourront être ajoutées sans toucher aux
 * appelants.
 */
public interface PromptInjectionScanner {
    ScanResult scan(String content);
}

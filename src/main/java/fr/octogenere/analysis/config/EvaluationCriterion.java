package fr.octogenere.analysis.config;

/** Définition fiable d'un critère proposé dans l'interface et envoyé au LLM. */
public record EvaluationCriterion(
        String id,
        String label,
        boolean enabled,
        double weight,
        int maxScore) {

    public EvaluationCriterion {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("L'identifiant du critère est invalide.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Le libellé du critère ne doit pas être vide.");
        }
        if (!Double.isFinite(weight) || weight <= 0 || weight > 1) {
            throw new IllegalArgumentException("Le poids du critère doit être compris entre 0 et 1.");
        }
        if (maxScore <= 0) {
            throw new IllegalArgumentException("Le score maximal du critère doit être positif.");
        }
        label = label.trim();
    }
}

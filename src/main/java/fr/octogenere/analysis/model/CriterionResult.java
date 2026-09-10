package fr.octogenere.analysis.model;

import java.util.ArrayList;
import java.util.List;

/** Résultat validé de l'évaluation d'un critère. */
public record CriterionResult(
        String criterion,
        int score,
        int maxScore,
        String summary,
        List<String> strengths,
        List<String> weaknesses,
        List<String> issues,
        List<String> recommendations) {

    public CriterionResult {
        criterion = requireText(criterion, "Le nom du critère");
        summary = requireText(summary, "La synthèse");

        if (maxScore <= 0) {
            throw new IllegalArgumentException("Le score maximal doit être strictement positif.");
        }
        if (score < 0 || score > maxScore) {
            throw new IllegalArgumentException("Le score doit être compris entre 0 et le score maximal.");
        }

        strengths = copyTextList(strengths, "Les points forts");
        weaknesses = copyTextList(weaknesses, "Les faiblesses");
        issues = copyTextList(issues, "Les problèmes");
        recommendations = copyTextList(recommendations, "Les recommandations");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " ne doit pas être vide.");
        }
        return value.trim();
    }

    private static List<String> copyTextList(List<String> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " ne doivent pas être absents.");
        }

        List<String> copy = new ArrayList<>();
        for (String value : values) {
            copy.add(requireText(value, "Un élément de " + fieldName.toLowerCase()));
        }
        return List.copyOf(copy);
    }
}

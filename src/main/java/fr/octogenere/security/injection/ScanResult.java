package fr.octogenere.security.injection;

import java.util.List;

/** Résultat de l'analyse d'un contenu à la recherche de tentatives d'injection de prompt. */
public record ScanResult(RiskLevel riskLevel, List<Finding> findings) {
    public ScanResult {
        findings = List.copyOf(findings);
    }

    public boolean suspicious() {
        return riskLevel != RiskLevel.NONE;
    }

    public static ScanResult clean() {
        return new ScanResult(RiskLevel.NONE, List.of());
    }

    /** Une occurrence précise d'une règle déclenchée dans le contenu analysé. */
    public record Finding(String ruleId, String description, int offset, String matchedSnippet) {
    }
}

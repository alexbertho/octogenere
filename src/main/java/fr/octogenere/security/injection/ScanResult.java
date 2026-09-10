package fr.octogenere.security.injection;

import java.util.List;

/** Result of scanning a piece of content for prompt injection attempts. */
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

    /** One specific occurrence of a rule that fired in the scanned content. */
    public record Finding(String ruleId, String description, int offset, String matchedSnippet) {
    }
}

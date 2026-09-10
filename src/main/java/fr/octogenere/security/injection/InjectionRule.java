package fr.octogenere.security.injection;

import java.util.regex.Pattern;

/** A heuristic rule: a pattern to look for and the severity if it's found. */
public record InjectionRule(String id, String description, Pattern pattern, RiskLevel severity) {
}

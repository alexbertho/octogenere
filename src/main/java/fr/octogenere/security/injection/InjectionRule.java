package fr.octogenere.security.injection;

import java.util.regex.Pattern;

/** Une règle heuristique : un motif à rechercher et la gravité si il est trouvé. */
public record InjectionRule(String id, String description, Pattern pattern, RiskLevel severity) {
}

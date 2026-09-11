package fr.octogenere.analysis.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Charge la configuration modulaire des critères depuis les ressources. */
public final class CriterionCatalog {
    private static final String DEFAULT_RESOURCE = "/analysis/criteria.json";
    private static final Set<String> ALLOWED_FIELDS =
            Set.of("label", "enabled", "weight", "maxScore");

    private final List<EvaluationCriterion> criteria;
    private final Map<String, EvaluationCriterion> byLabel;

    public CriterionCatalog(List<EvaluationCriterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException("Le catalogue doit contenir au moins un critère.");
        }
        this.criteria = List.copyOf(criteria);
        Map<String, EvaluationCriterion> index = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        for (EvaluationCriterion criterion : this.criteria) {
            if (!ids.add(criterion.id()) || index.put(criterion.label(), criterion) != null) {
                throw new IllegalArgumentException("Chaque critère doit avoir un identifiant et un libellé uniques.");
            }
        }
        byLabel = Map.copyOf(index);
    }

    public static CriterionCatalog loadDefault() {
        try (InputStream input = CriterionCatalog.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("La configuration des critères est introuvable.");
            }
            JsonNode root = new ObjectMapper().readTree(input);
            if (root == null || !root.isObject() || root.isEmpty()) {
                throw new IllegalStateException("La configuration des critères doit être un objet JSON non vide.");
            }

            List<EvaluationCriterion> definitions = new ArrayList<>();
            root.fields().forEachRemaining(entry -> definitions.add(parse(entry.getKey(), entry.getValue())));
            return new CriterionCatalog(definitions);
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalStateException("La configuration des critères est invalide.", error);
        }
    }

    private static EvaluationCriterion parse(String id, JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Le critère " + id + " doit être un objet JSON.");
        }
        node.fieldNames().forEachRemaining(field -> {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Propriété inconnue pour " + id + " : " + field);
            }
        });
        JsonNode label = required(node, "label", id);
        JsonNode enabled = required(node, "enabled", id);
        JsonNode weight = required(node, "weight", id);
        JsonNode maxScore = required(node, "maxScore", id);
        if (!label.isTextual() || !enabled.isBoolean() || !weight.isNumber() || !maxScore.isIntegralNumber()) {
            throw new IllegalArgumentException("Les propriétés du critère " + id + " ont un type invalide.");
        }
        return new EvaluationCriterion(id, label.textValue(), enabled.booleanValue(),
                weight.doubleValue(), maxScore.intValue());
    }

    private static JsonNode required(JsonNode node, String field, String id) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("La propriété " + field + " manque pour le critère " + id + ".");
        }
        return value;
    }

    public List<EvaluationCriterion> criteria() {
        return criteria;
    }

    public List<EvaluationCriterion> resolveLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("Sélectionne au moins un critère.");
        }
        List<EvaluationCriterion> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("Un critère sélectionné ne doit pas être vide.");
            }
            EvaluationCriterion criterion = byLabel.get(label.trim());
            if (criterion == null) {
                throw new IllegalArgumentException("Critère inconnu : " + label.trim());
            }
            if (!seen.add(criterion.id())) {
                throw new IllegalArgumentException("Le critère " + criterion.label() + " est sélectionné plusieurs fois.");
            }
            selected.add(criterion);
        }
        return List.copyOf(selected);
    }
}

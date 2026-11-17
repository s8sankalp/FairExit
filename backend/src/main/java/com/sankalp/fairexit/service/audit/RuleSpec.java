package com.sankalp.fairexit.service.audit;

import java.util.List;
import java.util.Map;

/**
 * A rule's data, free of JPA, so the engine and its tests do not need a database.
 * Built from a RuleDefinition row.
 */
public record RuleSpec(String ruleId, int version, String ruleType, int priority,
                       Map<String, Object> parameters, String reasonTemplate) {

    public RuleSpec {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public String ref() {
        return ruleId + "@" + version;
    }

    public int intParam(String name, int fallback) {
        Object value = parameters.get(name);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    public List<String> listParam(String name) {
        Object value = parameters.get(name);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> mapParam(String name) {
        Object value = parameters.get(name);
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    /** Fills {name} placeholders. Unknown placeholders are left as they are. */
    public String reason(Map<String, String> values) {
        String text = reasonTemplate;
        for (Map.Entry<String, String> e : values.entrySet()) {
            text = text.replace("{" + e.getKey() + "}", e.getValue());
        }
        return text;
    }
}

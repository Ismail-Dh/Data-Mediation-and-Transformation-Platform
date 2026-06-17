package com.miniESB.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "true")
public class EngineProcessService {

    private final ObjectMapper objectMapper;

    @Value("${engine.rules-file:/app/rules.json}")
    private String rulesFilePath;

    // ── Charger les règles depuis rules.json ──────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRules() throws Exception {
        return objectMapper.readValue(new File(rulesFilePath),
                new TypeReference<>() {});
    }

    // ── Process ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> process(String rawContent) throws Exception {
        Map<String, Object> rules  = loadRules();
        Map<String, Object> input  = objectMapper.readValue(rawContent,
                new TypeReference<>() {});

        // 1 — validation
        List<Map<String, Object>> validationFields =
                (List<Map<String, Object>>) rules.get("validationFields");
        validatePayload(input, validationFields);

        // 2 — mapping
        List<Map<String, Object>> mappingRules =
                (List<Map<String, Object>>) rules.get("mappingRules");
        return applyMapping(input, mappingRules);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validatePayload(Map<String, Object> input,
                                  List<Map<String, Object>> fields) {
        if (fields == null || fields.isEmpty()) return;

        List<String> errors = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            String path     = (String) field.get("fieldPath");
            boolean required = (Boolean) field.getOrDefault("required", false);
            if (required && !input.containsKey(path)) {
                errors.add("Missing required field: " + path);
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + errors);
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private Map<String, Object> applyMapping(Map<String, Object> input,
                                              List<Map<String, Object>> rules) {
        if (rules == null || rules.isEmpty()) return input;

        Map<String, Object> output = new LinkedHashMap<>(input);

        for (Map<String, Object> rule : rules) {
            String src  = (String) rule.get("sourceField");
            String tgt  = (String) rule.get("targetField");
            String type = (String) rule.get("mappingType");
            String expr = (String) rule.get("expression");

            switch (type.toUpperCase()) {
                case "FIELD_PLACEMENT" -> {
                    if (output.containsKey(src)) {
                        output.put(tgt, output.remove(src));
                    }
                }
                case "VALUE_TRANSFORM" -> {
                    if (output.containsKey(src) && expr != null) {
                        Object val = output.remove(src);
                        output.put(tgt, applyTransform(val, expr));
                    }
                }
                default -> log.warn("Unsupported mapping type: {}", type);
            }
        }
        return output;
    }

    private Object applyTransform(Object value, String expression) {
        String s = value.toString();
        return switch (expression.toUpperCase()) {
            case "UPPERCASE" -> s.toUpperCase();
            case "LOWERCASE" -> s.toLowerCase();
            case "TRIM"      -> s.trim();
            default          -> value;
        };
    }
}
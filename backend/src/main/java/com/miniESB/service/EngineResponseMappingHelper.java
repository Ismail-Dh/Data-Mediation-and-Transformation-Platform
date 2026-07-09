package com.miniESB.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Réplique en mémoire (sans JPA) de la logique T6 de ResponseMappingServiceImpl,
 * utilisée par EngineProcessService dans l'image Docker autonome.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "true")
public class EngineResponseMappingHelper {

    private final ObjectMapper objectMapper;

    public void applyResponseMapping(Long providerId, String rawBody,
                                     List<Map<String, Object>> rules,
                                     Map<String, Object> result) {
        Map<String, Object> sourceMap;
        try {
            sourceMap = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Cannot parse provider response as JSON: {}", e.getMessage());
            result.put("validationPassed", false);
            result.put("validationErrors", List.of("Response body is not valid JSON: " + e.getMessage()));
            return;
        }

        List<Map<String, Object>> applicableRules = rules.stream()
                .filter(r -> {
                    Object rProviderId = r.get("providerId");
                    return rProviderId == null
                            || Objects.equals(((Number) rProviderId).longValue(), providerId);
                }).toList();

        List<String> violations = new ArrayList<>();
        Map<String, Object> mappedBody = new LinkedHashMap<>();

        for (Map<String, Object> rule : applicableRules) {
            applyRule(rule, sourceMap, mappedBody, violations);
        }

        sourceMap.forEach((k, v) -> mappedBody.putIfAbsent(k, v));

        boolean passed = violations.isEmpty();
        result.put("validationPassed", passed);
        result.put("validationErrors", violations);
        result.put("mappedBody", mappedBody);
    }

    private void applyRule(Map<String, Object> rule,
                           Map<String, Object> source,
                           Map<String, Object> target,
                           List<String> violations) {
        String srcField  = (String) rule.get("sourceField");
        String tgtField  = (String) rule.get("targetField");
        String type      = (String) rule.get("mappingType");
        String expr      = (String) rule.get("expression");
        boolean required = Boolean.TRUE.equals(rule.get("required"));

        Object value = source.get(srcField);

        if (value == null) {
            if (required) {
                violations.add("Required field '" + srcField + "' is missing in provider response");
            }
            return;
        }

        Object transformed;
        try {
            transformed = switch (type == null ? "" : type.toUpperCase()) {
                case "FIELD_PLACEMENT"  -> value;
                case "FORMAT_CHANGE"    -> applyFormatChange(value, expr);
                case "VALUE_TRANSFORM"  -> applyValueTransform(expr, value);
                case "CALCULATED_FIELD" -> applyConcat(expr, source);
                case "RESTRUCTURING"    -> value;
                default -> value;
            };
        } catch (Exception e) {
            log.warn("Response rule failed for field '{}': {}", srcField, e.getMessage());
            violations.add("Transformation failed for field '" + srcField + "': " + e.getMessage());
            return;
        }

        target.put(tgtField, transformed);
    }

    private Object applyFormatChange(Object value, String expression) {
        if (expression == null || expression.isBlank()) return value;
        return switch (expression.trim().toUpperCase()) {
            case "UPPERCASE" -> value.toString().toUpperCase();
            case "LOWERCASE" -> value.toString().toLowerCase();
            case "TRIM"      -> value.toString().trim();
            default -> throw new IllegalArgumentException("Unknown FORMAT_CHANGE: " + expression);
        };
    }

    private Object applyValueTransform(String expression, Object value) {
        if (expression == null || expression.isBlank()) return value;
        if (expression.startsWith("CONSTANT:")) {
            return expression.substring("CONSTANT:".length());
        }
        return value;
    }

    private Object applyConcat(String expression, Map<String, Object> source) {
        if (expression == null || expression.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : expression.split("\\+")) {
            String token = part.trim().replace("'", "");
            sb.append(source.getOrDefault(token, token));
        }
        return sb.toString();
    }
}
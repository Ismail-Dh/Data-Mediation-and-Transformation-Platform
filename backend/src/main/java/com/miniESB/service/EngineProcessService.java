package com.miniESB.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.dispatch.DispatchOutcome;
import com.miniESB.engine.dispatch.HttpDispatchExecutor;
import com.miniESB.engine.mapping.MappingEngine;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.engine.validation.MapFieldResolver;
import com.miniESB.engine.validation.ValidationEngine;
import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.exception.FieldViolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Point d'entrée du "mode engine" : lit la configuration complète d'un pipeline
 * (validation, mapping, providers, mapping de réponse) depuis un fichier
 * {@code rules.json} embarqué dans l'image Docker générée, exécute le flux
 * complet (validation → mapping → dispatch providers → validation/mapping des
 * réponses), sans base de données.
 *
 * <p><strong>Avant refactoring</strong>, cette classe dupliquait intégralement
 * la logique de {@code MappingServiceImpl}, {@code BusinessValidatorService}
 * ET {@code ProviderDispatchServiceImpl} pour opérer sur des {@code Map} JSON
 * au lieu d'entités JPA — violation SRP/OCP/DRY majeure, quadruplée par
 * l'ajout du dispatch providers. Cette classe est désormais un simple
 * <em>Adapter</em> : elle convertit les données JSON du fichier au format
 * neutre attendu par les moteurs partagés ({@link MappingEngine},
 * {@link ValidationEngine}, {@link HttpDispatchExecutor}), qui portent
 * chacun la logique réellement partagée avec le mode base de données.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "true")
public class EngineProcessService {

    private final ObjectMapper objectMapper;
    private final MappingEngine mappingEngine;
    private final ValidationEngine validationEngine;
    private final HttpDispatchExecutor dispatchExecutor;
    private final EngineResponseMappingHelper responseMappingHelper;

    @Value("${engine.rules-file:/app/rules.json}")
    private String rulesFilePath;

    @SuppressWarnings("unchecked")
    public Map<String, Object> process(String rawContent) throws Exception {
        Map<String, Object> rules = loadRules();
        Map<String, Object> input = objectMapper.readValue(rawContent,
                new TypeReference<LinkedHashMap<String, Object>>() {});

        validatePayload(input, rules);

        List<MappingRuleData> mappingRules = extractMappingRules(rules);
        Map<String, Object> mapped = mappingEngine.apply(mappingRules, input);

        List<Map<String, Object>> providers =
                (List<Map<String, Object>>) rules.getOrDefault("providers", List.of());
        String outputFormatStr = (String) rules.getOrDefault("outputFormat", "JSON");
        DataFormat outputFormat = parseDataFormat(outputFormatStr);
        List<Map<String, Object>> responseMappingRules =
                (List<Map<String, Object>>) rules.getOrDefault("responseMappingRules", List.of());

        List<Map<String, Object>> dispatchResults =
                dispatchToProviders(mapped, providers, outputFormat, responseMappingRules);
        boolean anySuccess = dispatchResults.stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.get("success")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mappedPayload", mapped);
        result.put("providerResults", dispatchResults);
        result.put("overallSuccess", anySuccess);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRules() throws Exception {
        return objectMapper.readValue(new File(rulesFilePath), new TypeReference<>() {});
    }

    // ── Dispatch providers — délègue l'appel HTTP à HttpDispatchExecutor ────

    private List<Map<String, Object>> dispatchToProviders(Map<String, Object> mappedPayload,
                                                           List<Map<String, Object>> providers,
                                                           DataFormat outputFormat,
                                                           List<Map<String, Object>> responseMappingRules) {
        if (providers == null || providers.isEmpty()) {
            log.warn("No providers configured in rules.json — dispatch skipped");
            return List.of();
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(mappedPayload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize mapped payload: " + e.getMessage(), e);
        }

        return providers.stream()
                .map(provider -> callProvider(provider, body, outputFormat, responseMappingRules))
                .toList();
    }

    private Map<String, Object> callProvider(Map<String, Object> provider, String body,
                                              DataFormat outputFormat,
                                              List<Map<String, Object>> responseMappingRules) {
        String name = (String) provider.get("name");
        Number providerIdN = (Number) provider.get("id");
        Long providerId = providerIdN != null ? providerIdN.longValue() : null;
        String endpoint = (String) provider.get("endpoint");
        Number timeoutN = (Number) provider.get("timeout");
        int timeoutSec = timeoutN != null ? timeoutN.intValue() : 30;

        // Méthode HTTP choisie pour ce provider (exportée dans rules.json par
        // DockerBuildArtifactGenerator depuis PipelineProvider.httpMethod).
        // Fallback défensif sur POST si absente (rules.json généré avant ce changement).
        String httpMethodStr = (String) provider.getOrDefault("httpMethod", "POST");
        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(httpMethodStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Provider '{}' — httpMethod inconnu '{}' dans rules.json, fallback POST", name, httpMethodStr);
            httpMethod = HttpMethod.POST;
        }

        DispatchOutcome outcome = dispatchExecutor.call(endpoint, httpMethod, body, outputFormat, timeoutSec, name);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerName", name);
        result.put("endpoint", endpoint);
        result.put("httpStatus", outcome.httpStatus());
        result.put("success", outcome.success());
        result.put("durationMs", outcome.durationMs());
        if (outcome.errorMessage() != null) {
            result.put("errorMessage", outcome.errorMessage());
        } else {
            result.put("rawBody", outcome.rawBody());
        }

        if (outcome.success()) {
            responseMappingHelper.applyResponseMapping(providerId, outcome.rawBody(), responseMappingRules, result);
        }

        return result;
    }

    private DataFormat parseDataFormat(String raw) {
        try {
            return DataFormat.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return DataFormat.JSON;
        }
    }

    // ── Validation (niveau 1 structurel + niveau 2 métier) ──────────────────

    @SuppressWarnings("unchecked")
    private void validatePayload(Map<String, Object> input, Map<String, Object> rules) {
        List<Map<String, Object>> validationFields =
                (List<Map<String, Object>>) rules.getOrDefault("validationFields", List.of());
        List<Map<String, Object>> validationRules =
                (List<Map<String, Object>>) rules.getOrDefault("validationRules", List.of());

        // Niveau 1 — structurel (présence / nullabilité des champs du schéma pipeline)
        List<String> structuralErrors = new java.util.ArrayList<>();
        for (Map<String, Object> field : validationFields) {
            String path = (String) field.get("fieldPath");
            boolean required = (Boolean) field.getOrDefault("required", false);
            boolean nullable = (Boolean) field.getOrDefault("nullable", true);

            if (required && !input.containsKey(path)) {
                structuralErrors.add("Missing required field: " + path);
                continue;
            }
            if (!nullable && input.get(path) == null) {
                structuralErrors.add("Field '" + path + "' cannot be null");
            }
        }
        if (!structuralErrors.isEmpty()) {
            throw new IllegalArgumentException("Structural validation failed: " + structuralErrors);
        }

        // Niveau 2 — métier, délégué au ValidationEngine partagé avec BusinessValidatorService
        List<ValidationRuleData> ruleData = extractValidationRules(validationRules);
        List<FieldViolation> violations = validationEngine.validate(ruleData, new MapFieldResolver(input));
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Business validation failed: " + violations);
        }
    }

    private List<ValidationRuleData> extractValidationRules(List<Map<String, Object>> rules) {
        return rules.stream()
                .map(rule -> {
                    String fieldName = (String) rule.get("fieldName");
                    String ruleTypeStr = (String) rule.get("ruleType");
                    String pattern = (String) rule.get("pattern");
                    RuleType ruleType = parseRuleType(ruleTypeStr);
                    return ruleType == null ? null : new ValidationRuleData(fieldName, ruleType, pattern);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private RuleType parseRuleType(String raw) {
        if (raw == null) return null;
        try {
            return RuleType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown RuleType in rules.json: {}", raw);
            return null;
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<MappingRuleData> extractMappingRules(Map<String, Object> rules) {
        List<Map<String, Object>> mappingRules =
                (List<Map<String, Object>>) rules.getOrDefault("mappingRules", List.of());

        return mappingRules.stream()
                .map(rule -> {
                    String src = (String) rule.get("sourceField");
                    String tgt = (String) rule.get("targetField");
                    String typeStr = (String) rule.get("mappingType");
                    String expr = (String) rule.get("expression");
                    MappingType type = parseMappingType(typeStr);
                    return type == null ? null : new MappingRuleData(null, src, tgt, type, expr != null ? expr.trim() : null);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private MappingType parseMappingType(String raw) {
        if (raw == null) return null;
        try {
            return MappingType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unsupported mapping type in rules.json: {}", raw);
            return null;
        }
    }
}

package com.miniESB.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.responsemapping.ResponseFieldRuleData;
import com.miniESB.engine.responsemapping.ResponseMappingEngine;
import com.miniESB.engine.responsemapping.ResponseMappingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Réplique en mémoire (sans JPA) de la logique T6 de {@code ResponseMappingServiceImpl},
 * utilisée par {@code EngineProcessService} dans l'image Docker autonome (mode "engine").
 *
 * <p><strong>Depuis le refactoring</strong>, cette classe ne contient plus sa propre
 * copie du dispatch par {@code MappingType} : elle convertit les règles JSON du
 * fichier {@code rules.json} en {@link ResponseFieldRuleData} (même format neutre
 * que le mode base de données) et délègue à {@link ResponseMappingEngine} — le
 * même moteur que {@code ResponseMappingServiceImpl}. Élimine la duplication qui
 * existait entre les deux classes (violation SRP/OCP/DRY).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "true")
public class EngineResponseMappingHelper {

    private final ObjectMapper objectMapper;
    private final ResponseMappingEngine responseMappingEngine;

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

        List<ResponseFieldRuleData> applicableRules = rules.stream()
                .filter(r -> {
                    Object rProviderId = r.get("providerId");
                    return rProviderId == null
                            || Objects.equals(((Number) rProviderId).longValue(), providerId);
                })
                .map(this::toRuleData)
                .filter(Objects::nonNull)
                .toList();

        ResponseMappingResult mapping = responseMappingEngine.apply(applicableRules, sourceMap);

        result.put("validationPassed", mapping.passed());
        result.put("validationErrors", mapping.violations());
        result.put("mappedBody", mapping.mappedBody());
    }

    private ResponseFieldRuleData toRuleData(Map<String, Object> rule) {
        String srcField = (String) rule.get("sourceField");
        String tgtField = (String) rule.get("targetField");
        String typeStr = (String) rule.get("mappingType");
        String expr = (String) rule.get("expression");
        boolean required = Boolean.TRUE.equals(rule.get("required"));

        MappingType type = parseMappingType(typeStr);
        if (type == null) return null;

        return new ResponseFieldRuleData(null, srcField, tgtField, type, expr, required);
    }

    private MappingType parseMappingType(String raw) {
        if (raw == null) return null;
        try {
            return MappingType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unsupported response mapping type in rules.json: {}", raw);
            return null;
        }
    }
}

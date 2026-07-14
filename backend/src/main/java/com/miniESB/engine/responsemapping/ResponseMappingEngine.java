package com.miniESB.engine.responsemapping;

import com.miniESB.domain.enums.MappingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Moteur de mapping de réponses providers (T6), partagé entre
 * {@code ResponseMappingServiceImpl} (règles en base) et
 * {@code EngineResponseMappingHelper} (règles JSON fichier, mode "engine").
 *
 * <p>Avant ce refactoring, les deux classes dupliquaient intégralement cette
 * logique (violation DRY + SRP + OCP) — exactement le même schéma que celui
 * corrigé pour {@code MappingServiceImpl}/{@code EngineProcessService}
 * (voir {@code com.miniESB.engine.mapping}).</p>
 */
@Slf4j
@Component
public class ResponseMappingEngine {

    private final Map<MappingType, ResponseFieldTransformStrategy> strategies;

    public ResponseMappingEngine(List<ResponseFieldTransformStrategy> strategyBeans) {
        this.strategies = new EnumMap<>(MappingType.class);
        for (ResponseFieldTransformStrategy strategy : strategyBeans) {
            strategies.put(strategy.supports(), strategy);
        }
    }

    public ResponseMappingResult apply(List<ResponseFieldRuleData> rules, Map<String, Object> source) {
        List<String> violations = new ArrayList<>();
        Map<String, Object> mappedBody = new LinkedHashMap<>();

        for (ResponseFieldRuleData rule : rules) {
            Object value = source.get(rule.sourceField());

            if (value == null) {
                if (rule.required()) {
                    violations.add("Required field '" + rule.sourceField() + "' is missing in provider response");
                }
                continue;
            }

            ResponseFieldTransformStrategy strategy = strategies.get(rule.mappingType());
            if (strategy == null) {
                log.warn("Unsupported response mapping type: {}", rule.mappingType());
                continue;
            }

            try {
                Object transformed = strategy.apply(value, rule.expression(), source);
                mappedBody.put(rule.targetField(), transformed);
            } catch (Exception e) {
                log.warn("Response mapping rule {} — transformation failed for field '{}': {}",
                        rule.label(), rule.sourceField(), e.getMessage());
                violations.add("Transformation failed for field '" + rule.sourceField() + "': " + e.getMessage());
            }
        }

        // Passthrough : les champs non couverts par une règle sont conservés tels quels
        source.forEach(mappedBody::putIfAbsent);

        return new ResponseMappingResult(violations.isEmpty(), violations, mappedBody);
    }
}

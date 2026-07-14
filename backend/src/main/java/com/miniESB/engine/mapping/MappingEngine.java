package com.miniESB.engine.mapping;

import com.miniESB.domain.enums.MappingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moteur de mapping unique, partagé entre {@code MappingServiceImpl} (règles en
 * base de données) et {@code EngineProcessService} (règles chargées depuis un
 * fichier {@code rules.json}). Avant ce refactoring, ces deux classes dupliquaient
 * intégralement cette logique (violation DRY + SRP + OCP).
 *
 * <p>Chaque {@link MappingType} est traité par une {@link MappingStrategy} dédiée,
 * injectée automatiquement par Spring : ajouter un type de mapping ne nécessite
 * plus de modifier cette classe (Open/Closed Principle).</p>
 */
@Slf4j
@Component
public class MappingEngine {

    private final Map<MappingType, MappingStrategy> strategies;

    public MappingEngine(List<MappingStrategy> strategyBeans) {
        this.strategies = new EnumMap<>(MappingType.class);
        for (MappingStrategy strategy : strategyBeans) {
            strategies.put(strategy.supports(), strategy);
        }
    }

    /**
     * Applique la liste de règles (déjà filtrée sur "actives" par l'appelant)
     * au payload d'entrée, et retourne le payload transformé, réordonné pour
     * suivre au mieux l'ordre des clés d'entrée.
     */
    public Map<String, Object> apply(List<MappingRuleData> rules, Map<String, Object> input) {
        if (rules.isEmpty()) {
            log.warn("No mapping rule provided — returning input as-is");
            return input;
        }

        Map<String, Object> output = new LinkedHashMap<>(input);

        for (MappingRuleData rule : rules) {
            MappingStrategy strategy = strategies.get(rule.mappingType());
            if (strategy == null) {
                log.warn("Unsupported mapping type: {}", rule.mappingType());
                continue;
            }
            try {
                strategy.apply(rule, input, output);
            } catch (Exception e) {
                log.error("Mapping rule {} [{} → {}] failed: {}",
                        rule.label(), rule.sourceField(), rule.targetField(), e.getMessage());
            }
        }

        return PayloadPathUtils.reorderByInput(input, output, rules);
    }
}

package com.miniESB.engine.mapping;

import com.miniESB.domain.enums.MappingType;

import java.util.Map;

/**
 * Une stratégie applique un {@link MappingRuleData} (d'un {@link MappingType} donné)
 * sur le payload en cours de construction.
 *
 * <p>Remplace les blocs {@code if (rule.getMappingType() == X) ...} qui étaient
 * dupliqués dans {@code MappingServiceImpl} et {@code EngineProcessService}
 * (violation Open/Closed). Ajouter un nouveau {@link MappingType} = ajouter une
 * nouvelle implémentation de cette interface, sans toucher au code existant.</p>
 */
public interface MappingStrategy {

    MappingType supports();

    /**
     * @param rule   la règle à appliquer
     * @param input  payload source (lecture seule, jamais modifié)
     * @param output payload en cours de construction (modifié en place)
     */
    void apply(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output);
}

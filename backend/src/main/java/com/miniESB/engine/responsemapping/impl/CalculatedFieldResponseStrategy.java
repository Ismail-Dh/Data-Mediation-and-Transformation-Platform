package com.miniESB.engine.responsemapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.responsemapping.ResponseFieldTransformStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CALCULATED_FIELD : l'expression contient les noms de champs séparés par '+'
 * (ex: {@code firstName+' '+lastName}) et cette stratégie concatène les
 * valeurs des champs listés. Comportement historique T6, préservé tel quel
 * (le nom "CALCULATED_FIELD" est trompeur — il ne s'agit pas d'un calcul
 * arithmétique comme en T5, mais d'une concaténation).
 */
@Component
public class CalculatedFieldResponseStrategy implements ResponseFieldTransformStrategy {

    @Override
    public MappingType supports() {
        return MappingType.CALCULATED_FIELD;
    }

    @Override
    public Object apply(Object value, String expression, Map<String, Object> source) {
        if (expression == null || expression.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : expression.split("\\+")) {
            String token = part.trim().replace("'", "");
            sb.append(source.getOrDefault(token, token));
        }
        return sb.toString();
    }
}

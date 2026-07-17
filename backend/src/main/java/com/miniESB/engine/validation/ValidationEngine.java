package com.miniESB.engine.validation;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.exception.FieldViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Moteur de validation "niveau 2" (métier) unique, partagé entre
 * {@code BusinessValidatorService} (règles en base, payload en {@code JsonNode})
 * et {@code EngineProcessService} (règles JSON fichier, payload en {@code Map}).
 *
 * <p>Avant ce refactoring, les deux classes dupliquaient le même
 * {@code switch (ruleType)} (violation DRY + OCP). Ici, chaque {@link RuleType}
 * est traité par une {@link ValidationStrategy} dédiée, injectée par Spring.</p>
 */
@Slf4j
@Component
public class ValidationEngine {

    private final Map<RuleType, ValidationStrategy> strategies;

    public ValidationEngine(List<ValidationStrategy> strategyBeans) {
        this.strategies = new EnumMap<>(RuleType.class);
        for (ValidationStrategy strategy : strategyBeans) {
            strategies.put(strategy.supports(), strategy);
        }
    }

    /**
     * Valide {@code rules} contre le payload exposé par {@code resolver}.
     *
     * @return la liste des violations trouvées (vide si tout est valide)
     */
    public List<FieldViolation> validate(List<ValidationRuleData> rules, FieldResolver resolver) {
        List<FieldViolation> violations = new ArrayList<>();

        for (ValidationRuleData rule : rules) {
            // NOT_NULL est un cas particulier : il vérifie la présence/nullité,
            // et doit donc être évalué même quand le champ est absent — contrairement
            // à toutes les autres règles, sautées dans ce cas (niveau-1 gère la présence).
            if (rule.ruleType() == RuleType.NOT_NULL) {
                if (!resolver.isPresentAndNotNull(rule.fieldName())) {
                    violations.add(new FieldViolation(rule.fieldName(), "INVALID_FORMAT",
                            "Field '" + rule.fieldName() + "' must not be null or missing (NOT_NULL rule)"));
                }
                continue;
            }

            if (!resolver.isPresentAndNotNull(rule.fieldName())) continue; // absent → pas validé ici

            String value = resolver.resolveAsText(rule.fieldName());

            ValidationStrategy strategy = strategies.get(rule.ruleType());
            if (strategy == null) {
                log.warn("Unknown RuleType: {}", rule.ruleType());
                continue;
            }

            strategy.validate(rule, value).ifPresent(violations::add);
        }

        return violations;
    }
}

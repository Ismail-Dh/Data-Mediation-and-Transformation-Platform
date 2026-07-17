package com.miniESB.engine.validation;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.exception.FieldViolation;

import java.util.Optional;

/**
 * Une stratégie de validation pour un {@link RuleType} donné.
 *
 * <p>Remplace le {@code switch (rule.getRuleType())} qui était dupliqué dans
 * {@code BusinessValidatorService} et {@code EngineProcessService} (violation
 * Open/Closed). Ajouter un nouveau {@link RuleType} = ajouter une nouvelle
 * implémentation, sans modifier le code existant.</p>
 */
public interface ValidationStrategy {

    RuleType supports();

    /**
     * @param rule  la règle (contient le pattern éventuel)
     * @param value valeur textuelle déjà résolue (jamais {@code null} — les règles
     *              autres que NOT_NULL ne sont pas invoquées si le champ est absent)
     * @return une violation si la valeur ne respecte pas la règle, vide sinon
     */
    Optional<FieldViolation> validate(ValidationRuleData rule, String value);
}

package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.engine.validation.ValidationStrategy;
import com.miniESB.exception.FieldViolation;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * NOT_NULL est un cas particulier : {@link com.miniESB.engine.validation.ValidationEngine}
 * l'invoque avec la présence déjà résolue via {@link com.miniESB.engine.validation.FieldResolver}
 * plutôt qu'avec une valeur textuelle (contrairement aux autres règles, qui sont
 * sautées quand le champ est absent/null). Cette implémentation garde donc une
 * signature cohérente avec les autres stratégies, mais n'est en pratique jamais
 * invoquée via {@code validate(rule, value)} — voir {@code ValidationEngine}.
 */
@Component
public class NotNullValidationStrategy implements ValidationStrategy {

    @Override
    public RuleType supports() {
        return RuleType.NOT_NULL;
    }

    @Override
    public Optional<FieldViolation> validate(ValidationRuleData rule, String value) {
        return Optional.empty();
    }
}

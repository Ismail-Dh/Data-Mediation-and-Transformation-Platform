package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import org.springframework.stereotype.Component;

@Component
public class RegexPasswordValidationStrategy extends AbstractDynamicPatternValidationStrategy {

    @Override
    public RuleType supports() {
        return RuleType.REGEX_PASSWORD;
    }

    @Override
    protected String ruleLabel() {
        return "REGEX_PASSWORD";
    }
}

package com.miniESB.engine.validation.impl;

import com.miniESB.domain.enums.RuleType;
import org.springframework.stereotype.Component;

@Component
public class RegexEmailValidationStrategy extends AbstractDynamicPatternValidationStrategy {

    @Override
    public RuleType supports() {
        return RuleType.REGEX_EMAIL;
    }

    @Override
    protected String ruleLabel() {
        return "REGEX_EMAIL";
    }
}

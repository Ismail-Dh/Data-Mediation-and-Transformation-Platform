package com.miniESB.engine.mapping.operator;

import com.miniESB.engine.mapping.ValueTransformOperator;
import org.springframework.stereotype.Component;

@Component
public class UppercaseOperator implements ValueTransformOperator {

    @Override
    public boolean supports(String expression) {
        return expression.equalsIgnoreCase("UPPERCASE");
    }

    @Override
    public Object apply(Object rawValue, String expression) {
        return rawValue.toString().toUpperCase();
    }
}

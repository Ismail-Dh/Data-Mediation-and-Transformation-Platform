package com.miniESB.engine.mapping.operator;

import com.miniESB.engine.mapping.ValueTransformOperator;
import org.springframework.stereotype.Component;

@Component
public class TrimOperator implements ValueTransformOperator {

    @Override
    public boolean supports(String expression) {
        return expression.equalsIgnoreCase("TRIM");
    }

    @Override
    public Object apply(Object rawValue, String expression) {
        return rawValue.toString().trim();
    }
}

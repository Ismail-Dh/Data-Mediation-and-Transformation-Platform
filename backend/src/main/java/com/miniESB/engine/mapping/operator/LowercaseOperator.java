package com.miniESB.engine.mapping.operator;

import com.miniESB.engine.mapping.ValueTransformOperator;
import org.springframework.stereotype.Component;

@Component
public class LowercaseOperator implements ValueTransformOperator {

    @Override
    public boolean supports(String expression) {
        return expression.equalsIgnoreCase("LOWERCASE");
    }

    @Override
    public Object apply(Object rawValue, String expression) {
        return rawValue.toString().toLowerCase();
    }
}

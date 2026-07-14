package com.miniESB.engine.mapping.operator;

import com.miniESB.engine.mapping.ValueTransformOperator;
import org.springframework.stereotype.Component;

/** Expression : {@code REGEX_REPLACE:pattern:replacement} (replacement peut être vide) */
@Component
public class RegexReplaceOperator implements ValueTransformOperator {

    private static final String PREFIX = "REGEX_REPLACE:";

    @Override
    public boolean supports(String expression) {
        return expression.toUpperCase().startsWith(PREFIX);
    }

    @Override
    public Object apply(Object rawValue, String expression) {
        String body = expression.substring(PREFIX.length());
        int sepIdx = body.indexOf(":");
        if (sepIdx < 0)
            throw new IllegalArgumentException("REGEX_REPLACE format: REGEX_REPLACE:pattern:replacement");

        String pattern = body.substring(0, sepIdx);
        String replacement = body.substring(sepIdx + 1);
        return rawValue.toString().replaceAll(pattern, replacement);
    }
}

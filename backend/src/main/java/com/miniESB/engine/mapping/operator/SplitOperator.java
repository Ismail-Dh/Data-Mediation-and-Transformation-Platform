package com.miniESB.engine.mapping.operator;

import com.miniESB.engine.mapping.ValueTransformOperator;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Expression : {@code SPLIT:separator:index} */
@Component
public class SplitOperator implements ValueTransformOperator {

    @Override
    public boolean supports(String expression) {
        return expression.toUpperCase().startsWith("SPLIT:");
    }

    @Override
    public Object apply(Object rawValue, String expression) {
        String[] parts = expression.split(":", 3);
        if (parts.length < 3)
            throw new IllegalArgumentException("SPLIT format: SPLIT:separator:index");

        String separator = parts[1];
        int index = Integer.parseInt(parts[2].trim());
        String[] tokens = rawValue.toString().split(Pattern.quote(separator));

        if (index < 0 || index >= tokens.length)
            throw new IllegalArgumentException(
                    "SPLIT index " + index + " out of bounds (length=" + tokens.length + ")");

        return tokens[index].trim();
    }
}

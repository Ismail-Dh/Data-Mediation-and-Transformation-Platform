package com.miniESB.exception;

import java.util.List;

public class PayloadValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public PayloadValidationException(List<FieldViolation> violations) {
        super("Payload structural validation failed");
        this.violations = violations;
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
package com.miniESB.exception;


import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an Admin attempts to delete a global validation rule
 * that is still referenced by at least one pipeline.
 *
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class RuleInUseException extends RuntimeException {

    public RuleInUseException(Long ruleId) {
        super("Global validation rule with id=" + ruleId
                + " is still used by one or more pipelines and cannot be deleted.");
    }
}
package com.miniESB.exception;

/**
 * Thrown when an attempt is made to mutate a PUBLISHED or DISABLED template directly.
 */
public class TemplateImmutableException extends RuntimeException {

    public TemplateImmutableException(Long id, String status) {
        super("Template id=" + id + " is " + status
                + " and cannot be modified in place. A new DRAFT version will be created.");
    }
}
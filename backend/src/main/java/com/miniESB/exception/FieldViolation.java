package com.miniESB.exception;

public record FieldViolation(
    String fieldPath,
    String errorType,   // MISSING_FIELD, TYPE_MISMATCH, NULL_NOT_ALLOWED, INVALID_FORMAT
    String message
) {}
package com.miniESB.domain.enums;

/**
 * Supported validation rule types for niveau-2 business validation.
 *
 * Pattern field usage per type:
 *  - NOT_NULL        → no pattern (ignored)
 *  - TYPE_NUMBER     → optional regex override (default: any parseable double)
 *  - TYPE_DATE       → optional regex override (default: ISO-8601 YYYY-MM-DD)
 *  - REGEX_EMAIL     → required: regex stored in rule.pattern
 *  - REGEX_PHONE     → required: regex stored in rule.pattern
 *  - REGEX_PASSWORD  → required: regex stored in rule.pattern
 *  - REGEX           → required: free-form custom regex stored in rule.pattern
 *  - MIN_MAX_LENGTH  → required: "min,max" format stored in rule.pattern
 */
public enum RuleType {
    NOT_NULL,
    TYPE_NUMBER,
    TYPE_DATE,
    REGEX_EMAIL,
    REGEX_PHONE,
    REGEX_PASSWORD,
    REGEX,           // ← custom free-form regex
    MIN_MAX_LENGTH
}
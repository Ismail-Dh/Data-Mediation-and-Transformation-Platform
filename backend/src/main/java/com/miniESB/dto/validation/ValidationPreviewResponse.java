package com.miniESB.dto.validation;

import com.miniESB.exception.FieldViolation;

import java.util.List;

/**
 * Dry-run result covering both validation levels.
 *
 * Example (all good):
 * {
 *   "valid": true,
 *   "structuralOk": true,
 *   "businessOk": true,
 *   "fieldsChecked": 4,
 *   "rulesChecked": 3,
 *   "violationCount": 0,
 *   "violations": []
 * }
 *
 * Example (niveau-2 failure):
 * {
 *   "valid": false,
 *   "structuralOk": true,
 *   "businessOk": false,
 *   "fieldsChecked": 4,
 *   "rulesChecked": 3,
 *   "violationCount": 1,
 *   "violations": [
 *     { "fieldPath": "email", "errorType": "INVALID_FORMAT", "message": "…" }
 *   ]
 * }
 */
public record ValidationPreviewResponse(

        /** true only when both structural and business validations pass. */
        boolean valid,

        /** Niveau-1: schema shape check (presence, types, null constraints). */
        boolean structuralOk,

        /** Niveau-2: business rules check (NOT_NULL, REGEX_EMAIL, MIN_MAX_LENGTH…). */
        boolean businessOk,

        /** Number of PipelineField definitions checked (0 = no schema). */
        int fieldsChecked,

        /** Number of active ValidationRule definitions applied (0 = no rules). */
        int rulesChecked,

        /** Total violations found (structural + business). */
        int violationCount,

        /** Detailed violations list (empty when valid=true). */
        List<FieldViolation> violations
) {}
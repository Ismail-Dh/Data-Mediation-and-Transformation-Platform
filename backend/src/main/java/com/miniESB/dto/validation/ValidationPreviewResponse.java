package com.miniESB.dto.validation;

import com.miniESB.exception.FieldViolation;

import java.util.List;

/**
 * Result of a structural validation dry-run (niveau 1).
 *
 * Example response (valid):
 * {
 *   "valid": true,
 *   "structuralOk": true,
 *   "fieldsChecked": 4,
 *   "violationCount": 0,
 *   "violations": []
 * }
 *
 * Example response (invalid):
 * {
 *   "valid": false,
 *   "structuralOk": false,
 *   "fieldsChecked": 4,
 *   "violationCount": 2,
 *   "violations": [
 *     { "fieldPath": "customer.email", "errorType": "MISSING_FIELD", "message": "…" },
 *     { "fieldPath": "amount",         "errorType": "TYPE_MISMATCH",  "message": "…" }
 *   ]
 * }
 */
public record ValidationPreviewResponse(

        /** true only when structuralOk is true (no violations found). */
        boolean valid,

        /** Result of niveau-1 structural validation. */
        boolean structuralOk,

        /** Number of PipelineField definitions checked. 0 means no schema defined. */
        int fieldsChecked,

        /** Total number of violations found. */
        int violationCount,

        /** Detailed list of violations (empty when valid=true). */
        List<FieldViolation> violations
) {}
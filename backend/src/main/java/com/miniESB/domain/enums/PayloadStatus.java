package com.miniESB.domain.enums;

/**
 * Lifecycle status of a payload submitted to a pipeline.
 *
 * Flow (niveau 1 — structural validation only):
 *   RECEIVED → VALIDATED → MAPPED → SENT
 *                       ↘ FAILED  (structural violation or send error)
 */
public enum PayloadStatus {

    /** Payload received, structural validation not yet run (intermediate — rare). */
    RECEIVED,

    /**
     * Passed structural validation (niveau 1).
     * All required fields present with correct types.
     */
    VALIDATED,

    /**
     * Structural validation failed.
     * Payload stored for audit; violations returned as 422 to the caller.
     */
    FAILED,

    /** Payload mapped to the provider's target format. */
    MAPPED,

    /** Successfully forwarded to the configured provider. */
    SENT
}
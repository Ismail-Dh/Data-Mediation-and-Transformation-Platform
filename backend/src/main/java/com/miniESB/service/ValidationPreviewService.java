package com.miniESB.service;

import com.miniESB.dto.validation.ValidationPreviewRequest;
import com.miniESB.dto.validation.ValidationPreviewResponse;

/**
 * Dry-run validation service — niveau 1 (structural) only.
 * Never persists any payload.
 */
public interface ValidationPreviewService {

    /**
     * Runs structural validation against the pipeline schema
     * and returns a full violation report without saving anything.
     *
     * @param pipelineId target pipeline (must exist)
     * @param request    the raw payload + format to test
     * @return           preview result with valid flag and violation list
     */
    ValidationPreviewResponse preview(Long pipelineId, ValidationPreviewRequest request);
}
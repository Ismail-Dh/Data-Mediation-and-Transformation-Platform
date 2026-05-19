package com.miniESB.dto.mapping;

import java.util.Map;

// Response DTO returned after applying mapping rules to a raw payload.
// Contains both the original and the transformed payload for comparison.
public record MappingResultResponse(
    Long pipelineId,
    Map<String, Object> original,
    Map<String, Object> mapped
) {}
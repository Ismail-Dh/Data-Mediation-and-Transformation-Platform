package com.miniESB.dto.Pipeline;

import java.util.List;

public record UpdatePipelineRequest(
        String name,
        String version,
        String inputFormat,
        String outputFormat,

        /** IDs des providers à attacher (remplace providerId). */
        List<Long> providerIds
) {}
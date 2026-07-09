package com.miniESB.dto.Pipeline;

import java.util.List;

public record UpdatePipelineRequest(
        String name,
        String version,
        String inputFormat,
        String outputFormat,

        /** Providers à attacher (remplace providerIds) — null = ne pas modifier, [] = tout détacher. */
        List<PipelineProviderRequest> providers
) {}
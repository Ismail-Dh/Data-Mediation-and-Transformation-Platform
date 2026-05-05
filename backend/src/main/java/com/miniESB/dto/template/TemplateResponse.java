package com.miniESB.dto.template;

import com.miniESB.domain.enums.TemplateStatus;
import com.miniESB.domain.enums.TemplateType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response payload returned for both ValidationTemplate and MappingTemplate.
 */
public record TemplateResponse(
        Long id,
        String name,
        String description,
        TemplateType type,
        Map<String, Object> content,
        TemplateStatus status,
        Integer version,
        Long parentId,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
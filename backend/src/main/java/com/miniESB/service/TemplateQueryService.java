package com.miniESB.service;

import com.miniESB.domain.enums.TemplateType;
import com.miniESB.dto.template.TemplateResponse;

import java.util.List;

/**
 * Accès en lecture seule aux templates PUBLISHED, pour usage développeur
 * (configuration de pipeline). Voir {@link TemplateAdminService} pour le
 * pendant administration.
 */
public interface TemplateQueryService {

    /** Return only PUBLISHED templates — Developer view. */
    List<TemplateResponse> findPublished(TemplateType type);
}

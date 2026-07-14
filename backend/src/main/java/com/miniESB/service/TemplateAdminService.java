package com.miniESB.service;

import com.miniESB.domain.enums.TemplateType;
import com.miniESB.dto.template.TemplateRequest;
import com.miniESB.dto.template.TemplateResponse;

import java.util.List;

/**
 * Opérations d'administration des templates (VALIDATION / MAPPING).
 *
 * <p>Séparée de {@link TemplateQueryService} (lecture seule, côté développeur)
 * pour respecter l'Interface Segregation Principle : avant ce refactoring,
 * les deux étaient réunies dans une seule interface {@code TemplateService},
 * ce qui obligeait {@code DeveloperTemplateController} à dépendre de 6
 * méthodes d'administration (create/update/publish/disable/findAll/findById)
 * qu'il n'utilisait jamais.</p>
 */
public interface TemplateAdminService {

    /** Create a new DRAFT template (VALIDATION or MAPPING). */
    TemplateResponse create(TemplateRequest request, String adminUsername);

    /**
     * Update a DRAFT template.
     * If the template is already PUBLISHED, a new DRAFT version is created
     * (immutability: published templates cannot be mutated in place).
     */
    TemplateResponse update(Long id, TemplateType type, TemplateRequest request, String adminUsername);

    /** Transition DRAFT → PUBLISHED. */
    TemplateResponse publish(Long id, TemplateType type);

    /** Transition PUBLISHED → DISABLED. */
    TemplateResponse disable(Long id, TemplateType type);

    /** Return all templates of a given type (all statuses) — Admin view. */
    List<TemplateResponse> findAll(TemplateType type);

    /** Return a single template by id and type — Admin view. */
    TemplateResponse findById(Long id, TemplateType type);
}

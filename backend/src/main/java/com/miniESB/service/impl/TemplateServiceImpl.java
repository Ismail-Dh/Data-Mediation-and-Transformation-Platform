package com.miniESB.service.impl;

import com.miniESB.domain.entity.MappingTemplate;
import com.miniESB.domain.entity.User;
import com.miniESB.domain.entity.ValidationTemplate;
import com.miniESB.domain.enums.TemplateStatus;
import com.miniESB.domain.enums.TemplateType;
import com.miniESB.dto.template.TemplateRequest;
import com.miniESB.dto.template.TemplateResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.MappingTemplateRepository;
import com.miniESB.repository.UserRepository;
import com.miniESB.repository.ValidationTemplateRepository;
import com.miniESB.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)


public class TemplateServiceImpl implements TemplateService {

    private final ValidationTemplateRepository validationRepo;
    private final MappingTemplateRepository mappingRepo;
    private final UserRepository userRepository;

    // =========================================================================
    // CREATE
    // =========================================================================

    @Override
    @Transactional
    public TemplateResponse create(TemplateRequest request, String adminUsername) {
        log.info("Admin '{}' creating {} template '{}'", adminUsername, request.type(), request.name());

        User admin = findUserOrThrow(adminUsername);

        return switch (request.type()) {
            case VALIDATION -> {
                ValidationTemplate t = ValidationTemplate.builder()
                        .name(request.name())
                        .description(request.description())
                        .content(request.content())
                        .status(TemplateStatus.DRAFT)
                        .version(1)
                        .parentId(null)
                        .createdBy(admin)
                        .build();
                yield toResponse(validationRepo.save(t), TemplateType.VALIDATION);
            }
            case MAPPING -> {
                MappingTemplate t = MappingTemplate.builder()
                        .name(request.name())
                        .description(request.description())
                        .content(request.content())
                        .status(TemplateStatus.DRAFT)
                        .version(1)
                        .parentId(null)
                        .createdBy(admin)
                        .build();
                yield toResponse(mappingRepo.save(t), TemplateType.MAPPING);
            }
        };
    }

    // =========================================================================
    // UPDATE  (immutability rule: published → new version)
    // =========================================================================

    @Override
    @Transactional
    public TemplateResponse update(Long id, TemplateType type, TemplateRequest request, String adminUsername) {
        log.info("Admin '{}' updating {} template id={}", adminUsername, type, id);

        User admin = findUserOrThrow(adminUsername);

        return switch (type) {
            case VALIDATION -> {
                ValidationTemplate existing = findValidationOrThrow(id);

                if (existing.getStatus() == TemplateStatus.PUBLISHED) {
                    // Immutability: fork into a new DRAFT version
                    log.info("Template id={} is PUBLISHED — creating new version", id);
                    ValidationTemplate newVersion = ValidationTemplate.builder()
                            .name(request.name())
                            .description(request.description())
                            .content(request.content())
                            .status(TemplateStatus.DRAFT)
                            .version(existing.getVersion() + 1)
                            .parentId(existing.getId())
                            .createdBy(admin)
                            .build();
                    yield toResponse(validationRepo.save(newVersion), TemplateType.VALIDATION);
                }

                if (existing.getStatus() == TemplateStatus.DISABLED) {
                    throw new IllegalArgumentException(
                            "Cannot update a DISABLED template (id=" + id + "). Create a new one instead.");
                }

                // DRAFT → update in place
                existing.setName(request.name());
                existing.setDescription(request.description());
                existing.setContent(request.content());
                yield toResponse(validationRepo.save(existing), TemplateType.VALIDATION);
            }

            case MAPPING -> {
                MappingTemplate existing = findMappingOrThrow(id);

                if (existing.getStatus() == TemplateStatus.PUBLISHED) {
                    log.info("Template id={} is PUBLISHED — creating new version", id);
                    MappingTemplate newVersion = MappingTemplate.builder()
                            .name(request.name())
                            .description(request.description())
                            .content(request.content())
                            .status(TemplateStatus.DRAFT)
                            .version(existing.getVersion() + 1)
                            .parentId(existing.getId())
                            .createdBy(admin)
                            .build();
                    yield toResponse(mappingRepo.save(newVersion), TemplateType.MAPPING);
                }

                if (existing.getStatus() == TemplateStatus.DISABLED) {
                    throw new IllegalArgumentException(
                            "Cannot update a DISABLED template (id=" + id + "). Create a new one instead.");
                }

                existing.setName(request.name());
                existing.setDescription(request.description());
                existing.setContent(request.content());
                yield toResponse(mappingRepo.save(existing), TemplateType.MAPPING);
            }
        };
    }

    // =========================================================================
    // PUBLISH  (DRAFT → PUBLISHED)
    // =========================================================================

    @Override
    @Transactional
    public TemplateResponse publish(Long id, TemplateType type) {
        log.info("Publishing {} template id={}", type, id);

        return switch (type) {
            case VALIDATION -> {
                ValidationTemplate t = findValidationOrThrow(id);
                assertTransitionAllowed(t.getStatus(), TemplateStatus.PUBLISHED, id);
                t.setStatus(TemplateStatus.PUBLISHED);
                yield toResponse(validationRepo.save(t), TemplateType.VALIDATION);
            }
            case MAPPING -> {
                MappingTemplate t = findMappingOrThrow(id);
                assertTransitionAllowed(t.getStatus(), TemplateStatus.PUBLISHED, id);
                t.setStatus(TemplateStatus.PUBLISHED);
                yield toResponse(mappingRepo.save(t), TemplateType.MAPPING);
            }
        };
    }

    // =========================================================================
    // DISABLE  (PUBLISHED → DISABLED)
    // =========================================================================

    @Override
    @Transactional
    public TemplateResponse disable(Long id, TemplateType type) {
        log.info("Disabling {} template id={}", type, id);

        return switch (type) {
            case VALIDATION -> {
                ValidationTemplate t = findValidationOrThrow(id);
                assertTransitionAllowed(t.getStatus(), TemplateStatus.DISABLED, id);
                t.setStatus(TemplateStatus.DISABLED);
                yield toResponse(validationRepo.save(t), TemplateType.VALIDATION);
            }
            case MAPPING -> {
                MappingTemplate t = findMappingOrThrow(id);
                assertTransitionAllowed(t.getStatus(), TemplateStatus.DISABLED, id);
                t.setStatus(TemplateStatus.DISABLED);
                yield toResponse(mappingRepo.save(t), TemplateType.MAPPING);
            }
        };
    }

    // =========================================================================
    // READ — Admin (all statuses)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<TemplateResponse> findAll(TemplateType type) {
        return switch (type) {
            case VALIDATION -> validationRepo.findAll().stream()
                    .map(t -> toResponse(t, TemplateType.VALIDATION)).toList();
            case MAPPING -> mappingRepo.findAll().stream()
                    .map(t -> toResponse(t, TemplateType.MAPPING)).toList();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse findById(Long id, TemplateType type) {
        return switch (type) {
            case VALIDATION -> toResponse(findValidationOrThrow(id), TemplateType.VALIDATION);
            case MAPPING    -> toResponse(findMappingOrThrow(id),    TemplateType.MAPPING);
        };
    }

    // =========================================================================
    // READ — Developer (PUBLISHED only)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<TemplateResponse> findPublished(TemplateType type) {
        return switch (type) {
            case VALIDATION -> validationRepo.findAllByStatus(TemplateStatus.PUBLISHED).stream()
                    .map(t -> toResponse(t, TemplateType.VALIDATION)).toList();
            case MAPPING -> mappingRepo.findAllByStatus(TemplateStatus.PUBLISHED).stream()
                    .map(t -> toResponse(t, TemplateType.MAPPING)).toList();
        };
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private ValidationTemplate findValidationOrThrow(Long id) {
        return validationRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ValidationTemplate not found with id=" + id));
    }

    private MappingTemplate findMappingOrThrow(Long id) {
        return mappingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MappingTemplate not found with id=" + id));
    }

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    /**
     * Validates allowed state transitions:
     *   DRAFT     → PUBLISHED  ✔
     *   PUBLISHED → DISABLED   ✔
     *   anything else          ✘
     */
    private void assertTransitionAllowed(TemplateStatus current, TemplateStatus target, Long id) {
        boolean allowed = (current == TemplateStatus.DRAFT     && target == TemplateStatus.PUBLISHED)
                || (current == TemplateStatus.PUBLISHED && target == TemplateStatus.DISABLED);
        if (!allowed) {
            throw new IllegalArgumentException(
                    "Invalid status transition for template id=" + id
                            + ": " + current + " → " + target);
        }
    }

    private TemplateResponse toResponse(ValidationTemplate t, TemplateType type) {
        return new TemplateResponse(
                t.getId(),
                t.getName(),
                t.getDescription(),
                type,
                t.getContent(),
                t.getStatus(),
                t.getVersion(),
                t.getParentId(),
                t.getCreatedBy() != null ? t.getCreatedBy().getUsername() : null,
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    private TemplateResponse toResponse(MappingTemplate t, TemplateType type) {
        return new TemplateResponse(
                t.getId(),
                t.getName(),
                t.getDescription(),
                type,
                t.getContent(),
                t.getStatus(),
                t.getVersion(),
                t.getParentId(),
                t.getCreatedBy() != null ? t.getCreatedBy().getUsername() : null,
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
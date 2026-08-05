package com.miniESB.service.impl;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.PipelineProvider;
import com.miniESB.domain.entity.Provider;
import com.miniESB.domain.entity.User;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.HttpRequestMethod;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.Pipeline.CreatePipelineRequest;
import com.miniESB.dto.Pipeline.PipelineDetailsResponse;
import com.miniESB.dto.Pipeline.PipelineProviderRequest;
import com.miniESB.dto.Pipeline.PipelineResponse;
import com.miniESB.dto.Pipeline.UpdatePipelineRequest;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineProviderRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ProviderRepository;
import com.miniESB.repository.UserRepository;
import com.miniESB.service.MappingService;
import com.miniESB.service.PipelineFieldService;
import com.miniESB.service.PipelineService;
import com.miniESB.service.PipelineValidationRuleService;
import com.miniESB.service.ResponseMappingRuleAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@Service
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

    private final PipelineRepository pipelineRepository;
    private final UserRepository     userRepository;
    private final ProviderRepository providerRepository;
    private final PipelineProviderRepository pipelineProviderRepository;

    // Sous-domaines rattachés au pipeline, utilisés pour construire la vue consolidée
    // (getPipelineFullDetails) consommée par les écrans Admin / Developer "détail".
    private final PipelineFieldService pipelineFieldService;
    private final PipelineValidationRuleService pipelineValidationRuleService;
    private final MappingService mappingService;
    private final ResponseMappingRuleAdminService responseMappingRuleAdminService;

    @Override
    @Transactional
    public PipelineResponse createPipeline(CreatePipelineRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Pipeline pipeline = Pipeline.builder()
                .name(request.name())
                .version(request.version())
                .inputFormat(DataFormat.valueOf(request.inputFormat()))
                .outputFormat(DataFormat.valueOf(request.outputFormat()))
                .status(PipelineStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .createdBy(user)
                .build();

        // 1) On sauvegarde d'abord le pipeline SEUL pour obtenir son ID généré
        //    (Provider IDENTITY) — indispensable avant de créer les liens, puisque
        //    PipelineProvider utilise une clé composite dérivée (pipeline_id, provider_id).
        pipeline = pipelineRepository.save(pipeline);

        // 2) On sauvegarde EXPLICITEMENT les liens pipeline↔provider (avec leur
        //    méthode HTTP) via leur propre repository, plutôt que de compter sur
        //    un cascade JPA fragile dans ce contexte.
        List<PipelineProvider> links = resolvePipelineProviders(pipeline, request.providers());
        links = pipelineProviderRepository.saveAll(links);

        return toResponse(pipeline, links);
    }

    @Override
    @Transactional
    public PipelineResponse updatePipeline(Long id, UpdatePipelineRequest request, String username) {
        Pipeline pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getRole().name().equals("ADMIN")) {
            checkOwnership(pipeline, username);
        }

        if (request.name()         != null) pipeline.setName(request.name());
        if (request.version()      != null) pipeline.setVersion(request.version());
        if (request.inputFormat()  != null) pipeline.setInputFormat(DataFormat.valueOf(request.inputFormat()));
        if (request.outputFormat() != null) pipeline.setOutputFormat(DataFormat.valueOf(request.outputFormat()));

        pipeline = pipelineRepository.save(pipeline);

        List<PipelineProvider> links;
        if (request.providers() != null) {
            // Remplacement explicite : on supprime d'abord tous les liens existants
            // de CE pipeline, on flush pour que le DELETE parte avant les INSERT
            // (sinon collision sur la clé composite pipeline_id+provider_id si un
            // même provider reste sélectionné avec une méthode HTTP différente),
            // puis on insère les nouveaux liens avec la méthode HTTP choisie.
            pipelineProviderRepository.deleteAllByPipelineId(pipeline.getId());
            pipelineProviderRepository.flush();

            links = resolvePipelineProviders(pipeline, request.providers());
            links = pipelineProviderRepository.saveAll(links);
        } else {
            // Pas de modification demandée sur les providers → on garde les liens existants.
            links = pipelineProviderRepository.findByPipeline_Id(pipeline.getId());
        }

        return toResponse(pipeline, links);
    }

    @Override
    @Transactional
    public void deletePipeline(Long id, String username) {
        Pipeline pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getRole().name().equals("ADMIN")) {
            checkOwnership(pipeline, username);
        }

        pipelineRepository.delete(pipeline);
    }

    @Override
    public PipelineResponse getPipelineById(Long id, String username) {
        Pipeline pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getRole().name().equals("ADMIN")) {
            checkOwnership(pipeline, username);
        }

        return toResponse(pipeline);
    }

    @Override
    public PipelineDetailsResponse getPipelineFullDetails(Long id, String username) {
        Pipeline pipeline = pipelineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getRole().name().equals("ADMIN")) {
            checkOwnership(pipeline, username);
        }

        return new PipelineDetailsResponse(
                toResponse(pipeline),
                pipelineFieldService.getFields(id),
                pipelineValidationRuleService.getRules(id),
                mappingService.getAllRulesByPipeline(id),
                responseMappingRuleAdminService.getRules(id)
        );
    }

    @Override
    public List<PipelineResponse> getMyPipelines(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return pipelineRepository.findByCreatedBy(user)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<PipelineResponse> getAllPipelines() {
        return pipelineRepository.findAll()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PipelineResponse validatePipeline(Long pipelineId) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + pipelineId));

        if (pipeline.getStatus() != PipelineStatus.CONFIGURED) {
            throw new IllegalStateException(
                    "Pipeline must be CONFIGURED before validation — current: " + pipeline.getStatus());
        }

        pipeline.setStatus(PipelineStatus.VALIDATED);
        pipelineRepository.save(pipeline);
        log.info("Pipeline id={} validated", pipelineId);
        return toResponse(pipeline);
    }

    @Override
    @Transactional
    public PipelineResponse revertPipeline(Long pipelineId) {
        Pipeline pipeline = pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + pipelineId));

        if (pipeline.getStatus() != PipelineStatus.VALIDATED) {
            throw new IllegalStateException(
                    "Pipeline must be VALIDATED to revert — current: " + pipeline.getStatus());
        }

        pipeline.setStatus(PipelineStatus.CONFIGURED);
        pipelineRepository.save(pipeline);
        log.info("Pipeline id={} reverted to CONFIGURED", pipelineId);
        return toResponse(pipeline);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Construit les associations PipelineProvider (provider + méthode HTTP) à partir
     * de la requête. La méthode HTTP est optionnelle par sélection — POST par défaut
     * si non fournie, pour rester rétrocompatible avec un appel sans httpMethod.
     */
    private List<PipelineProvider> resolvePipelineProviders(Pipeline pipeline, List<PipelineProviderRequest> selections) {
        if (selections == null || selections.isEmpty()) return new ArrayList<>();
        return selections.stream()
                .map(sel -> {
                    Provider provider = providerRepository.findById(sel.providerId())
                            .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + sel.providerId()));
                    return PipelineProvider.builder()
                            .pipeline(pipeline)
                            .provider(provider)
                            .httpMethod(sel.httpMethod() != null ? sel.httpMethod() : HttpRequestMethod.POST)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private void checkOwnership(Pipeline pipeline, String username) {
        if (!pipeline.getCreatedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("You are not the owner of this pipeline");
        }
    }

    private PipelineResponse toResponse(Pipeline p) {
        return toResponse(p, p.getPipelineProviders());
    }

    /**
     * Variante utilisée après create/update : on passe explicitement les liens
     * qu'on vient de sauvegarder via PipelineProviderRepository, plutôt que de
     * relire p.getPipelineProviders() (collection LAZY potentiellement périmée
     * puisqu'on gère désormais ces lignes hors du cycle de vie cascadé du pipeline).
     */
    private PipelineResponse toResponse(Pipeline p, List<PipelineProvider> pipelineProviders) {
        List<PipelineResponse.ProviderSummary> providerSummaries = pipelineProviders == null
                ? List.of()
                : pipelineProviders.stream()
                .map(link -> new PipelineResponse.ProviderSummary(
                        link.getProvider().getId(),
                        link.getProvider().getName(),
                        link.getProvider().getEndpoint(),
                        link.getHttpMethod()))
                .toList();

        return new PipelineResponse(
                p.getId(),
                p.getName(),
                p.getVersion(),
                p.getCreatedAt(),
                p.getInputFormat().name(),
                p.getOutputFormat().name(),
                p.getStatus().name(),
                p.getCreatedBy().getUsername(),
                providerSummaries
        );
    }
}
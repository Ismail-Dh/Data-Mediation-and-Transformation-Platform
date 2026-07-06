package com.miniESB.service.impl;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.Provider;
import com.miniESB.domain.entity.User;
import com.miniESB.domain.enums.DataFormat;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.Pipeline.CreatePipelineRequest;
import com.miniESB.dto.Pipeline.PipelineResponse;
import com.miniESB.dto.Pipeline.UpdatePipelineRequest;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.repository.ProviderRepository;
import com.miniESB.repository.UserRepository;
import com.miniESB.service.PipelineService;
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

    @Override
    @Transactional
    public PipelineResponse createPipeline(CreatePipelineRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Provider> providers = resolveProviders(request.providerIds());

        Pipeline pipeline = Pipeline.builder()
                .name(request.name())
                .version(request.version())
                .inputFormat(DataFormat.valueOf(request.inputFormat()))
                .outputFormat(DataFormat.valueOf(request.outputFormat()))
                .status(PipelineStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .createdBy(user)
                .providers(providers)
                .build();

        return toResponse(pipelineRepository.save(pipeline));
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

        if (request.providerIds() != null) {
            pipeline.setProviders(resolveProviders(request.providerIds()));
        }

        return toResponse(pipelineRepository.save(pipeline));
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

    private List<Provider> resolveProviders(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        return ids.stream()
                .map(id -> providerRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Provider not found: " + id)))
                .collect(Collectors.toList());
    }

    private void checkOwnership(Pipeline pipeline, String username) {
        if (!pipeline.getCreatedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("You are not the owner of this pipeline");
        }
    }

    private PipelineResponse toResponse(Pipeline p) {
        List<PipelineResponse.ProviderSummary> providerSummaries = p.getProviders() == null
                ? List.of()
                : p.getProviders().stream()
                .map(pr -> new PipelineResponse.ProviderSummary(
                        pr.getId(), pr.getName(), pr.getEndpoint()))
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
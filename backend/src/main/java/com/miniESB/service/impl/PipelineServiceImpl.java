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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

    private final PipelineRepository pipelineRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    @Override
    @Transactional
    public PipelineResponse createPipeline(CreatePipelineRequest request, String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Provider provider = null;
        if (request.providerId() != null) {
            provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        }

        Pipeline pipeline = Pipeline.builder()
            .name(request.name())
            .version(request.version())
            .inputFormat(DataFormat.valueOf(request.inputFormat()))
            .outputFormat(DataFormat.valueOf(request.outputFormat()))
            .status(PipelineStatus.DRAFT)
            .createdAt(LocalDateTime.now())
            .createdBy(user)
            .provider(provider)
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

        if (request.name() != null) pipeline.setName(request.name());
        if (request.version() != null) pipeline.setVersion(request.version());
        if (request.inputFormat() != null) pipeline.setInputFormat(DataFormat.valueOf(request.inputFormat()));
        if (request.outputFormat() != null) pipeline.setOutputFormat(DataFormat.valueOf(request.outputFormat()));
        if (request.providerId() != null) {
            Provider provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
            pipeline.setProvider(provider);
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
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    public List<PipelineResponse> getAllPipelines() {
        return pipelineRepository.findAll()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private void checkOwnership(Pipeline pipeline, String username) {
        if (!pipeline.getCreatedBy().getUsername().equals(username)) {
            throw new AccessDeniedException("You are not the owner of this pipeline");
        }
    }

    private PipelineResponse toResponse(Pipeline p) {
        return new PipelineResponse(
            p.getId(),
            p.getName(),
            p.getVersion(),
            p.getCreatedAt(),
            p.getInputFormat().name(),
            p.getOutputFormat().name(),
            p.getStatus().name(),
            p.getCreatedBy().getUsername(),
            p.getProvider() != null ? p.getProvider().getId() : null,
            p.getProvider() != null ? p.getProvider().getName() : null,
            p.getProvider() != null ? p.getProvider().getEndpoint() : null
        );
    }
}
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PipelineServiceImpl implements PipelineService {

    private final PipelineRepository pipelineRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    @Autowired
    public PipelineServiceImpl(PipelineRepository pipelineRepository,
                                UserRepository userRepository,
                                ProviderRepository providerRepository) {
        this.pipelineRepository = pipelineRepository;
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
    }

   @Override
@Transactional
public PipelineResponse createPipeline(CreatePipelineRequest request, String username) {
    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    Provider provider;

    if (request.providerId() != null) {
        provider = providerRepository.findById(request.providerId())
            .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

    } else if (request.providerName() != null && request.providerEndpoint() != null
               && request.providerProtocol() != null && request.providerTimeout() != null) {
        provider = Provider.builder()
            .name(request.providerName())
            .endpoint(request.providerEndpoint())
            .protocol(request.providerProtocol())
            .timeout(request.providerTimeout())
            .build();
        provider = providerRepository.save(provider);

    } else {
        throw new IllegalArgumentException(
            "You must either provide a providerId or full provider details (name, endpoint, protocol, timeout)"
        );
    }

    Pipeline pipeline = Pipeline.builder()
        .name(request.name())
        .providerUrl(request.providerUrl())
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

        checkOwnership(pipeline, username);

        if (request.name() != null) pipeline.setName(request.name());
        if (request.providerUrl() != null) pipeline.setProviderUrl(request.providerUrl());
        if (request.version() != null) pipeline.setVersion(request.version());
        if (request.inputFormat() != null) pipeline.setInputFormat(DataFormat.valueOf(request.inputFormat()));
        if (request.outputFormat() != null) pipeline.setOutputFormat(DataFormat.valueOf(request.outputFormat()));
        if (request.providerId() != null) {
    Provider provider = providerRepository.findById(request.providerId())
        .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
    pipeline.setProvider(provider);

} else if (request.providerName() != null && request.providerEndpoint() != null
           && request.providerProtocol() != null && request.providerTimeout() != null) {
    Provider provider = Provider.builder()
        .name(request.providerName())
        .endpoint(request.providerEndpoint())
        .protocol(request.providerProtocol())
        .timeout(request.providerTimeout())
        .build();
    pipeline.setProvider(providerRepository.save(provider));
}

        return toResponse(pipelineRepository.save(pipeline));
    }

    @Override
    @Transactional
    public void deletePipeline(Long id, String username) {
        Pipeline pipeline = pipelineRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found"));
        checkOwnership(pipeline, username);
        pipelineRepository.delete(pipeline);
    }

    @Override
    @Transactional(readOnly = true)
    public PipelineResponse getPipelineById(Long id, String username) {
        Pipeline pipeline = pipelineRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found"));
        checkOwnership(pipeline, username);
        return toResponse(pipeline);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PipelineResponse> getMyPipelines(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return pipelineRepository.findByCreatedBy(user)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PipelineResponse> getAllPipelines() {
        return pipelineRepository.findAll()
            .stream().map(this::toResponse).collect(Collectors.toList());
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
            p.getProviderUrl(),
            p.getVersion(),
            p.getCreatedAt(),
            p.getInputFormat().name(),
            p.getOutputFormat().name(),
            p.getStatus().name(),
            p.getCreatedBy().getUsername(),
            p.getProvider().getId(),
            p.getProvider().getName()
        );
    }
}
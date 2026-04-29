package com.miniESB.service.impl;
import com.miniESB.domain.entity.Provider;
import com.miniESB.dto.provider.CreateProviderRequest;
import com.miniESB.dto.provider.ProviderResponse;
import com.miniESB.dto.provider.UpdateProviderRequest;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.ProviderRepository;
import com.miniESB.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderRepository providerRepository;

    @Override
    @Transactional
    public ProviderResponse createProvider(CreateProviderRequest request) {
        Provider provider = Provider.builder()
            .name(request.name())
            .endpoint(request.endpoint())
            .protocol(request.protocol())
            .timeout(request.timeout())
            .build();
        return toResponse(providerRepository.save(provider));
    }

    @Override
    @Transactional
    public ProviderResponse updateProvider(Long id, UpdateProviderRequest request) {
        Provider provider = providerRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (request.name() != null) provider.setName(request.name());
        if (request.endpoint() != null) provider.setEndpoint(request.endpoint());
        if (request.protocol() != null) provider.setProtocol(request.protocol());
        if (request.timeout() != null) provider.setTimeout(request.timeout());

        return toResponse(providerRepository.save(provider));
    }

    @Override
    @Transactional
    public void deleteProvider(Long id) {
        if (!providerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Provider not found");
        }
        providerRepository.deleteById(id);
    }

    @Override
    public ProviderResponse getProviderById(Long id) {
        return toResponse(
            providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"))
        );
    }

    @Override
    public List<ProviderResponse> getAllProviders() {
        return providerRepository.findAll()
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private ProviderResponse toResponse(Provider provider) {
        return new ProviderResponse(
            provider.getId(),
            provider.getName(),
            provider.getEndpoint(),
            provider.getProtocol(),
            provider.getTimeout()
        );
    }
}
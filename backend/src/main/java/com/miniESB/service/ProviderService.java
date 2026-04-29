package com.miniESB.service;

import com.miniESB.dto.provider.CreateProviderRequest;
import com.miniESB.dto.provider.ProviderResponse;
import com.miniESB.dto.provider.UpdateProviderRequest;

import java.util.List;

public interface ProviderService {
    ProviderResponse createProvider(CreateProviderRequest request);
    ProviderResponse updateProvider(Long id, UpdateProviderRequest request);
    void deleteProvider(Long id);
    ProviderResponse getProviderById(Long id);
    List<ProviderResponse> getAllProviders();
}

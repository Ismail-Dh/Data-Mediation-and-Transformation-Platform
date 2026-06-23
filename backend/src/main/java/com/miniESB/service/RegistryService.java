package com.miniESB.service;

import com.miniESB.dto.registry.*;
import java.util.List;

public interface RegistryService {
    RegistryResponse create(CreateRegistryRequest request);
    RegistryResponse update(Long id, UpdateRegistryRequest request);
    RegistryResponse getById(Long id);
    List<RegistryResponse> getAll();
    void delete(Long id);
    String decryptPassword(Long id); 
}
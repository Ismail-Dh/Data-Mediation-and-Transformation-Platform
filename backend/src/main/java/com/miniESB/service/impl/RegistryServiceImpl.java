package com.miniESB.service.impl;

import com.miniESB.domain.entity.Registry;
import com.miniESB.dto.registry.*;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.RegistryRepository;
import com.miniESB.service.AesEncryptionService;
import com.miniESB.service.RegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
public class RegistryServiceImpl implements RegistryService {

    private final RegistryRepository    registryRepository;
    private final AesEncryptionService  aesEncryptionService;

    @Override
    @Transactional
    public RegistryResponse create(CreateRegistryRequest request) {
        Registry registry = Registry.builder()
                .name(request.name())
                .url(request.url())
                .username(request.username())
                .encryptedPassword(aesEncryptionService.encrypt(request.password()))
                .build();
        Registry saved = registryRepository.save(registry);
        log.info("Registry created: id={} name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RegistryResponse update(Long id, UpdateRegistryRequest request) {
        Registry registry = findOrThrow(id);
        if (request.name()     != null) registry.setName(request.name());
        if (request.url()      != null) registry.setUrl(request.url());
        if (request.username() != null) registry.setUsername(request.username());
        if (request.password() != null) {
            registry.setEncryptedPassword(
                    aesEncryptionService.encrypt(request.password()));
        }
        log.info("Registry updated: id={}", id);
        return toResponse(registryRepository.save(registry));
    }

    @Override
    public RegistryResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    public List<RegistryResponse> getAll() {
        return registryRepository.findAll()
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!registryRepository.existsById(id))
            throw new ResourceNotFoundException("Registry not found: " + id);
        registryRepository.deleteById(id);
        log.info("Registry deleted: id={}", id);
    }

    @Override
    public String decryptPassword(Long id) {
        Registry registry = findOrThrow(id);
        return aesEncryptionService.decrypt(registry.getEncryptedPassword());
    }

    private Registry findOrThrow(Long id) {
        return registryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registry not found: " + id));
    }

    private RegistryResponse toResponse(Registry r) {
        return new RegistryResponse(r.getId(), r.getName(), r.getUrl(), r.getUsername());
    }
}
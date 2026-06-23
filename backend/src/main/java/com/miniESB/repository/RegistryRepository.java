package com.miniESB.repository;

import com.miniESB.domain.entity.Registry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RegistryRepository extends JpaRepository<Registry, Long> {
    Optional<Registry> findByName(String name);
    boolean existsByName(String name);
}
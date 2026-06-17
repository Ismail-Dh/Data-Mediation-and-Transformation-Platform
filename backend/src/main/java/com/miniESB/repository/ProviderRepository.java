package com.miniESB.repository;


import com.miniESB.domain.entity.Provider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = true)

public interface ProviderRepository extends JpaRepository<Provider, Long> {}

package com.miniESB.repository;

import com.miniESB.domain.entity.ProviderResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderResponseRepository extends JpaRepository<ProviderResponse, Long> {

    /** Retrouve la réponse provider liée à un payload donné. */
    Optional<ProviderResponse> findByPayloadId(Long payloadId);
}
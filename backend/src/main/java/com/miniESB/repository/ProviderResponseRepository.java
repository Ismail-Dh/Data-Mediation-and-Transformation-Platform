package com.miniESB.repository;

import com.miniESB.domain.entity.ProviderResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderResponseRepository extends JpaRepository<ProviderResponse, Long> {

    /**
     * Toutes les réponses reçues pour un payload — une par provider appelé.
     * Remplace findByPayloadId (singulier) suite à la migration multi-provider (T5).
     */
    List<ProviderResponse> findAllByPayloadId(Long payloadId);

    /** Retrouve la réponse d'un provider précis pour un payload précis (pour re-dispatch / mise à jour). */
    Optional<ProviderResponse> findByPayloadIdAndProviderId(Long payloadId, Long providerId);
}
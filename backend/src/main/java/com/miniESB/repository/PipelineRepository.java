package com.miniESB.repository;

import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.entity.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import java.util.List;
@Repository
@ConditionalOnProperty(name = "audit.enabled", havingValue = "true", matchIfMissing = true)

public interface PipelineRepository extends JpaRepository<Pipeline, Long> {
    List<Pipeline> findByCreatedBy(User user);

    /**
     * Fetch le pipeline avec ses providers ET la méthode HTTP par lien
     * (association PipelineProvider). Remplace l'ancien "LEFT JOIN FETCH p.providers"
     * qui n'existe plus depuis le passage à l'association dédiée PipelineProvider.
     */
    @Query("SELECT DISTINCT p FROM Pipeline p " +
            "LEFT JOIN FETCH p.pipelineProviders pp " +
            "LEFT JOIN FETCH pp.provider " +
            "WHERE p.id = :id")
    Optional<Pipeline> findByIdWithProviders(@Param("id") Long id);
}
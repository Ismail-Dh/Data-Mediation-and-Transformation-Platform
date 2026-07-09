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
    @Query("SELECT p FROM Pipeline p LEFT JOIN FETCH p.providers WHERE p.id = :id")
    Optional<Pipeline> findByIdWithProviders(@Param("id") Long id);
}

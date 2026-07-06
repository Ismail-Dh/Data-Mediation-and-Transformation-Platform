package com.miniESB.repository;

import com.miniESB.domain.entity.ResponseMappingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResponseMappingRuleRepository extends JpaRepository<ResponseMappingRule, Long> {

    /** Règles actives d'un pipeline, tous providers confondus (provider IS NULL ou spécifique). */
    List<ResponseMappingRule> findByPipelineIdAndActiveTrue(Long pipelineId);

    /** Toutes les règles d'un pipeline (actives et inactives) — utilisé par le CRUD. */
    List<ResponseMappingRule> findByPipelineId(Long pipelineId);

    /**
     * Règles actives applicables à un provider donné : celles ciblant
     * spécifiquement ce provider, plus celles génériques (provider IS NULL).
     */
    @Query("""
            SELECT r FROM ResponseMappingRule r
            WHERE r.pipeline.id = :pipelineId
              AND r.active = true
              AND (r.provider.id = :providerId OR r.provider IS NULL)
            """)
    List<ResponseMappingRule> findApplicableRules(@Param("pipelineId") Long pipelineId,
                                                  @Param("providerId") Long providerId);
}
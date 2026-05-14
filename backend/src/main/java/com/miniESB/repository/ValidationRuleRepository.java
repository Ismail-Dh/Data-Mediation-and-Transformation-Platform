package com.miniESB.repository;

import com.miniESB.domain.entity.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationRuleRepository extends JpaRepository<ValidationRule, Long> {

    // ── Global rules (Admin-managed) ──────────────────────────────────────────

    /** All global rules — Admin view (active + inactive). */
    List<ValidationRule> findAllByGlobalTrue();

    /** Active global rules only — Developer read-only view. */
    List<ValidationRule> findAllByGlobalTrueAndActiveTrue();

    /**
     * Active global rules (pipeline IS NULL) — Correct developer view.
     * Excludes copies attached to pipelines.
     */
    @Query("SELECT vr FROM ValidationRule vr WHERE vr.global = true AND vr.active = true AND vr.pipeline IS NULL")
    List<ValidationRule> findAllActiveGlobalRulesOnly();

    /**
     * Checks whether a given global rule is referenced by at least one pipeline.
     * Used to block hard-delete when the rule is still in use (→ 409).
     */
    @Query("SELECT COUNT(vr) > 0 FROM ValidationRule vr WHERE vr.id = :id AND vr.global = true AND vr.pipeline IS NOT NULL")
    boolean isGlobalRuleUsedByPipeline(@Param("id") Long id);

    // ── Pipeline-scoped private rules (Developer-managed) ────────────────────

    /** All rules (active + inactive) attached to a specific pipeline. */
    List<ValidationRule> findAllByPipelineId(Long pipelineId);

    /** Active private rules attached to a specific pipeline (used at runtime). */
    List<ValidationRule> findAllByPipelineIdAndActiveTrue(Long pipelineId);

    /** Check for duplicate (fieldName + ruleType) on the same pipeline. */
    boolean existsByPipelineIdAndFieldNameAndRuleType(
            Long pipelineId,
            String fieldName,
            com.miniESB.domain.enums.RuleType ruleType);
}
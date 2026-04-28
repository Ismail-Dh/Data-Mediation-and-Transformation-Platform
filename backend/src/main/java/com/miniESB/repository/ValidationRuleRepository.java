package com.miniESB.repository;


import com.miniESB.domain.entity.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ValidationRuleRepository extends JpaRepository<ValidationRule, Long> {

    /** All global rules (admin-managed, not attached to a specific pipeline). */
    List<ValidationRule> findAllByGlobalTrue();

    /** All active global rules (exposed to Developers). */
    List<ValidationRule> findAllByGlobalTrueAndActiveTrue();

    /**
     * Checks whether a given global rule is referenced by at least one pipeline.
     * Used to block deletion of a rule that is still in use (→ 409).
     */
    @Query("SELECT COUNT(vr) > 0 FROM ValidationRule vr WHERE vr.id = :id AND vr.global = true AND vr.pipeline IS NOT NULL")
    boolean isGlobalRuleUsedByPipeline(@Param("id") Long id);
}
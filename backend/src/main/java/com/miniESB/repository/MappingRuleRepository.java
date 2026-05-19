package com.miniESB.repository;

import com.miniESB.domain.entity.MappingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MappingRuleRepository extends JpaRepository<MappingRule, Long> {

    // Returns only active rules for a given pipeline
    List<MappingRule> findByPipelineIdAndActiveTrue(Long pipelineId);

    // Returns all rules (active and inactive)
    List<MappingRule> findByPipelineId(Long pipelineId);
}
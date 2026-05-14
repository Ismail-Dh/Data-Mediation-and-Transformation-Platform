package com.miniESB.service;

import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleRequest;
import com.miniESB.dto.pipelineValidationRule.PipelineValidationRuleResponse;

import java.util.List;

/**
 * Developer-facing service to manage validation rules scoped to a pipeline.
 *
 * Two types of rules:
 *  - Attached global rule: copied from Admin's catalogue — global=true, read-only content
 *  - Private rule: created by Developer for this pipeline — global=false, fully editable
 */
public interface PipelineValidationRuleService {

    /** Attach a global rule OR create a private rule on the pipeline. */
    PipelineValidationRuleResponse addRule(Long pipelineId, PipelineValidationRuleRequest request);

    /** List all rules (active + inactive) of a pipeline. */
    List<PipelineValidationRuleResponse> getRules(Long pipelineId);

    /** Update a private rule (global rules cannot be edited here). */
    PipelineValidationRuleResponse updateRule(Long pipelineId, Long ruleId, PipelineValidationRuleRequest request);

    /** Toggle active/inactive on any rule attached to the pipeline. */
    PipelineValidationRuleResponse toggleActive(Long pipelineId, Long ruleId);

    /** Remove a rule from the pipeline (detach global or delete private). */
    void deleteRule(Long pipelineId, Long ruleId);
}

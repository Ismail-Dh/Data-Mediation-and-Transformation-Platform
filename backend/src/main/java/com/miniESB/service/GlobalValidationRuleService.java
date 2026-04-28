package com.miniESB.service;




import com.miniESB.dto.globalValidationRule.GlobalValidationRuleRequestDTO;
import com.miniESB.dto.globalValidationRule.GlobalValidationRuleResponseDTO;

import java.util.List;

/**
 * Service contract for ADMIN-only management of global validation rules.
 *
 * <p>Global rules are reusable across all pipelines and are defined by the Admin.
 * Developers can read active global rules; only Admins can create, update, or delete them.</p>
 */
public interface GlobalValidationRuleService {

    /**
     * Creates a new global validation rule.
     *
     * @param request the rule definition
     * @return the persisted rule as a response DTO
     */
    GlobalValidationRuleResponseDTO createRule(GlobalValidationRuleRequestDTO request);

    /**
     * Retrieves all global validation rules (active and inactive).
     * Intended for Admin use.
     *
     * @return list of all global rules
     */
    List<GlobalValidationRuleResponseDTO> getAllRules();

    /**
     * Retrieves all <em>active</em> global validation rules.
     * Intended for Developer use — inactive rules are filtered out.
     *
     * @return list of active global rules
     */
    List<GlobalValidationRuleResponseDTO> getActiveRules();

    /**
     * Retrieves a single global validation rule by its identifier.
     *
     * @param id the rule identifier
     * @return the matching rule
     * @throws com.miniESB.exception.ResourceNotFoundException if no rule with that id exists
     */
    GlobalValidationRuleResponseDTO getRuleById(Long id);

    /**
     * Fully updates a global validation rule.
     *
     * @param id      the rule identifier
     * @param request the updated rule definition
     * @return the updated rule as a response DTO
     * @throws com.miniESB.exception.ResourceNotFoundException if no rule with that id exists
     */
    GlobalValidationRuleResponseDTO updateRule(Long id, GlobalValidationRuleRequestDTO request);

    /**
     * Soft-disables a global validation rule by setting {@code active = false}.
     * The rule remains in the database and continues to appear in Admin listings.
     *
     * @param id the rule identifier
     * @throws com.miniESB.exception.ResourceNotFoundException if no rule with that id exists
     */
    void deactivateRule(Long id);

    /**
     * Permanently deletes a global validation rule.
     *
     * <p>If the rule is currently referenced by at least one pipeline the deletion
     * is rejected and a {@link com.miniESB.exception.RuleInUseException} (HTTP 409)
     * is thrown instead.</p>
     *
     * @param id the rule identifier
     * @throws com.miniESB.exception.ResourceNotFoundException if no rule with that id exists
     * @throws com.miniESB.exception.RuleInUseException        if the rule is used by a pipeline
     */
    void deleteRule(Long id);
}
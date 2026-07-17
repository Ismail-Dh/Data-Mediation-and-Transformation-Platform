package com.miniESB.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.engine.validation.JsonNodeFieldResolver;
import com.miniESB.engine.validation.ValidationEngine;
import com.miniESB.engine.validation.ValidationRuleData;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Niveau-2 business validator.
 *
 * <p>Applique une liste de {@link ValidationRule} (actives, scope pipeline ou
 * globales) contre un payload JSON parsé. Chaque règle cible un champ précis
 * (notation pointée, ex. "customer.email").</p>
 *
 * <p>Appelé par {@code PayloadServiceImpl} APRÈS que la validation structurelle
 * (niveau 1) ait réussi. Lance {@link PayloadValidationException} si une
 * violation est trouvée.</p>
 *
 * <p><strong>Depuis le refactoring</strong>, la logique de dispatch par
 * {@code RuleType} et l'implémentation de chaque règle ne sont plus ici :
 * elles vivent dans {@code com.miniESB.engine.validation} (pattern Strategy),
 * et sont partagées avec {@code EngineProcessService} (mode fichier), qui
 * dupliquait auparavant ce même code pour opérer sur une {@code Map} au lieu
 * d'un {@code JsonNode}. Cette classe ne fait plus qu'adapter le JSON en
 * entrée et convertir les entités {@link ValidationRule} en {@link ValidationRuleData}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessValidatorService {

    private final ObjectMapper objectMapper;
    private final ValidationEngine validationEngine;

    /**
     * Valide {@code rawContent} contre chaque règle active de {@code rules}.
     *
     * @param rawContent JSON string (déjà confirmé parseable par le niveau 1)
     * @param rules      liste des ValidationRule actives pour ce pipeline
     * @throws PayloadValidationException si une ou plusieurs règles sont violées
     */
    public void validate(String rawContent, List<ValidationRule> rules) {
        if (rules == null || rules.isEmpty()) return;

        JsonNode root;
        try {
            root = objectMapper.readTree(rawContent);
        } catch (Exception e) {
            // Ne devrait pas arriver — le niveau 1 a déjà vérifié que le JSON est parseable
            throw new PayloadValidationException(
                    List.of(new FieldViolation("$", "INVALID_JSON", "Payload is not valid JSON")));
        }

        List<ValidationRuleData> ruleData = rules.stream()
                .map(r -> new ValidationRuleData(r.getFieldName(), r.getRuleType(), r.getPattern()))
                .toList();

        List<FieldViolation> violations = validationEngine.validate(ruleData, new JsonNodeFieldResolver(root));

        if (!violations.isEmpty()) {
            log.warn("Business validation failed — {} violation(s)", violations.size());
            throw new PayloadValidationException(violations);
        }
    }
}

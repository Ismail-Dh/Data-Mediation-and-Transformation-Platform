package com.miniESB.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.ValidationRule;
import com.miniESB.domain.enums.RuleType;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Niveau-2 business validator.
 *
 * Applies a list of {@link ValidationRule} (active, pipeline-scoped or global)
 * against a parsed JSON payload. Each rule targets a specific field by path
 * (dot-notation supported, e.g. "customer.email").
 *
 * Called by PayloadServiceImpl AFTER structural validation (niveau 1) passes.
 * Throws {@link PayloadValidationException} if any violation is found.
 *
 * All regex patterns (including EMAIL, PHONE, PASSWORD) are read from the rule's
 * own {@code pattern} field. No static patterns are compiled here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessValidatorService {

    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates {@code rawContent} against every active rule in {@code rules}.
     *
     * @param rawContent JSON string (already confirmed parseable by niveau-1)
     * @param rules      active ValidationRule list for this pipeline
     * @throws PayloadValidationException if one or more rules are violated
     */
    public void validate(String rawContent, List<ValidationRule> rules) {
        if (rules == null || rules.isEmpty()) return;

        JsonNode root;
        try {
            root = objectMapper.readTree(rawContent);
        } catch (Exception e) {
            // Should not happen — niveau-1 already verified JSON is parseable
            throw new PayloadValidationException(
                    List.of(new FieldViolation("$", "INVALID_JSON", "Payload is not valid JSON")));
        }

        List<FieldViolation> violations = new ArrayList<>();

        for (ValidationRule rule : rules) {
            applyRule(root, rule, violations);
        }

        if (!violations.isEmpty()) {
            log.warn("Business validation failed — {} violation(s)", violations.size());
            throw new PayloadValidationException(violations);
        }
    }

    // ── Rule dispatcher ───────────────────────────────────────────────────────

    private void applyRule(JsonNode root, ValidationRule rule, List<FieldViolation> violations) {
        String path  = rule.getFieldName();
        JsonNode node = resolvePath(root, path);

        // NOT_NULL is special: it checks presence/null regardless of type
        if (rule.getRuleType() == RuleType.NOT_NULL) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                violations.add(new FieldViolation(path, "INVALID_FORMAT",
                        "Field '" + path + "' must not be null or missing (NOT_NULL rule)"));
            }
            return;
        }

        // For all other rules: missing/null field → skip (niveau-1 handles presence)
        if (node == null || node.isMissingNode() || node.isNull()) return;

        String value = node.isTextual() ? node.textValue() : node.toString();

        switch (rule.getRuleType()) {
            case TYPE_NUMBER -> {
                // If pattern is provided, use it for custom number validation
                if (rule.getPattern() != null && !rule.getPattern().isBlank()) {
                    checkDynamicPattern(path, value, rule.getPattern(), "TYPE_NUMBER", violations);
                } else {
                    checkTypeNumber(path, value, violations);
                }
            }
            case TYPE_DATE -> {
                // If pattern is provided, use it for custom date validation
                if (rule.getPattern() != null && !rule.getPattern().isBlank()) {
                    checkDynamicPattern(path, value, rule.getPattern(), "TYPE_DATE", violations);
                } else {
                    checkTypeDate(path, value, violations);
                }
            }
            case REGEX_EMAIL    -> checkDynamicPattern(path, value, rule.getPattern(), "REGEX_EMAIL",    violations);
            case REGEX_PHONE    -> checkDynamicPattern(path, value, rule.getPattern(), "REGEX_PHONE",    violations);
            case REGEX_PASSWORD -> checkDynamicPattern(path, value, rule.getPattern(), "REGEX_PASSWORD", violations);
            case REGEX          -> checkDynamicPattern(path, value, rule.getPattern(), "REGEX",          violations);
            case MIN_MAX_LENGTH -> checkMinMaxLength(path, value, rule.getPattern(), violations);
            default             -> log.warn("Unknown RuleType: {}", rule.getRuleType());
        }
    }

    // ── Individual validators ─────────────────────────────────────────────────

    private void checkTypeNumber(String path, String value, List<FieldViolation> violations) {
        try {
            Double.parseDouble(value);
        } catch (NumberFormatException e) {
            violations.add(new FieldViolation(path, "INVALID_FORMAT",
                    "Field '" + path + "' must be a valid number (got: " + value + ")"));
        }
    }

    private void checkTypeDate(String path, String value, List<FieldViolation> violations) {
        try {
            LocalDate.parse(value);  // expects ISO-8601: YYYY-MM-DD
        } catch (DateTimeParseException e) {
            violations.add(new FieldViolation(path, "INVALID_FORMAT",
                    "Field '" + path + "' must be a valid date in ISO-8601 format YYYY-MM-DD (got: " + value + ")"));
        }
    }

    /**
     * Generic dynamic-pattern check.
     * The regex comes from the rule's own {@code pattern} field (stored in DB).
     * If the pattern is null or blank, the rule is skipped with a warning.
     */
    private void checkDynamicPattern(String path, String value, String patternStr,
                                     String ruleName, List<FieldViolation> violations) {
        if (patternStr == null || patternStr.isBlank()) {
            log.warn("{} rule for field '{}' has no pattern defined — skipping", ruleName, path);
            return;
        }
        try {
            Pattern compiled = Pattern.compile(patternStr);
            if (!compiled.matcher(value).matches()) {
                violations.add(new FieldViolation(path, "INVALID_FORMAT",
                        "Field '" + path + "' does not match " + ruleName + " format (got: " + value + ")"));
            }
        } catch (PatternSyntaxException e) {
            log.warn("{} rule for field '{}' has invalid regex pattern '{}': {}", ruleName, path, patternStr, e.getMessage());
        }
    }

    private void checkMinMaxLength(String path, String value, String patternParam,
                                   List<FieldViolation> violations) {
        // patternParam format: "min,max" e.g. "3,30"
        if (patternParam == null || !patternParam.contains(",")) {
            log.warn("MIN_MAX_LENGTH rule for field '{}' has invalid pattern: '{}'", path, patternParam);
            return;
        }
        try {
            String[] parts = patternParam.split(",", 2);
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            int len = value.length();
            if (len < min || len > max) {
                violations.add(new FieldViolation(path, "INVALID_FORMAT",
                        "Field '" + path + "' length must be between " + min + " and " + max
                                + " (got: " + len + ")"));
            }
        } catch (NumberFormatException e) {
            log.warn("Cannot parse MIN_MAX_LENGTH pattern '{}' for field '{}'", patternParam, path);
        }
    }

    // ── Path resolver (dot-notation: "customer.email") ────────────────────────

    private JsonNode resolvePath(JsonNode root, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isMissingNode()) return null;
            current = current.get(part);
        }
        return current;
    }
}
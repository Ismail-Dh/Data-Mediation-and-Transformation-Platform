package com.miniESB.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.PipelineField;
import com.miniESB.domain.enums.FieldType;
import com.miniESB.exception.FieldViolation;
import com.miniESB.exception.PayloadValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StructuralValidatorService {

    private final ObjectMapper objectMapper;

    /**
     * Valide le rawContent JSON contre la liste des PipelineField.
     * Lance PayloadValidationException si des violations sont trouvées.
     */
    public void validate(String rawContent, List<PipelineField> fields) {
        List<FieldViolation> violations = new ArrayList<>();

        // 1 — JSON 
        JsonNode root;
        try {
            root = objectMapper.readTree(rawContent);
        } catch (Exception e) {
            violations.add(new FieldViolation("$", "INVALID_JSON", "Payload is not valid JSON"));
            throw new PayloadValidationException(violations);
        }

        // 2 — Vérifier chaque champ défini dans le schéma
        for (PipelineField field : fields) {
            validateField(root, field, violations);
        }

        // 3 — Lancer l'exception si violations
        if (!violations.isEmpty()) {
            log.warn("Structural validation failed with {} violation(s)", violations.size());
            throw new PayloadValidationException(violations);
        }
    }

    // -------------------------------------------------------------------------
    // LOGIQUE PAR CHAMP
    // -------------------------------------------------------------------------

    private void validateField(JsonNode root, PipelineField field, List<FieldViolation> violations) {
        String path = field.getFieldPath();

        // cas tableau : "items[].qty"
        if (path.contains("[].")) {
            validateArrayField(root, field, violations);
            return;
        }

        // cas normal : "orderId" ou "customer.email"
        JsonNode node = resolvePath(root, path);

        if (node == null || node.isMissingNode()) {
            if (field.isRequired()) {
                violations.add(new FieldViolation(path, "MISSING_FIELD",
                    "Required field '" + path + "' is missing"));
            }
            return;
        }

        if (node.isNull()) {
            if (!field.isNullable()) {
                violations.add(new FieldViolation(path, "NULL_NOT_ALLOWED",
                    "Field '" + path + "' cannot be null"));
            }
            return;
        }

        // vérifier le type
        checkType(node, field, violations);
    }

    private void validateArrayField(JsonNode root, PipelineField field, List<FieldViolation> violations) {
        // "items[].qty" → arrayPath = "items", subPath = "qty"
        String path = field.getFieldPath();
        String arrayPath = path.substring(0, path.indexOf("[]."));
        String subPath = path.substring(path.indexOf("[].") + 3);

        JsonNode arrayNode = resolvePath(root, arrayPath);

        if (arrayNode == null || arrayNode.isMissingNode()) {
            if (field.isRequired()) {
                violations.add(new FieldViolation(arrayPath, "MISSING_FIELD",
                    "Required array '" + arrayPath + "' is missing"));
            }
            return;
        }

        if (!arrayNode.isArray()) {
            violations.add(new FieldViolation(arrayPath, "TYPE_MISMATCH",
                "Field '" + arrayPath + "' expected ARRAY"));
            return;
        }

        // vérifier chaque élément du tableau
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode item = arrayNode.get(i);
            JsonNode subNode = resolvePath(item, subPath);
            String fullPath = arrayPath + "[" + i + "]." + subPath;

            if (subNode == null || subNode.isMissingNode()) {
                if (field.isRequired()) {
                    violations.add(new FieldViolation(fullPath, "MISSING_FIELD",
                        "Required field '" + fullPath + "' is missing"));
                }
                continue;
            }

            if (subNode.isNull()) {
                if (!field.isNullable()) {
                    violations.add(new FieldViolation(fullPath, "NULL_NOT_ALLOWED",
                        "Field '" + fullPath + "' cannot be null"));
                }
                continue;
            }

            checkType(subNode, field, violations, fullPath);
        }
    }

    // -------------------------------------------------------------------------
    // VÉRIFICATION DE TYPE
    // -------------------------------------------------------------------------

    private void checkType(JsonNode node, PipelineField field, List<FieldViolation> violations) {
        checkType(node, field, violations, field.getFieldPath());
    }

    private void checkType(JsonNode node, PipelineField field, List<FieldViolation> violations, String displayPath) {
        FieldType expected = field.getFieldType();
        boolean typeOk = switch (expected) {
            case STRING  -> node.isTextual();
            case INTEGER -> node.isInt() || node.isLong() || node.isBigInteger();
            case NUMBER  -> node.isNumber();
            case BOOLEAN -> node.isBoolean();
            case OBJECT  -> node.isObject();
            case ARRAY   -> node.isArray();
            case NULL    -> node.isNull();
        };

        if (!typeOk) {
            violations.add(new FieldViolation(displayPath, "TYPE_MISMATCH",
                "Field '" + displayPath + "' expected " + expected
                + " but got " + resolveActualType(node)));
        }
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    /**
     * Résout un path pointé ex: "customer.email" → node
     */
    private JsonNode resolvePath(JsonNode root, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isMissingNode()) return null;
            current = current.get(part);
        }
        return current;
    }

    private String resolveActualType(JsonNode node) {
        if (node.isTextual()) {
            return "STRING";
        }
        if (node.isIntegralNumber()) {
            return "INTEGER";
        }
        if (node.isNumber()) {
            return "NUMBER";
        }
        if (node.isBoolean()) {
            return "BOOLEAN";
        }
        if (node.isObject()) {
            return "OBJECT";
        }
        if (node.isArray()) {
            return "ARRAY";
        }
        if (node.isNull()) {
            return "NULL";
        }
        return "UNKNOWN";
    }
}
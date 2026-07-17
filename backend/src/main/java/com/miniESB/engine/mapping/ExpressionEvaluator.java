package com.miniESB.engine.mapping;

import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.miniESB.engine.mapping.PayloadPathUtils.getNestedValue;

/**
 * Interprète les trois familles d'expressions utilisées par les règles
 * {@code CALCULATED_FIELD} : arithmétique ("{prix} * (1+{tva})"), conditionnelle
 * ("IF:field:op:val:ifTrue:ifFalse") et agrégation ("SUM:items[].prix").
 *
 * <p>Extrait de {@code MappingServiceImpl}/{@code EngineProcessService} pour être
 * partagé entre les deux, et pour isoler cette responsabilité (interprétation
 * d'expressions) du reste de l'orchestration du mapping (SRP).</p>
 */
public final class ExpressionEvaluator {

    private ExpressionEvaluator() {
    }

    public static boolean isAggregation(String expr) {
        String up = expr.toUpperCase();
        return up.startsWith("SUM:") || up.startsWith("AVG:") || up.startsWith("COUNT:")
                || up.startsWith("MIN:") || up.startsWith("MAX:");
    }

    public static boolean isConditional(String expr) {
        return expr.toUpperCase().startsWith("IF:");
    }

    public static Object evaluateArithmetic(String expr, Map<String, Object> input) {
        String resolved = expr;
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(expr);

        while (m.find()) {
            String fieldName = m.group(1).trim();
            Object val = getNestedValue(input, fieldName);
            if (val == null)
                throw new IllegalArgumentException("ARITHMETIC — field '" + fieldName + "' not found in input");
            if (!(val instanceof Number))
                throw new IllegalArgumentException("ARITHMETIC — field '" + fieldName + "' is not a number (got: " + val + ")");
            resolved = resolved.replace(m.group(0), val.toString());
        }

        try {
            return new ExpressionBuilder(resolved).build().evaluate();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("ARITHMETIC — invalid expression '" + resolved + "': " + e.getMessage());
        }
    }

    public static Object evaluateConditional(String expr, Map<String, Object> input) {
        String[] parts = expr.split(":", 6);
        if (parts.length < 6)
            throw new IllegalArgumentException("IF format: IF:field:op:compareValue:ifTrue:ifFalse — got: " + expr);

        String fieldName = parts[1].trim();
        String op = parts[2].trim().toLowerCase();
        String compareValue = parts[3].trim();
        String ifTrue = parts[4].trim();
        String ifFalse = parts[5].trim();

        Object fieldVal = getNestedValue(input, fieldName);
        if (fieldVal == null)
            throw new IllegalArgumentException("IF — field '" + fieldName + "' not found in input");

        return evaluateCondition(fieldVal, op, compareValue) ? ifTrue : ifFalse;
    }

    private static boolean evaluateCondition(Object fieldVal, String op, String compareValue) {
        String strVal = fieldVal.toString().trim();

        return switch (op) {
            case "eq" -> strVal.equalsIgnoreCase(compareValue);
            case "ne" -> !strVal.equalsIgnoreCase(compareValue);
            case "contains" -> strVal.toLowerCase().contains(compareValue.toLowerCase());
            case "gt", "lt", "gte", "lte" -> {
                double fieldNum = Double.parseDouble(strVal);
                double compareNum = Double.parseDouble(compareValue);
                yield switch (op) {
                    case "gt" -> fieldNum > compareNum;
                    case "lt" -> fieldNum < compareNum;
                    case "gte" -> fieldNum >= compareNum;
                    case "lte" -> fieldNum <= compareNum;
                    default -> false;
                };
            }
            default -> throw new IllegalArgumentException("Unknown IF operator: " + op);
        };
    }

    @SuppressWarnings("unchecked")
    public static Object evaluateAggregation(String expr, Map<String, Object> input) {
        int sep = expr.indexOf(":");
        String op = expr.substring(0, sep).toUpperCase();
        String path = expr.substring(sep + 1).trim();

        String arrayPath;
        String subField;

        if (path.contains("[].")) {
            int idx = path.indexOf("[].");
            arrayPath = path.substring(0, idx);
            subField = path.substring(idx + 3);
        } else if (path.endsWith("[]")) {
            arrayPath = path.substring(0, path.length() - 2);
            subField = null;
        } else {
            throw new IllegalArgumentException("Aggregation path must contain '[].' or end with '[]' — got: " + path);
        }

        Object arrayObj = getNestedValue(input, arrayPath);
        if (!(arrayObj instanceof List))
            throw new IllegalArgumentException("Aggregation — '" + arrayPath + "' is not an array");

        List<Object> items = (List<Object>) arrayObj;

        if (op.equals("COUNT")) return items.size();

        if (subField == null)
            throw new IllegalArgumentException(op + " requires a sub-field (e.g. items[].prix)");

        final String sf = subField;
        List<Double> values = items.stream()
                .filter(item -> item instanceof Map)
                .map(item -> {
                    Object v = getNestedValue((Map<String, Object>) item, sf);
                    if (v == null)
                        throw new IllegalArgumentException(op + " — sub-field '" + sf + "' not found in one of the items");
                    if (!(v instanceof Number))
                        throw new IllegalArgumentException(op + " — sub-field '" + sf + "' is not a number");
                    return ((Number) v).doubleValue();
                })
                .toList();

        if (values.isEmpty())
            throw new IllegalArgumentException(op + " — array is empty");

        return switch (op) {
            case "SUM" -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "AVG" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "MIN" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "MAX" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            default -> throw new IllegalArgumentException("Unknown aggregation: " + op);
        };
    }
}

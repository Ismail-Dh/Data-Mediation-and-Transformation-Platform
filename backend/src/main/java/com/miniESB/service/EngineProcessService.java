package com.miniESB.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "true")
public class EngineProcessService {

    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;
    private final EngineResponseMappingHelper responseMappingHelper;


    @Value("${engine.rules-file:/app/rules.json}")
    private String rulesFilePath;

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRules() throws Exception {
        return objectMapper.readValue(new File(rulesFilePath),
                new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> process(String rawContent) throws Exception {
        Map<String, Object> rules = loadRules();
        Map<String, Object> input = objectMapper.readValue(rawContent,
                new TypeReference<LinkedHashMap<String, Object>>() {});

        // 1 — validation
        List<Map<String, Object>> validationFields =
                (List<Map<String, Object>>) rules.getOrDefault("validationFields", List.of());
        List<Map<String, Object>> validationRules =
                (List<Map<String, Object>>) rules.getOrDefault("validationRules", List.of());

        validatePayload(input, validationFields, validationRules);

        // 2 — mapping
        List<Map<String, Object>> mappingRules =
                (List<Map<String, Object>>) rules.getOrDefault("mappingRules", List.of());
        Map<String, Object> mapped = applyMapping(input, mappingRules);

        // 3 — NOUVEAU : dispatch vers les providers
        List<Map<String, Object>> providers =
                (List<Map<String, Object>>) rules.getOrDefault("providers", List.of());
        String outputFormat = (String) rules.getOrDefault("outputFormat", "JSON");
        List<Map<String, Object>> responseMappingRules =
                (List<Map<String, Object>>) rules.getOrDefault("responseMappingRules", List.of());

        List<Map<String, Object>> dispatchResults = dispatchToProviders(mapped, providers, outputFormat, responseMappingRules);
        boolean anySuccess = dispatchResults.stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.get("success")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mappedPayload", mapped);
        result.put("providerResults", dispatchResults);
        result.put("overallSuccess", anySuccess);
        return result;
    }

    // ── Dispatch providers ────────────────────────────────────────────────────

    private List<Map<String, Object>> dispatchToProviders(Map<String, Object> mappedPayload,
                                                          List<Map<String, Object>> providers,
                                                          String outputFormat, List<Map<String, Object>> responseMappingRules) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (providers == null || providers.isEmpty()) {
            log.warn("No providers configured in rules.json — dispatch skipped");
            return results;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(mappedPayload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize mapped payload: " + e.getMessage(), e);
        }

        for (Map<String, Object> provider : providers) {
            results.add(callProvider(provider, body, outputFormat,responseMappingRules));
        }
        return results;
    }

    private Map<String, Object> callProvider(Map<String, Object> provider, String body, String outputFormat, List<Map<String, Object>> responseMappingRules) {
        String name     = (String) provider.get("name");
        Number providerIdN = (Number) provider.get("id");
        Long providerId = providerIdN != null ? providerIdN.longValue() : null;
        String endpoint = (String) provider.get("endpoint");
        Number timeoutN = (Number) provider.get("timeout");
        int timeoutSec  = timeoutN != null ? timeoutN.intValue() : 30;

        // Méthode HTTP choisie pour ce provider (exportée dans rules.json par
        // DockerImageGeneratorService depuis PipelineProvider.httpMethod).
        // Fallback défensif sur POST si absente (rules.json généré avant ce changement).
        String httpMethodStr = (String) provider.getOrDefault("httpMethod", "POST");
        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(httpMethodStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Provider '{}' — httpMethod inconnu '{}' dans rules.json, fallback POST", name, httpMethodStr);
            httpMethod = HttpMethod.POST;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerName", name);
        result.put("endpoint", endpoint);

        long start = System.currentTimeMillis();
        log.info("{} {} → provider='{}'", httpMethod, endpoint, name);

        try {
            RestTemplate rt = restTemplateBuilder
                    .connectTimeout(Duration.ofSeconds(timeoutSec))
                    .readTimeout(Duration.ofSeconds(timeoutSec))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType("XML".equalsIgnoreCase(outputFormat)
                    ? MediaType.APPLICATION_XML : MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.ALL));

            HttpEntity<String> req = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = rt.exchange(endpoint, httpMethod, req, String.class);

            long duration = System.currentTimeMillis() - start;
            boolean ok = resp.getStatusCode().is2xxSuccessful();

            result.put("httpStatus", resp.getStatusCode().value());
            result.put("success", ok);
            result.put("rawBody", resp.getBody());
            result.put("durationMs", duration);
            log.info("Provider '{}' → HTTP {} in {}ms", name, resp.getStatusCode().value(), duration);
            if (ok) {
                responseMappingHelper.applyResponseMapping(providerId, resp.getBody(), responseMappingRules, result);
            }

        } catch (HttpStatusCodeException e) {
            long duration = System.currentTimeMillis() - start;
            result.put("httpStatus", e.getStatusCode().value());
            result.put("success", false);
            result.put("rawBody", e.getResponseBodyAsString());
            result.put("durationMs", duration);
            log.warn("Provider '{}' HTTP {} — {}", name, e.getStatusCode().value(), e.getMessage());

        } catch (ResourceAccessException e) {
            long duration = System.currentTimeMillis() - start;
            result.put("httpStatus", 0);
            result.put("success", false);
            result.put("errorMessage", "Network error: " + e.getMessage());
            result.put("durationMs", duration);
            log.error("Network error → provider '{}': {}", name, e.getMessage());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            result.put("httpStatus", 0);
            result.put("success", false);
            result.put("errorMessage", "Unexpected error: " + e.getMessage());
            result.put("durationMs", duration);
            log.error("Unexpected error → provider '{}': {}", name, e.getMessage(), e);
        }

        return result;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validatePayload(Map<String, Object> input,
                                 List<Map<String, Object>> fields,
                                 List<Map<String, Object>> rules) {
        List<String> errors = new ArrayList<>();

        // Niveau 1 — validation structurelle (PipelineFields)
        for (Map<String, Object> field : fields) {
            String   path     = (String)  field.get("fieldPath");
            boolean required = (Boolean) field.getOrDefault("required", false);
            boolean nullable = (Boolean) field.getOrDefault("nullable", true);

            if (required && !input.containsKey(path)) {
                errors.add("Missing required field: " + path);
                continue;
            }

            Object value = input.get(path);
            if (!nullable && value == null) {
                errors.add("Field '" + path + "' cannot be null");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Structural validation failed: " + errors);
        }

        // Niveau 2 — validation métier (ValidationRules / GlobalValidationRules)
        for (Map<String, Object> rule : rules) {
            String fieldName = (String) rule.get("fieldName");
            String ruleType  = (String) rule.get("ruleType");
            String pattern   = (String) rule.get("pattern");

            Object value = input.get(fieldName);

            if (value == null) continue; // champ absent → pas validé ici

            String strVal = value.toString();

            boolean valid = switch (ruleType == null ? "" : ruleType.toUpperCase()) {
                case "NOT_NULL"       -> value != null;
                case "REGEX_EMAIL"    -> strVal.matches(
                        pattern != null ? pattern : "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$");
                case "REGEX_PHONE"    -> strVal.matches(
                        pattern != null ? pattern : "^\\+?[0-9]{7,15}$");
                case "REGEX_PASSWORD" -> strVal.matches(
                        pattern != null ? pattern : "^(?=.*[A-Z])(?=.*[0-9]).{8,}$");
                case "REGEX"          -> pattern != null && strVal.matches(pattern);
                case "MIN_MAX_LENGTH" -> {
                    // pattern = "min|max"
                    if (pattern == null) yield true;
                    String[] parts = pattern.split("\\|");
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    yield strVal.length() >= min && strVal.length() <= max;
                }
                case "TYPE_NUMBER"    -> strVal.matches("-?\\d+(\\.\\d+)?");
                case "TYPE_DATE"      -> {
                    if (pattern == null) yield true;
                    try {
                        java.time.LocalDate.parse(strVal,
                                java.time.format.DateTimeFormatter.ofPattern(pattern));
                        yield true;
                    } catch (Exception e) {
                        yield false;
                    }
                }
                default -> true;
            };

            if (!valid) {
                errors.add("Field '" + fieldName + "' failed rule " + ruleType
                        + (pattern != null ? " (pattern: " + pattern + ")" : ""));
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Business validation failed: " + errors);
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private Map<String, Object> applyMapping(Map<String, Object> input,
                                             List<Map<String, Object>> rules) {
        if (rules == null || rules.isEmpty()) return input;

        Map<String, Object> output = new LinkedHashMap<>(input);

        for (Map<String, Object> rule : rules) {
            String src  = (String) rule.get("sourceField");
            String tgt  = (String) rule.get("targetField");
            String type = (String) rule.get("mappingType");
            String expr = (String) rule.get("expression");
            if (expr != null) expr = expr.trim();

            try {
                switch (type == null ? "" : type.toUpperCase()) {
                    case "FIELD_PLACEMENT"  -> applyFieldPlacement(src, tgt, input, output);
                    case "VALUE_TRANSFORM"  -> applyValueTransform(src, tgt, expr, input, output);
                    case "RESTRUCTURING"    -> applyRestructuring(src, tgt, expr, input, output);
                    case "FORMAT_CHANGE"    -> applyFormatChange(src, tgt, expr, input, output);
                    case "CALCULATED_FIELD" -> applyCalculatedField(tgt, expr, input, output);
                    default -> log.warn("Unsupported mapping type: {}", type);
                }
            } catch (Exception e) {
                log.error("Mapping rule failed [{} → {}]: {}", src, tgt, e.getMessage());
            }
        }
        return output;
    }

    // ── FIELD_PLACEMENT ───────────────────────────────────────────────────────

    private void applyFieldPlacement(String src, String tgt,
                                     Map<String, Object> input,
                                     Map<String, Object> output) {
        if (src != null && src.contains("[].")) {
            processArrayField(src, tgt, input, output);
            return;
        }
        Object value = getNestedValue(input, src);
        if (value == null) { log.warn("FIELD_PLACEMENT — '{}' not found", src); return; }
        removeNestedKey(output, src);
        setNestedValue(output, tgt, value);
    }

    // ── VALUE_TRANSFORM ───────────────────────────────────────────────────────

    private void applyValueTransform(String src, String tgt, String expr,
                                     Map<String, Object> input,
                                     Map<String, Object> output) {
        if (expr == null || expr.isBlank()) return;

        Object transformed;
        if (expr.toUpperCase().startsWith("CONCAT:")) {
            transformed = applyConcat(expr, input);
        } else {
            Object raw = getNestedValue(input, src);
            if (raw == null) { log.warn("VALUE_TRANSFORM — '{}' not found", src); return; }
            transformed = transformSingleValue(raw, expr);
        }
        removeNestedKey(output, src);
        setNestedValue(output, tgt, transformed);
    }

    private Object transformSingleValue(Object raw, String expr) {
        String s = raw.toString();
        if (expr.equalsIgnoreCase("UPPERCASE"))      return s.toUpperCase();
        if (expr.equalsIgnoreCase("LOWERCASE"))      return s.toLowerCase();
        if (expr.equalsIgnoreCase("TRIM"))           return s.trim();

        if (expr.toUpperCase().startsWith("SPLIT:")) {
            String[] parts = expr.split(":", 3);
            if (parts.length < 3) throw new IllegalArgumentException("SPLIT format: SPLIT:sep:index");
            String[] tokens = s.split(java.util.regex.Pattern.quote(parts[1]));
            int index = Integer.parseInt(parts[2].trim());
            return tokens[index].trim();
        }

        if (expr.toUpperCase().startsWith("REGEX_REPLACE:")) {
            String body = expr.substring("REGEX_REPLACE:".length());
            int sep = body.indexOf(":");
            return s.replaceAll(body.substring(0, sep), body.substring(sep + 1));
        }

        throw new IllegalArgumentException("Unknown VALUE_TRANSFORM: " + expr);
    }

    private Object applyConcat(String expr, Map<String, Object> input) {
        String[] parts = expr.split(":", -1);
        if (parts.length < 4) throw new IllegalArgumentException("CONCAT format: CONCAT:sep:f1:f2");
        String separator = parts[1];
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            Object val = getNestedValue(input, parts[i].trim());
            if (val == null) throw new IllegalArgumentException("CONCAT — field '" + parts[i].trim() + "' not found");
            if (i > 2) sb.append(separator);
            sb.append(val);
        }
        return sb.toString();
    }

    // ── RESTRUCTURING ─────────────────────────────────────────────────────────

    private void applyRestructuring(String src, String tgt, String expr,
                                    Map<String, Object> input,
                                    Map<String, Object> output) {
        if ("FLATTEN".equalsIgnoreCase(expr)) {
            Object subObj = getNestedValue(input, src);
            if (!(subObj instanceof Map)) { log.warn("FLATTEN — '{}' is not an object", src); return; }
            @SuppressWarnings("unchecked")
            Map<String, Object> subMap = (Map<String, Object>) subObj;
            flattenInto(output, subMap, src);
            removeNestedKey(output, src);
        } else {
            // NEST (défaut)
            Object value = getNestedValue(input, src);
            if (value == null) { log.warn("RESTRUCTURING — '{}' not found", src); return; }
            removeNestedKey(output, src);
            setNestedValue(output, tgt, value);
        }
    }

    // ── FORMAT_CHANGE ─────────────────────────────────────────────────────────

    private void applyFormatChange(String src, String tgt, String expr,
                                   Map<String, Object> input,
                                   Map<String, Object> output) {
        Object raw = getNestedValue(input, src);
        if (raw == null) { log.warn("FORMAT_CHANGE — '{}' not found", src); return; }
        Object converted = convertValue(raw, expr);
        removeNestedKey(output, src);
        setNestedValue(output, tgt, converted);
    }

    private Object convertValue(Object raw, String expression) {
        String expr = expression == null ? "" : expression.trim().toUpperCase();
        return switch (expr) {
            case "STRING_TO_INT"    -> (int) Double.parseDouble(raw.toString().trim());
            case "STRING_TO_DOUBLE" -> Double.parseDouble(raw.toString().trim());
            case "STRING_TO_BOOL"   -> {
                String s = raw.toString().trim().toLowerCase();
                yield switch (s) {
                    case "true","1","yes","oui"  -> true;
                    case "false","0","no","non"  -> false;
                    default -> throw new IllegalArgumentException("Cannot convert '" + s + "' to boolean");
                };
            }
            case "NUMBER_TO_STRING", "BOOL_TO_STRING", "" -> String.valueOf(raw);
            case "DATE_TO_UNIX" -> {
                if (raw instanceof Number n) yield n.longValue();
                yield java.time.LocalDate.parse(raw.toString().trim(),
                                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        .atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
            }
            case "UNIX_TO_DATE" -> {
                long epoch = raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString().trim());
                yield java.time.Instant.ofEpochSecond(epoch)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            }
            default -> {
                if (!expr.contains("|")) throw new IllegalArgumentException("Unknown FORMAT_CHANGE: " + expression);
                String[] parts = expression.split("\\|", 2);
                yield java.time.LocalDate.parse(raw.toString().trim(),
                                java.time.format.DateTimeFormatter.ofPattern(parts[0].trim()))
                        .format(java.time.format.DateTimeFormatter.ofPattern(parts[1].trim()));
            }
        };
    }

    // ── CALCULATED_FIELD ──────────────────────────────────────────────────────

    private void applyCalculatedField(String tgt, String expr,
                                      Map<String, Object> input,
                                      Map<String, Object> output) {
        if (expr == null || expr.isBlank()) return;
        Object result;
        if (expr.toUpperCase().startsWith("IF:")) {
            result = evaluateConditional(expr, input);
        } else if (isAggregation(expr)) {
            result = evaluateAggregation(expr, input);
        } else {
            result = evaluateArithmetic(expr, input);
        }
        setNestedValue(output, tgt, result);
    }

    private boolean isAggregation(String expr) {
        String up = expr.toUpperCase();
        return up.startsWith("SUM:") || up.startsWith("AVG:") || up.startsWith("COUNT:")
                || up.startsWith("MIN:") || up.startsWith("MAX:");
    }

    private Object evaluateArithmetic(String expr, Map<String, Object> input) {
        String resolved = expr;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^}]+)}").matcher(expr);
        while (m.find()) {
            String field = m.group(1).trim();
            Object val = getNestedValue(input, field);
            if (val == null || !(val instanceof Number))
                throw new IllegalArgumentException("ARITHMETIC — field '" + field + "' not found or not a number");
            resolved = resolved.replace(m.group(0), val.toString());
        }
        return new net.objecthunter.exp4j.ExpressionBuilder(resolved).build().evaluate();
    }

    private Object evaluateConditional(String expr, Map<String, Object> input) {
        String[] parts = expr.split(":", 6);
        if (parts.length < 6) throw new IllegalArgumentException("IF format: IF:field:op:val:ifTrue:ifFalse");
        Object fieldVal = getNestedValue(input, parts[1].trim());
        if (fieldVal == null) throw new IllegalArgumentException("IF — field '" + parts[1].trim() + "' not found");
        boolean condition = evaluateCondition(fieldVal, parts[2].trim().toLowerCase(), parts[3].trim());
        return condition ? parts[4].trim() : parts[5].trim();
    }

    private boolean evaluateCondition(Object fieldVal, String op, String compareValue) {
        String strVal = fieldVal.toString().trim();
        return switch (op) {
            case "eq"       -> strVal.equalsIgnoreCase(compareValue);
            case "ne"       -> !strVal.equalsIgnoreCase(compareValue);
            case "contains" -> strVal.toLowerCase().contains(compareValue.toLowerCase());
            case "gt"  -> Double.parseDouble(strVal) >  Double.parseDouble(compareValue);
            case "lt"  -> Double.parseDouble(strVal) <  Double.parseDouble(compareValue);
            case "gte" -> Double.parseDouble(strVal) >= Double.parseDouble(compareValue);
            case "lte" -> Double.parseDouble(strVal) <= Double.parseDouble(compareValue);
            default -> throw new IllegalArgumentException("Unknown IF operator: " + op);
        };
    }

    @SuppressWarnings("unchecked")
    private Object evaluateAggregation(String expr, Map<String, Object> input) {
        int sep = expr.indexOf(":");
        String op   = expr.substring(0, sep).toUpperCase();
        String path = expr.substring(sep + 1).trim();

        String arrayPath, subField;
        if (path.contains("[].")) {
            arrayPath = path.substring(0, path.indexOf("[]."));
            subField  = path.substring(path.indexOf("[].") + 3);
        } else if (path.endsWith("[]")) {
            arrayPath = path.substring(0, path.length() - 2);
            subField  = null;
        } else {
            throw new IllegalArgumentException("Aggregation path must contain '[].' — got: " + path);
        }

        Object arrayObj = getNestedValue(input, arrayPath);
        if (!(arrayObj instanceof List)) throw new IllegalArgumentException("'" + arrayPath + "' is not an array");
        List<Object> items = (List<Object>) arrayObj;

        if (op.equals("COUNT")) return items.size();

        final String sf = subField;
        List<Double> values = items.stream()
                .filter(i -> i instanceof Map)
                .map(i -> {
                    Object v = getNestedValue((Map<String, Object>) i, sf);
                    if (!(v instanceof Number)) throw new IllegalArgumentException(op + " — '" + sf + "' is not a number");
                    return ((Number) v).doubleValue();
                }).toList();

        return switch (op) {
            case "SUM" -> values.stream().mapToDouble(Double::doubleValue).sum();
            case "AVG" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "MIN" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            case "MAX" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            default    -> throw new IllegalArgumentException("Unknown aggregation: " + op);
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String path) {
        if (path == null) return null;
        String[] keys = path.split("\\.");
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(key);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String path, Object value) {
        String[] keys = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        current.put(keys[keys.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private void removeNestedKey(Map<String, Object> map, String path) {
        String[] keys = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < keys.length - 1; i++) {
            Object next = current.get(keys[i]);
            if (!(next instanceof Map)) return;
            current = (Map<String, Object>) next;
        }
        current.remove(keys[keys.length - 1]);
    }

    @SuppressWarnings("unchecked")
    private void flattenInto(Map<String, Object> target, Map<String, Object> subMap, String path) {
        for (Map.Entry<String, Object> entry : subMap.entrySet()) {
            String fullKey = path + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flattenInto(target, (Map<String, Object>) entry.getValue(), fullKey);
            } else {
                target.put(fullKey, entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processArrayField(String src, String tgt,
                                   Map<String, Object> input,
                                   Map<String, Object> output) {
        int sep       = src.indexOf("[].");
        String arrayPath = src.substring(0, sep);
        String subSrc    = src.substring(sep + 3);
        String tgtArray  = tgt.contains("[].") ? tgt.substring(0, tgt.indexOf("[].")) : arrayPath;
        String subTgt    = tgt.contains("[].") ? tgt.substring(tgt.indexOf("[].") + 3) : tgt;

        Object arrayObj = getNestedValue(input, arrayPath);
        if (!(arrayObj instanceof List)) { log.warn("'{}' is not an array", arrayPath); return; }
        List<Object> items = (List<Object>) arrayObj;
        List<Object> outItems = (List<Object>) ((Map<String, Object>)
                output.computeIfAbsent(tgtArray, k -> new ArrayList<>()));

        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map)) continue;
            Map<String, Object> srcItem = (Map<String, Object>) items.get(i);
            while (outItems.size() <= i) outItems.add(new HashMap<String, Object>());
            Map<String, Object> outItem = (Map<String, Object>) outItems.get(i);
            Object value = getNestedValue(srcItem, subSrc);
            if (value == null) continue;
            outItem.remove(subSrc);
            setNestedValue(outItem, subTgt, value);
        }
    }
}
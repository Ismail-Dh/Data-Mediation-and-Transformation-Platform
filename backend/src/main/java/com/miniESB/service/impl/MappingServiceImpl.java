package com.miniESB.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniESB.domain.entity.MappingRule;
import com.miniESB.domain.entity.Pipeline;
import com.miniESB.domain.enums.MappingType;
import com.miniESB.domain.enums.PayloadStatus;
import com.miniESB.domain.enums.PipelineStatus;
import com.miniESB.dto.mapping.MappingResultResponse;
import com.miniESB.dto.mapping.MappingRuleRequest;
import com.miniESB.dto.mapping.MappingRuleResponse;
import com.miniESB.exception.ResourceNotFoundException;
import com.miniESB.repository.MappingRuleRepository;
import com.miniESB.repository.PayloadRepository;
import com.miniESB.repository.PipelineRepository;
import com.miniESB.service.MappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.miniESB.domain.entity.Payload;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MappingServiceImpl implements MappingService {

    private final MappingRuleRepository mappingRuleRepository;
    private final PipelineRepository    pipelineRepository;
    private final ObjectMapper          objectMapper; // injected by Spring — do not instantiate manually
    private final PayloadRepository payloadRepository; // ajouter


    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

@Override
@Transactional
public MappingRuleResponse createRule(Long pipelineId, MappingRuleRequest request) {
    Pipeline pipeline = pipelineRepository.findById(pipelineId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Pipeline not found with id=" + pipelineId));

    MappingRule rule = MappingRule.builder()
            .sourceField(request.sourceField())
            .targetField(request.targetField())
            .mappingType(request.mappingType())
            .expression(request.expression())
            .active(true)
            .pipeline(pipeline)
            .build();

    MappingRule saved = mappingRuleRepository.save(rule);
    log.info("MappingRule created: id={}, {}→{}, pipeline={}",
            saved.getId(), saved.getSourceField(), saved.getTargetField(), pipelineId);

    // Dirty-checking : pas besoin de save() explicite — Hibernate détecte le changement
    if (pipeline.getStatus() == PipelineStatus.DRAFT) {
        pipeline.setStatus(PipelineStatus.CONFIGURED);
        // ❌ pipelineRepository.save(pipeline);  ← SUPPRIMER cette ligne
        log.info("Pipeline id={} status updated to CONFIGURED", pipelineId);
    }

    return toResponse(saved);
}
    @Override
    @Transactional
    public MappingRuleResponse updateRule(Long pipelineId, Long ruleId, MappingRuleRequest request) {
        MappingRule rule = mappingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MappingRule not found with id=" + ruleId));

        if (!rule.getPipeline().getId().equals(pipelineId)) {
            throw new ResourceNotFoundException(
                    "MappingRule id=" + ruleId + " does not belong to pipeline id=" + pipelineId);
        }

        rule.setSourceField(request.sourceField());
        rule.setTargetField(request.targetField());
        rule.setMappingType(request.mappingType());
        rule.setExpression(request.expression());
        // active status is preserved — update never disables the rule

        MappingRule updated = mappingRuleRepository.save(rule);
        log.info("MappingRule updated: id={}, pipeline={}", ruleId, pipelineId);
        return toResponse(updated);
    }

    @Override
    public List<MappingRuleResponse> getRulesByPipeline(Long pipelineId) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));
        return mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<MappingRuleResponse> getAllRulesByPipeline(Long pipelineId) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));
        return mappingRuleRepository.findByPipelineId(pipelineId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteRule(Long pipelineId, Long ruleId) {
        MappingRule rule = mappingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MappingRule not found with id=" + ruleId));

        // Ensure the rule belongs to the given pipeline — prevents cross-pipeline operations
        if (!rule.getPipeline().getId().equals(pipelineId)) {
            throw new ResourceNotFoundException(
                    "MappingRule id=" + ruleId + " does not belong to pipeline id=" + pipelineId);
        }

        rule.setActive(false); // soft delete — rule is kept in DB for audit purposes
        mappingRuleRepository.save(rule);
        log.info("MappingRule disabled: id={}, pipeline={}", ruleId, pipelineId);
    }

    @Override
    @Transactional
    public MappingResultResponse applyMappingToPayload(Long pipelineId, Long payloadId) {
      pipelineRepository.findById(pipelineId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Pipeline not found with id=" + pipelineId));

       Payload payload = payloadRepository.findById(payloadId)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Payload not found with id=" + payloadId));

       // Vérifier que le payload est VALIDATED avant de mapper
       if (payload.getStatus() != PayloadStatus.VALIDATED) {
          throw new IllegalStateException(
                "Payload id=" + payloadId + " must be VALIDATED before mapping — current status: "
                + payload.getStatus());
       }

       Map<String, Object> input  = parseRawContent(payload.getRawContent());
       Map<String, Object> mapped = applyMapping(pipelineId, input);

       // payload → MAPPED
       payload.setStatus(PayloadStatus.MAPPED);
       payloadRepository.save(payload);
       log.info("Payload mapped: id={}, pipeline={}", payloadId, pipelineId);

       return new MappingResultResponse(pipelineId, input, mapped);
    }

    @Override
    @Transactional
    public MappingRuleResponse activateRule(Long pipelineId, Long ruleId) {
        MappingRule rule = mappingRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MappingRule not found with id=" + ruleId));

        // Ensure the rule belongs to the given pipeline — prevents cross-pipeline operations
        if (!rule.getPipeline().getId().equals(pipelineId)) {
            throw new ResourceNotFoundException(
                    "MappingRule id=" + ruleId + " does not belong to pipeline id=" + pipelineId);
        }

        rule.setActive(true);
        mappingRuleRepository.save(rule);
        log.info("MappingRule activated: id={}, pipeline={}", ruleId, pipelineId);
        return toResponse(rule);
    }

    // -------------------------------------------------------------------------
    // MAPPING EXECUTION
    // -------------------------------------------------------------------------

    @Override
    public MappingResultResponse applyMappingToPayload(Long pipelineId, String rawContent) {
        pipelineRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pipeline not found with id=" + pipelineId));

        Map<String, Object> input  = parseRawContent(rawContent);
        Map<String, Object> mapped = applyMapping(pipelineId, input);

        log.info("Mapping applied for pipeline={}", pipelineId);
        return new MappingResultResponse(pipelineId, input, mapped);
    }


    private Map<String, Object> applyMapping(Long pipelineId, Map<String, Object> input) {
        List<MappingRule> rules = mappingRuleRepository.findByPipelineIdAndActiveTrue(pipelineId);

        if (rules.isEmpty()) {
            log.warn("Pipeline id={} has no MappingRule defined — returning input as-is", pipelineId);
            return input;
        }

        // Copy all fields first — unmapped fields are kept as-is
        Map<String, Object> output = new LinkedHashMap<>(input);

        for (MappingRule rule : rules) {
            if (rule.getMappingType() == MappingType.FIELD_PLACEMENT) {
                applyFieldPlacement(rule, input, output);
            }
            if(rule.getMappingType()==MappingType.VALUE_TRANSFORM){
                applyValueTransform(rule, input, output);
            }
            if(rule.getMappingType()== MappingType.RESTRUCTURING){
                applyRestructuring(rule, input, output);
            }
            if(rule.getMappingType()==MappingType.FORMAT_CHANGE){
                applyFormatChange(rule, input, output);
            }
            if(rule.getMappingType()==MappingType.CALCULATED_FIELD){
                applyCalculatedField(rule, input, output);
            }
        }

        return reorderByInput(input, output, rules);

    }
    private Map<String, Object> reorderByInput(Map<String, Object> input,
                                               Map<String, Object> output,
                                               List<MappingRule> rules) {
        // Build a lookup: top-level sourceField → top-level targetField
        Map<String, String> srcToTgt = new LinkedHashMap<>();
        for (MappingRule rule : rules) {
            String src = rule.getSourceField();
            String tgt = rule.getTargetField();
            if (src == null || tgt == null || src.equals("N/A") || tgt.equals("N/A")) continue;
            String srcTop = src.contains(".") ? src.substring(0, src.indexOf('.')) : src;
            String tgtTop = tgt.contains(".") ? tgt.substring(0, tgt.indexOf('.')) : tgt;
            if (!srcTop.contains("[") && !tgtTop.contains("[")) {
                srcToTgt.put(srcTop, tgtTop);
            }
        }

        Map<String, Object> ordered = new LinkedHashMap<>();
        Set<String> placed = new java.util.HashSet<>();

        // 1. Walk input keys in original order
        for (String inputKey : input.keySet()) {
            String targetKey = srcToTgt.getOrDefault(inputKey, inputKey);
            if (output.containsKey(targetKey) && !placed.contains(targetKey)) {
                ordered.put(targetKey, output.get(targetKey));
                placed.add(targetKey);
            } else if (output.containsKey(inputKey) && !placed.contains(inputKey)) {
                ordered.put(inputKey, output.get(inputKey));
                placed.add(inputKey);
            }
            // key consumed by rule (e.g. FLATTEN) — skip
        }

        // 2. Append keys added by rules (CALCULATED_FIELD, new fields, etc.)
        for (Map.Entry<String, Object> entry : output.entrySet()) {
            if (!placed.contains(entry.getKey())) {
                ordered.put(entry.getKey(), entry.getValue());
            }
        }

        return ordered;
    }


    private void applyFieldPlacement(MappingRule rule,
                                     Map<String, Object> input,
                                     Map<String, Object> output) {
        String src = rule.getSourceField();
        String tgt = rule.getTargetField();


        if (src.contains("[].")) {
            processArrayField(rule, input, output);
            return;
        }


        Object value = getNestedValue(input, src);
        if (value == null) {
            log.warn("MappingRule id={} — sourceField '{}' not found in input",
                    rule.getId(), src);
            return;
        }

        removeNestedKey(output, src);
        setNestedValue(output, tgt, value);
        log.debug("Mapped '{}' → '{}'", src, tgt);
    }


    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String path) {
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
            current = (Map<String, Object>)
                    current.computeIfAbsent(keys[i], k -> new HashMap<>());
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
    private void processArrayField(MappingRule rule,
                                   Map<String, Object> input,
                                   Map<String, Object> output) {
        String src = rule.getSourceField();
        String tgt = rule.getTargetField();

        int sep = src.indexOf("[].");
        String arrayPath = src.substring(0, sep);
        String subSrc    = src.substring(sep + 3);

        String subTgt = tgt.contains("[].") ? tgt.substring(tgt.indexOf("[].") + 3) : tgt;
        String tgtArray = tgt.contains("[].") ? tgt.substring(0, tgt.indexOf("[].")) : arrayPath;

        Object arrayObj = getNestedValue(input, arrayPath);
        if (!(arrayObj instanceof List)) {
            log.warn("MappingRule id={} — '{}' n'est pas un array dans input",
                    rule.getId(), arrayPath);
            return;
        }

        List<Object> items = (List<Object>) arrayObj;

        List<Object> outItems = (List<Object>)
                ((Map<String, Object>) output
                        .computeIfAbsent(tgtArray, k -> new java.util.ArrayList<>()));

        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map)) continue;
            Map<String, Object> srcItem = (Map<String, Object>) items.get(i);

            // S'assure que l'item correspondant existe dans outItems
            while (outItems.size() <= i) outItems.add(new HashMap<String, Object>());
            Map<String, Object> outItem = (Map<String, Object>) outItems.get(i);

            Object value = getNestedValue(srcItem, subSrc);
            if (value == null) {
                log.warn("MappingRule id={} — '{}' absent dans item[{}]",
                        rule.getId(), subSrc, i);
                continue;
            }
            outItem.remove(subSrc);
            setNestedValue(outItem, subTgt, value);
            log.debug("Array mapped [{}] '{}' → '{}'", i, subSrc, subTgt);
        }
    }

//VALUE_TRANSFORM
    private void applyValueTransform(MappingRule rule,
                                     Map<String, Object> input,
                                     Map<String, Object> output) {
        if (rule.getExpression() == null || rule.getExpression().isBlank()) {
            log.warn("VALUE_TRANSFORM rule id={} — expression is null or blank, skipping", rule.getId());
            return;
        }

        String expr = rule.getExpression().trim();
        Object transformed;

        try {
            if (expr.startsWith("CONCAT:")) {
                transformed = applyConcat(expr, input);
            } else {
                Object raw = getNestedValue(input, rule.getSourceField());
                if (raw == null) {
                    log.warn("VALUE_TRANSFORM rule id={} — sourceField '{}' not found in input",
                            rule.getId(), rule.getSourceField());
                    return;
                }
                transformed = transformSingleValue(raw, expr);
            }
        } catch (Exception e) {
            log.error("VALUE_TRANSFORM rule id={} — failed: {}", rule.getId(), e.getMessage());
            return;
        }

        removeNestedKey(output, rule.getSourceField());
        setNestedValue(output, rule.getTargetField(), transformed);
        log.debug("VALUE_TRANSFORM '{}' → '{}' ({})",
                rule.getSourceField(), rule.getTargetField(), expr);
    }


    private Object transformSingleValue(Object raw, String expr) {
        String s = raw.toString();

        if (expr.equalsIgnoreCase("UPPERCASE")) return s.toUpperCase();
        if (expr.equalsIgnoreCase("LOWERCASE")) return s.toLowerCase();
        if (expr.equalsIgnoreCase("TRIM"))      return s.trim();

        // ── Famille amber : extraction ─────────────────────────────────────────
        // expression = "SPLIT:séparateur:index"

        if (expr.toUpperCase().startsWith("SPLIT:")) {
            String[] parts = expr.split(":", 3);
            if (parts.length < 3)
                throw new IllegalArgumentException("SPLIT format: SPLIT:separator:index");
            String separator = parts[1];
            int index = Integer.parseInt(parts[2].trim());
            String[] tokens = s.split(java.util.regex.Pattern.quote(separator));
            if (index < 0 || index >= tokens.length)
                throw new IllegalArgumentException(
                        "SPLIT index " + index + " out of bounds (length=" + tokens.length + ")");
            return tokens[index].trim();
        }

        // ── Famille coral : nettoyage regex ────────────────────────────────────
        // expression = "REGEX_REPLACE:pattern:replacement"
        // le replacement peut être vide → "REGEX_REPLACE:[^0-9]:"
        if (expr.toUpperCase().startsWith("REGEX_REPLACE:")) {
            String body = expr.substring("REGEX_REPLACE:".length());
            int sepIdx = body.indexOf(":");
            if (sepIdx < 0)
                throw new IllegalArgumentException("REGEX_REPLACE format: REGEX_REPLACE:pattern:replacement");
            String pattern     = body.substring(0, sepIdx);
            String replacement = body.substring(sepIdx + 1);
            return s.replaceAll(pattern, replacement);
        }

        throw new IllegalArgumentException("Unknown VALUE_TRANSFORM expression: " + expr);
    }

    // expression = "CONCAT:séparateur:champ1:champ2:..."

    private Object applyConcat(String expr, Map<String, Object> input) {
        String[] parts = expr.split(":", -1);
        if (parts.length < 4)
            throw new IllegalArgumentException(
                    "CONCAT format: CONCAT:separator:field1:field2[:field3...]");

        String separator = parts[1];
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < parts.length; i++) {
            Object val = getNestedValue(input, parts[i].trim());
            if (val == null)
                throw new IllegalArgumentException(
                        "CONCAT — field '" + parts[i].trim() + "' not found in input");
            if (i > 2) sb.append(separator);
            sb.append(val);
        }
        return sb.toString();
    }



    //RESTRUCTURING
    private void applyRestructuring(MappingRule rule,
                                    Map<String, Object> input,
                                    Map<String, Object> output) {
        String expr = rule.getExpression() == null ? "" : rule.getExpression().trim().toUpperCase();

        if (expr.equals("FLATTEN")) {
            applyFlatten(rule, input, output);
        } else {

            applyNest(rule, input, output);
        }
    }


    private void applyNest(MappingRule rule,
                           Map<String, Object> input,
                           Map<String, Object> output) {
        Object value = getNestedValue(input, rule.getSourceField());
        if (value == null) {
            log.warn("RESTRUCTURING/NEST rule id={} — sourceField '{}' not found in input",
                    rule.getId(), rule.getSourceField());
            return;
        }

        removeNestedKey(output, rule.getSourceField());

        setNestedValue(output, rule.getTargetField(), value);

        log.debug("RESTRUCTURING/NEST '{}' → '{}'",
                rule.getSourceField(), rule.getTargetField());
    }


    @SuppressWarnings("unchecked")
    private void applyFlatten(MappingRule rule,
                              Map<String, Object> input,
                              Map<String, Object> output) {
        Object subObj = getNestedValue(input, rule.getSourceField());

        if (!(subObj instanceof Map)) {
            log.warn("RESTRUCTURING/FLATTEN rule id={} — '{}' is not an object (or not found)",
                    rule.getId(), rule.getSourceField());
            return;
        }

        Map<String, Object> subMap = (Map<String, Object>) subObj;

        String prefix = rule.getSourceField();

        flattenInto(output, subMap, prefix);

        removeNestedKey(output, rule.getSourceField());

        log.debug("RESTRUCTURING/FLATTEN '{}' → {} clés aplaties",
                rule.getSourceField(), subMap.size());
    }


    @SuppressWarnings("unchecked")
    private void flattenInto(Map<String, Object> target,
                             Map<String, Object> subMap,
                             String path) {
        for (Map.Entry<String, Object> entry : subMap.entrySet()) {
            String fullKey = path + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flattenInto(target, (Map<String, Object>) entry.getValue(), fullKey);
            } else {
                target.put(fullKey, entry.getValue());
            }
        }
    }


    //FORMAT_CHANGE
    private void applyFormatChange(MappingRule rule,
                                   Map<String, Object> input,
                                   Map<String, Object> output) {
        Object raw = getNestedValue(input, rule.getSourceField());
        if (raw == null) {
            log.warn("FORMAT_CHANGE rule id={} — '{}' not found in input",
                    rule.getId(), rule.getSourceField());
            return;
        }

        Object converted;
        try {
            converted = convertValue(raw, rule.getExpression());
        } catch (Exception e) {
            log.error("FORMAT_CHANGE rule id={} — conversion failed: {}", rule.getId(), e.getMessage());
            return;
        }

        removeNestedKey(output, rule.getSourceField());
        setNestedValue(output, rule.getTargetField(), converted);
        log.debug("FORMAT_CHANGE '{}' → '{}' ({})", rule.getSourceField(), rule.getTargetField(), rule.getExpression());
    }

    private Object convertValue(Object raw, String expression) {
        // expression contient le nom de la conversion (ex: "STRING_TO_INT")
        // sauf pour DATE_REFORMAT où c'est "sourceFormat|targetFormat"
        String expr = expression == null ? "" : expression.trim().toUpperCase();

        return switch (expr) {

            // ── String → type primitif ───────────────────────────────────────
            case "STRING_TO_INT" -> {
                String s = raw.toString().trim();
                // gère les doubles comme "3.0" envoyés par Jackson
                yield (int) Double.parseDouble(s);
            }
            case "STRING_TO_DOUBLE" -> Double.parseDouble(raw.toString().trim());

            case "STRING_TO_BOOL"   -> {
                String s = raw.toString().trim().toLowerCase();
                yield switch (s) {
                    case "true",  "1", "yes", "oui" -> true;
                    case "false", "0", "no",  "non" -> false;
                    default -> throw new IllegalArgumentException("Cannot convert '" + s + "' to boolean");
                };
            }

            // ── Type primitif → String ───────────────────────────────────────
            case "NUMBER_TO_STRING", "BOOL_TO_STRING", "" -> String.valueOf(raw);

            // ── Date → epoch secondes ────────────────────────────────────────
            case "DATE_TO_UNIX" -> {
                // accepte aussi un nombre déjà en epoch
                if (raw instanceof Number n) yield n.longValue();
                yield java.time.LocalDate
                        .parse(raw.toString().trim(),
                                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        .atStartOfDay(java.time.ZoneOffset.UTC)
                        .toEpochSecond();
            }

            // ── Epoch secondes → ISO date ────────────────────────────────────
            case "UNIX_TO_DATE" -> {
                long epoch = raw instanceof Number n ? n.longValue()
                        : Long.parseLong(raw.toString().trim());
                yield java.time.Instant.ofEpochSecond(epoch)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toLocalDate()
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            }

            // ── Reformater une date  "sourceFormat|targetFormat" ────────────
            default -> {
                // expression = "dd/MM/yyyy|yyyy-MM-dd"
                if (!expr.contains("|"))
                    throw new IllegalArgumentException("Unknown FORMAT_CHANGE expression: " + expression);

                String[] parts = expression.split("\\|", 2);
                java.time.format.DateTimeFormatter srcFmt =
                        java.time.format.DateTimeFormatter.ofPattern(parts[0].trim());
                java.time.format.DateTimeFormatter tgtFmt =
                        java.time.format.DateTimeFormatter.ofPattern(parts[1].trim());

                yield java.time.LocalDate
                        .parse(raw.toString().trim(), srcFmt)
                        .format(tgtFmt);
            }
        };
    }




    private void applyCalculatedField(MappingRule rule,
                                      Map<String, Object> input,
                                      Map<String, Object> output) {
        if (rule.getExpression() == null || rule.getExpression().isBlank()) {
            log.warn("CALCULATED_FIELD rule id={} — expression is null or blank, skipping",
                    rule.getId());
            return;
        }

        String expr = rule.getExpression().trim();
        Object result;

        try {
            if (expr.toUpperCase().startsWith("IF:")) {
                result = evaluateConditional(expr, input);
            } else if (isAggregation(expr)) {
                result = evaluateAggregation(expr, input);
            } else {
                // expression arithmétique : "{prix} * (1 + {tva})"
                result = evaluateArithmetic(expr, input);
            }
        } catch (Exception e) {
            log.error("CALCULATED_FIELD rule id={} — evaluation failed: {}",
                    rule.getId(), e.getMessage());
            return;
        }

        setNestedValue(output, rule.getTargetField(), result);
        log.debug("CALCULATED_FIELD → '{}' = {}", rule.getTargetField(), result);
    }

    private boolean isAggregation(String expr) {
        String up = expr.toUpperCase();
        return up.startsWith("SUM:")  || up.startsWith("AVG:")   ||
                up.startsWith("COUNT:")|| up.startsWith("MIN:")   ||
                up.startsWith("MAX:");
    }


    private Object evaluateArithmetic(String expr, Map<String, Object> input) {
        String resolved = expr;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\{([^}]+)}")
                        .matcher(expr);

        while (m.find()) {
            String fieldName = m.group(1).trim();
            Object val = getNestedValue(input, fieldName);
            if (val == null)
                throw new IllegalArgumentException(
                        "ARITHMETIC — field '" + fieldName + "' not found in input");
            if (!(val instanceof Number))
                throw new IllegalArgumentException(
                        "ARITHMETIC — field '" + fieldName + "' is not a number (got: " + val + ")");
            resolved = resolved.replace(m.group(0), val.toString());
        }


        try {
            return new net.objecthunter.exp4j.ExpressionBuilder(resolved)
                    .build()
                    .evaluate();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "ARITHMETIC — invalid expression '" + resolved + "': " + e.getMessage());
        }
    }

    private Object evaluateConditional(String expr, Map<String, Object> input) {
        String[] parts = expr.split(":", 6);
        if (parts.length < 6)
            throw new IllegalArgumentException(
                    "IF format: IF:field:op:compareValue:ifTrue:ifFalse — got: " + expr);

        String fieldName    = parts[1].trim();
        String op           = parts[2].trim().toLowerCase();
        String compareValue = parts[3].trim();
        String ifTrue       = parts[4].trim();
        String ifFalse      = parts[5].trim();

        Object fieldVal = getNestedValue(input, fieldName);
        if (fieldVal == null)
            throw new IllegalArgumentException(
                    "IF — field '" + fieldName + "' not found in input");

        boolean condition = evaluateCondition(fieldVal, op, compareValue);
        return condition ? ifTrue : ifFalse;
    }

    private boolean evaluateCondition(Object fieldVal, String op, String compareValue) {
        String strVal = fieldVal.toString().trim();

        return switch (op) {
            case "eq"       -> strVal.equalsIgnoreCase(compareValue);
            case "ne"       -> !strVal.equalsIgnoreCase(compareValue);
            case "contains" -> strVal.toLowerCase().contains(compareValue.toLowerCase());

            case "gt", "lt", "gte", "lte" -> {
                double fieldNum   = Double.parseDouble(strVal);
                double compareNum = Double.parseDouble(compareValue);
                yield switch (op) {
                    case "gt"  -> fieldNum >  compareNum;
                    case "lt"  -> fieldNum <  compareNum;
                    case "gte" -> fieldNum >= compareNum;
                    case "lte" -> fieldNum <= compareNum;
                    default    -> false;
                };
            }
            default -> throw new IllegalArgumentException("Unknown IF operator: " + op);
        };
    }


    @SuppressWarnings("unchecked")
    private Object evaluateAggregation(String expr, Map<String, Object> input) {
        String up   = expr.toUpperCase();
        int    sep  = expr.indexOf(":");
        String op   = expr.substring(0, sep).toUpperCase();
        String path = expr.substring(sep + 1).trim();


        String arrayPath;
        String subField;

        if (path.contains("[].")) {
            int idx  = path.indexOf("[].");
            arrayPath = path.substring(0, idx);
            subField  = path.substring(idx + 3);
        } else if (path.endsWith("[]")) {
            arrayPath = path.substring(0, path.length() - 2);
            subField  = null;
        } else {
            throw new IllegalArgumentException(
                    "Aggregation path must contain '[].' or end with '[]' — got: " + path);
        }

        Object arrayObj = getNestedValue(input, arrayPath);
        if (!(arrayObj instanceof java.util.List))
            throw new IllegalArgumentException(
                    "Aggregation — '" + arrayPath + "' is not an array");

        java.util.List<Object> items = (java.util.List<Object>) arrayObj;

        if (op.equals("COUNT")) return items.size();

        if (subField == null)
            throw new IllegalArgumentException(op + " requires a sub-field (e.g. items[].prix)");

        final String sf = subField;
        java.util.List<Double> values = items.stream()
                .filter(item -> item instanceof Map)
                .map(item -> {
                    Object v = getNestedValue((Map<String, Object>) item, sf);
                    if (v == null)
                        throw new IllegalArgumentException(
                                op + " — sub-field '" + sf + "' not found in one of the items");
                    if (!(v instanceof Number))
                        throw new IllegalArgumentException(
                                op + " — sub-field '" + sf + "' is not a number");
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
            default    -> throw new IllegalArgumentException("Unknown aggregation: " + op);
        };
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    // Parses a raw JSON string into a Map. Throws if the content is not valid JSON.
    private Map<String, Object> parseRawContent(String rawContent) {
        try {
        return objectMapper.readValue(rawContent, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON content: " + e.getMessage());
        }
    }

    private MappingRuleResponse toResponse(MappingRule rule) {
        return new MappingRuleResponse(
                rule.getId(),
                rule.getSourceField(),
                rule.getTargetField(),
                rule.getMappingType(),
                rule.getExpression(),
                rule.isActive(),
                rule.getPipeline().getId()
        );
    }
}
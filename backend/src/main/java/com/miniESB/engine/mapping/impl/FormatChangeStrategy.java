package com.miniESB.engine.mapping.impl;

import com.miniESB.domain.enums.MappingType;
import com.miniESB.engine.mapping.MappingRuleData;
import com.miniESB.engine.mapping.MappingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static com.miniESB.engine.mapping.PayloadPathUtils.*;

@Slf4j
@Component
public class FormatChangeStrategy implements MappingStrategy {

    @Override
    public MappingType supports() {
        return MappingType.FORMAT_CHANGE;
    }

    @Override
    public void apply(MappingRuleData rule, Map<String, Object> input, Map<String, Object> output) {
        Object raw = getNestedValue(input, rule.sourceField());
        if (raw == null) {
            log.warn("FORMAT_CHANGE rule {} — '{}' not found in input", rule.label(), rule.sourceField());
            return;
        }

        Object converted;
        try {
            converted = convertValue(raw, rule.expression());
        } catch (Exception e) {
            log.error("FORMAT_CHANGE rule {} — conversion failed: {}", rule.label(), e.getMessage());
            return;
        }

        removeNestedKey(output, rule.sourceField());
        setNestedValue(output, rule.targetField(), converted);
        log.debug("FORMAT_CHANGE '{}' → '{}' ({})", rule.sourceField(), rule.targetField(), rule.expression());
    }

    private Object convertValue(Object raw, String expression) {
        // expression contient le nom de la conversion (ex: "STRING_TO_INT")
        // sauf pour le reformatage de date où c'est "sourceFormat|targetFormat"
        String expr = expression == null ? "" : expression.trim().toUpperCase();

        return switch (expr) {
            case "STRING_TO_INT" -> {
                String s = raw.toString().trim();
                yield (int) Double.parseDouble(s); // gère les doubles comme "3.0" (Jackson)
            }
            case "STRING_TO_DOUBLE" -> Double.parseDouble(raw.toString().trim());

            case "STRING_TO_BOOL" -> {
                String s = raw.toString().trim().toLowerCase();
                yield switch (s) {
                    case "true", "1", "yes", "oui" -> true;
                    case "false", "0", "no", "non" -> false;
                    default -> throw new IllegalArgumentException("Cannot convert '" + s + "' to boolean");
                };
            }

            case "NUMBER_TO_STRING", "BOOL_TO_STRING", "" -> String.valueOf(raw);

            case "DATE_TO_UNIX" -> {
                if (raw instanceof Number n) yield n.longValue();
                yield LocalDate.parse(raw.toString().trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toEpochSecond();
            }

            case "UNIX_TO_DATE" -> {
                long epoch = raw instanceof Number n ? n.longValue() : Long.parseLong(raw.toString().trim());
                yield Instant.ofEpochSecond(epoch)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .format(DateTimeFormatter.ISO_LOCAL_DATE);
            }

            default -> {
                // "dd/MM/yyyy|yyyy-MM-dd"
                if (!expr.contains("|"))
                    throw new IllegalArgumentException("Unknown FORMAT_CHANGE expression: " + expression);

                String[] parts = expression.split("\\|", 2);
                DateTimeFormatter srcFmt = DateTimeFormatter.ofPattern(parts[0].trim());
                DateTimeFormatter tgtFmt = DateTimeFormatter.ofPattern(parts[1].trim());

                yield LocalDate.parse(raw.toString().trim(), srcFmt).format(tgtFmt);
            }
        };
    }
}

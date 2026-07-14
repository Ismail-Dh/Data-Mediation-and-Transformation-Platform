package com.miniESB.engine.mapping;

import java.util.*;

/**
 * Utilitaires de navigation dans un payload JSON générique ({@code Map<String,Object>})
 * en notation pointée ("customer.email", "items[].price").
 *
 * <p>Extrait de {@code MappingServiceImpl} et {@code EngineProcessService}, où cette
 * logique était dupliquée à l'identique (violation SRP + DRY). Classe utilitaire
 * sans état, testable indépendamment du reste du moteur de mapping.</p>
 */
public final class PayloadPathUtils {

    private PayloadPathUtils() {
    }

    @SuppressWarnings("unchecked")
    public static Object getNestedValue(Map<String, Object> map, String path) {
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
    public static void setNestedValue(Map<String, Object> map, String path, Object value) {
        String[] keys = path.split("\\.");
        Map<String, Object> current = map;
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        current.put(keys[keys.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    public static void removeNestedKey(Map<String, Object> map, String path) {
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
    public static void flattenInto(Map<String, Object> target, Map<String, Object> subMap, String path) {
        for (Map.Entry<String, Object> entry : subMap.entrySet()) {
            String fullKey = path + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flattenInto(target, (Map<String, Object>) entry.getValue(), fullKey);
            } else {
                target.put(fullKey, entry.getValue());
            }
        }
    }

    /**
     * Réordonne {@code output} pour suivre l'ordre des clés de {@code input},
     * en tenant compte des renommages top-level effectués par les règles.
     */
    public static Map<String, Object> reorderByInput(Map<String, Object> input,
                                                      Map<String, Object> output,
                                                      List<MappingRuleData> rules) {
        Map<String, String> srcToTgt = new LinkedHashMap<>();
        for (MappingRuleData rule : rules) {
            String src = rule.sourceField();
            String tgt = rule.targetField();
            if (src == null || tgt == null || src.equals("N/A") || tgt.equals("N/A")) continue;
            String srcTop = src.contains(".") ? src.substring(0, src.indexOf('.')) : src;
            String tgtTop = tgt.contains(".") ? tgt.substring(0, tgt.indexOf('.')) : tgt;
            if (!srcTop.contains("[") && !tgtTop.contains("[")) {
                srcToTgt.put(srcTop, tgtTop);
            }
        }

        Map<String, Object> ordered = new LinkedHashMap<>();
        Set<String> placed = new HashSet<>();

        for (String inputKey : input.keySet()) {
            String targetKey = srcToTgt.getOrDefault(inputKey, inputKey);
            if (output.containsKey(targetKey) && !placed.contains(targetKey)) {
                ordered.put(targetKey, output.get(targetKey));
                placed.add(targetKey);
            } else if (output.containsKey(inputKey) && !placed.contains(inputKey)) {
                ordered.put(inputKey, output.get(inputKey));
                placed.add(inputKey);
            }
        }

        for (Map.Entry<String, Object> entry : output.entrySet()) {
            if (!placed.contains(entry.getKey())) {
                ordered.put(entry.getKey(), entry.getValue());
            }
        }

        return ordered;
    }
}

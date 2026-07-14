package com.miniESB.engine.mapping;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PayloadPathUtils — unit tests")
class PayloadPathUtilsTest {

    @Nested
    @DisplayName("getNestedValue")
    class GetNestedValue {

        @Test
        @DisplayName("returns top-level value")
        void topLevel() {
            Map<String, Object> map = Map.of("name", "Alice");
            assertThat(PayloadPathUtils.getNestedValue(map, "name")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("returns nested value across several levels")
        void nested() {
            Map<String, Object> map = new HashMap<>();
            Map<String, Object> customer = new HashMap<>();
            customer.put("email", "alice@example.com");
            map.put("customer", customer);

            assertThat(PayloadPathUtils.getNestedValue(map, "customer.email"))
                    .isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("returns null when the path is null")
        void nullPath() {
            Map<String, Object> map = Map.of("a", "b");
            assertThat(PayloadPathUtils.getNestedValue(map, null)).isNull();
        }

        @Test
        @DisplayName("returns null when an intermediate segment is missing")
        void missingIntermediate() {
            Map<String, Object> map = Map.of("customer", Map.of("email", "a@b.com"));
            assertThat(PayloadPathUtils.getNestedValue(map, "customer.phone.number")).isNull();
        }

        @Test
        @DisplayName("returns null when an intermediate segment is not a map")
        void intermediateNotAMap() {
            Map<String, Object> map = Map.of("customer", "not-a-map");
            assertThat(PayloadPathUtils.getNestedValue(map, "customer.email")).isNull();
        }
    }

    @Nested
    @DisplayName("setNestedValue")
    class SetNestedValue {

        @Test
        @DisplayName("sets a top-level value")
        void topLevel() {
            Map<String, Object> map = new HashMap<>();
            PayloadPathUtils.setNestedValue(map, "name", "Bob");
            assertThat(map).containsEntry("name", "Bob");
        }

        @Test
        @DisplayName("creates intermediate maps as needed")
        void createsIntermediateMaps() {
            Map<String, Object> map = new HashMap<>();
            PayloadPathUtils.setNestedValue(map, "customer.address.city", "Paris");

            @SuppressWarnings("unchecked")
            Map<String, Object> customer = (Map<String, Object>) map.get("customer");
            @SuppressWarnings("unchecked")
            Map<String, Object> address = (Map<String, Object>) customer.get("address");
            assertThat(address).containsEntry("city", "Paris");
        }

        @Test
        @DisplayName("overwrites an existing value")
        void overwrites() {
            Map<String, Object> map = new HashMap<>(Map.of("name", "Old"));
            PayloadPathUtils.setNestedValue(map, "name", "New");
            assertThat(map).containsEntry("name", "New");
        }
    }

    @Nested
    @DisplayName("removeNestedKey")
    class RemoveNestedKey {

        @Test
        @DisplayName("removes a top-level key")
        void topLevel() {
            Map<String, Object> map = new HashMap<>(Map.of("name", "Alice"));
            PayloadPathUtils.removeNestedKey(map, "name");
            assertThat(map).doesNotContainKey("name");
        }

        @Test
        @DisplayName("removes a nested key without touching siblings")
        void nested() {
            Map<String, Object> customer = new HashMap<>();
            customer.put("email", "a@b.com");
            customer.put("phone", "123");
            Map<String, Object> map = new HashMap<>();
            map.put("customer", customer);

            PayloadPathUtils.removeNestedKey(map, "customer.email");

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) map.get("customer");
            assertThat(result).doesNotContainKey("email").containsEntry("phone", "123");
        }

        @Test
        @DisplayName("does nothing when an intermediate segment is not a map")
        void intermediateNotAMap() {
            Map<String, Object> map = new HashMap<>(Map.of("customer", "not-a-map"));
            assertThatCode(() -> PayloadPathUtils.removeNestedKey(map, "customer.email"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("flattenInto")
    class FlattenInto {

        @Test
        @DisplayName("flattens a single-level sub-map with the given prefix")
        void singleLevel() {
            Map<String, Object> target = new LinkedHashMap<>();
            Map<String, Object> subMap = Map.of("email", "a@b.com", "phone", "123");

            PayloadPathUtils.flattenInto(target, subMap, "customer");

            assertThat(target).containsEntry("customer.email", "a@b.com")
                    .containsEntry("customer.phone", "123");
        }

        @Test
        @DisplayName("recursively flattens nested sub-maps")
        void recursive() {
            Map<String, Object> target = new LinkedHashMap<>();
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("city", "Paris");
            Map<String, Object> subMap = new LinkedHashMap<>();
            subMap.put("address", address);

            PayloadPathUtils.flattenInto(target, subMap, "customer");

            assertThat(target).containsEntry("customer.address.city", "Paris");
        }
    }

    @Nested
    @DisplayName("reorderByInput")
    class ReorderByInput {

        @Test
        @DisplayName("keeps output ordered per input key order when no renames apply")
        void noRenames() {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("b", 1);
            input.put("a", 2);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("a", 2);
            output.put("b", 1);

            Map<String, Object> result = PayloadPathUtils.reorderByInput(input, output, List.of());

            assertThat(result.keySet()).containsExactly("b", "a");
        }

        @Test
        @DisplayName("follows renames declared by top-level mapping rules")
        void withRenames() {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("firstName", "Alice");
            input.put("lastName", "Doe");

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("lastName", "Doe");
            output.put("givenName", "Alice");

            MappingRuleData rule = new MappingRuleData(1L, "firstName", "givenName",
                    com.miniESB.domain.enums.MappingType.FIELD_PLACEMENT, null);

            Map<String, Object> result = PayloadPathUtils.reorderByInput(input, output, List.of(rule));

            assertThat(result.keySet()).containsExactly("givenName", "lastName");
        }

        @Test
        @DisplayName("appends output keys not present in input at the end")
        void appendsExtraKeys() {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("a", 1);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("a", 1);
            output.put("computed", 42);

            Map<String, Object> result = PayloadPathUtils.reorderByInput(input, output, List.of());

            assertThat(result.keySet()).containsExactly("a", "computed");
        }

        @Test
        @DisplayName("ignores rules with array-notation fields when building rename map")
        void ignoresArrayRules() {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("items", "irrelevant");

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("items", "irrelevant");

            MappingRuleData rule = new MappingRuleData(1L, "items[].price", "items[].amount",
                    com.miniESB.domain.enums.MappingType.FIELD_PLACEMENT, null);

            Map<String, Object> result = PayloadPathUtils.reorderByInput(input, output, List.of(rule));

            assertThat(result).containsEntry("items", "irrelevant");
        }
    }
}

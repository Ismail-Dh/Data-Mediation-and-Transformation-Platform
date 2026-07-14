package com.miniESB.engine.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FieldResolver implementations — unit tests")
class FieldResolversTest {

    @Nested
    @DisplayName("MapFieldResolver")
    class MapResolver {

        @Test
        @DisplayName("resolveAsText returns the string form of a nested value")
        void resolveAsText() {
            Map<String, Object> root = new HashMap<>(Map.of("customer", Map.of("age", 30)));
            MapFieldResolver resolver = new MapFieldResolver(root);
            assertThat(resolver.resolveAsText("customer.age")).isEqualTo("30");
        }

        @Test
        @DisplayName("resolveAsText returns null when the field is absent")
        void resolveAsTextMissing() {
            MapFieldResolver resolver = new MapFieldResolver(new HashMap<>());
            assertThat(resolver.resolveAsText("missing")).isNull();
        }

        @Test
        @DisplayName("isPresentAndNotNull reflects field presence")
        void isPresentAndNotNull() {
            Map<String, Object> root = new HashMap<>(Map.of("name", "Alice"));
            MapFieldResolver resolver = new MapFieldResolver(root);
            assertThat(resolver.isPresentAndNotNull("name")).isTrue();
            assertThat(resolver.isPresentAndNotNull("missing")).isFalse();
        }
    }

    @Nested
    @DisplayName("JsonNodeFieldResolver")
    class JsonResolver {

        private final ObjectMapper mapper = new ObjectMapper();

        @Test
        @DisplayName("resolveAsText returns the textual value for a text node")
        void resolveAsTextForTextNode() throws Exception {
            JsonNode root = mapper.readTree("{\"name\":\"Alice\"}");
            JsonNodeFieldResolver resolver = new JsonNodeFieldResolver(root);
            assertThat(resolver.resolveAsText("name")).isEqualTo("Alice");
        }

        @Test
        @DisplayName("resolveAsText stringifies non-textual nodes")
        void resolveAsTextForNonTextNode() throws Exception {
            JsonNode root = mapper.readTree("{\"age\":30}");
            JsonNodeFieldResolver resolver = new JsonNodeFieldResolver(root);
            assertThat(resolver.resolveAsText("age")).isEqualTo("30");
        }

        @Test
        @DisplayName("resolveAsText returns null for a missing or explicit null field")
        void resolveAsTextMissingOrNull() throws Exception {
            JsonNode root = mapper.readTree("{\"a\":null}");
            JsonNodeFieldResolver resolver = new JsonNodeFieldResolver(root);
            assertThat(resolver.resolveAsText("a")).isNull();
            assertThat(resolver.resolveAsText("missing")).isNull();
        }

        @Test
        @DisplayName("resolveAsText navigates nested paths")
        void resolveAsTextNested() throws Exception {
            JsonNode root = mapper.readTree("{\"customer\":{\"email\":\"a@b.com\"}}");
            JsonNodeFieldResolver resolver = new JsonNodeFieldResolver(root);
            assertThat(resolver.resolveAsText("customer.email")).isEqualTo("a@b.com");
        }

        @Test
        @DisplayName("isPresentAndNotNull is false for missing or null nodes, true otherwise")
        void isPresentAndNotNull() throws Exception {
            JsonNode root = mapper.readTree("{\"a\":1,\"b\":null}");
            JsonNodeFieldResolver resolver = new JsonNodeFieldResolver(root);
            assertThat(resolver.isPresentAndNotNull("a")).isTrue();
            assertThat(resolver.isPresentAndNotNull("b")).isFalse();
            assertThat(resolver.isPresentAndNotNull("c")).isFalse();
        }
    }
}

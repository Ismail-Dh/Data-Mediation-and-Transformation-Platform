package com.miniESB.engine.validation;

import com.fasterxml.jackson.databind.JsonNode;

/** Adapte un {@link JsonNode} (Jackson) en {@link FieldResolver}. */
public class JsonNodeFieldResolver implements FieldResolver {

    private final JsonNode root;

    public JsonNodeFieldResolver(JsonNode root) {
        this.root = root;
    }

    @Override
    public String resolveAsText(String path) {
        JsonNode node = resolvePath(path);
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        return node.isTextual() ? node.textValue() : node.toString();
    }

    @Override
    public boolean isPresentAndNotNull(String path) {
        JsonNode node = resolvePath(path);
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private JsonNode resolvePath(String path) {
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || current.isMissingNode()) return null;
            current = current.get(part);
        }
        return current;
    }
}

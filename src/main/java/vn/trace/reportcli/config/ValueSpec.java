package vn.trace.reportcli.config;

import com.fasterxml.jackson.databind.JsonNode;

public record ValueSpec(
        String path,
        String type,
        JsonNode defaultValue,
        String format,
        Integer width,
        Integer height
) {
    public String normalizedType() {
        return type == null || type.isBlank() ? "string" : type.trim().toLowerCase();
    }
}

package vn.trace.reportcli.model;

import com.fasterxml.jackson.databind.JsonNode;

public record BatchJob(
        String id,
        String template,
        String outputName,
        JsonNode data
) {}

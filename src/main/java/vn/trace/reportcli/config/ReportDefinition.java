package vn.trace.reportcli.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record ReportDefinition(
        String code,
        String jrxml,
        String defaultOutputName,
        String recordsPath,
        Map<String, ValueSpec> parameters
) {
    public ReportDefinition {
        parameters = parameters == null ? new LinkedHashMap<>() : parameters;
    }
}

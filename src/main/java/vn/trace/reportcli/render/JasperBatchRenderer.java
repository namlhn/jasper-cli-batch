package vn.trace.reportcli.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import vn.trace.reportcli.config.ReportDefinition;
import vn.trace.reportcli.config.ValueSpec;
import vn.trace.reportcli.model.BatchJob;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class JasperBatchRenderer {
    private final TemplateRegistry templates;
    private final ValueResolver values;

    public JasperBatchRenderer(TemplateRegistry templates, Path assetRoot) {
        this.templates = templates;
        this.values = new ValueResolver(assetRoot);
    }

    public RenderResult render(BatchJob job) throws Exception {
        ReportDefinition definition = templates.definition(job.template());
        JsonNode data = job.data() == null ? JsonNodeFactory.instance.objectNode() : job.data();

        Map<String, Object> parameters = new HashMap<>();
        for (Map.Entry<String, ValueSpec> entry : definition.parameters().entrySet()) {
            parameters.put(entry.getKey(), values.resolve(data, entry.getValue()));
        }

        JRDataSource dataSource = buildDataSource(data, definition.recordsPath());
        JasperPrint print = JasperFillManager.fillReport(
                templates.compiled(job.template()), parameters, dataSource);

        String name = firstNonBlank(job.outputName(), definition.defaultOutputName(), job.id(), job.template()) + ".pdf";
        return new RenderResult(name, print);
    }

    public Path exportSingle(RenderResult result, Path outputDir) throws Exception {
        Files.createDirectories(outputDir);
        Path output = safeOutput(outputDir, result.fileName());
        JasperExportManager.exportReportToPdfFile(result.print(), output.toString());
        return output;
    }

    public Path exportMerged(List<RenderResult> results, Path output) throws Exception {
        if (results.isEmpty()) throw new IllegalArgumentException("No reports to merge");
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(SimpleExporterInput.getInstance(
                results.stream().map(RenderResult::print).toList()));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output.toFile()));
        exporter.exportReport();
        return output;
    }

    public byte[] exportMergedBytes(List<RenderResult> results) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(SimpleExporterInput.getInstance(
                results.stream().map(RenderResult::print).toList()));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
        exporter.exportReport();
        return out.toByteArray();
    }

    private JRDataSource buildDataSource(JsonNode data, String recordsPath) {
        if (recordsPath == null || recordsPath.isBlank()) return new JREmptyDataSource(1);
        JsonNode rows = values.select(data, recordsPath);
        if (rows == null || !rows.isArray()) {
            throw new IllegalArgumentException("recordsPath does not point to an array: " + recordsPath);
        }
        List<Map<String, ?>> list = new ArrayList<>();
        for (JsonNode row : rows) {
            @SuppressWarnings("unchecked")
            Map<String, ?> map = vn.trace.reportcli.util.JsonSupport.JSON.convertValue(row, Map.class);
            list.add(map);
        }
        return new JRMapCollectionDataSource(list);
    }

    private static Path safeOutput(Path dir, String fileName) {
        String cleaned = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = dir.toAbsolutePath().normalize().resolve(cleaned).normalize();
        if (!target.startsWith(dir.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Invalid output file name: " + fileName);
        }
        return target;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "report";
    }

    public record RenderResult(String fileName, JasperPrint print) {}
}

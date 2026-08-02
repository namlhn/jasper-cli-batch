package vn.trace.reportcli.render;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.fill.JasperReportSource;
import net.sf.jasperreports.engine.fill.SimpleJasperReportSource;
import net.sf.jasperreports.repo.SimpleRepositoryResourceContext;
import vn.trace.reportcli.config.ReportDefinition;
import vn.trace.reportcli.util.JsonSupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class TemplateRegistry {
    private final Path configDir;
    private final Map<String, ReportDefinition> definitions = new HashMap<>();
    private final Map<String, JasperReport> compiled = new HashMap<>();

    public TemplateRegistry(Path configDir) throws IOException {
        this.configDir = configDir.toAbsolutePath().normalize();
        loadDefinitions();
    }

    private void loadDefinitions() throws IOException {
        if (!Files.isDirectory(configDir)) {
            throw new IOException("Template config directory does not exist: " + configDir);
        }
        try (Stream<Path> paths = Files.list(configDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".yaml") || p.getFileName().toString().endsWith(".yml"))
                    .forEach(path -> {
                        try {
                            ReportDefinition definition = JsonSupport.YAML.readValue(path.toFile(), ReportDefinition.class);
                            if (definition.code() == null || definition.code().isBlank()) {
                                throw new IllegalArgumentException("Missing code in " + path);
                            }
                            if (definitions.putIfAbsent(definition.code(), definition) != null) {
                                throw new IllegalArgumentException("Duplicate template code: " + definition.code());
                            }
                        } catch (Exception e) {
                            throw new TemplateLoadRuntimeException(path, e);
                        }
                    });
        } catch (TemplateLoadRuntimeException e) {
            throw new IOException("Cannot load template config " + e.path, e.getCause());
        }
    }

    public Path configDir() {
        return configDir;
    }

    public ReportDefinition definition(String code) {
        ReportDefinition definition = definitions.get(code);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown template code: " + code + ". Available: " + definitions.keySet());
        }
        return definition;
    }

    public synchronized JasperReportSource reportSource(String code) throws IOException, JRException {
        ReportDefinition definition = definition(code);
        Path jrxmlPath = configDir.resolve(definition.jrxml()).normalize();
        if (!jrxmlPath.startsWith(configDir)) {
            throw new IOException("JRXML path escapes config directory: " + definition.jrxml());
        }
        return SimpleJasperReportSource.from(
                compiled(code),
                jrxmlPath.toString(),
                SimpleRepositoryResourceContext.of(jrxmlPath.getParent().toString()));
    }

    public synchronized JasperReport compiled(String code) throws IOException, JRException {
        JasperReport cached = compiled.get(code);
        if (cached != null) return cached;

        ReportDefinition definition = definition(code);
        Path jrxmlPath = configDir.resolve(definition.jrxml()).normalize();
        if (!jrxmlPath.startsWith(configDir)) {
            throw new IOException("JRXML path escapes config directory: " + definition.jrxml());
        }
        try (InputStream input = Files.newInputStream(jrxmlPath)) {
            JasperReport report = JasperCompileManager.compileReport(input);
            compiled.put(code, report);
            return report;
        }
    }

    private static final class TemplateLoadRuntimeException extends RuntimeException {
        private final Path path;
        private TemplateLoadRuntimeException(Path path, Throwable cause) {
            super(cause);
            this.path = path;
        }
    }
}

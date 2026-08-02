package vn.trace.reportcli.render;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.trace.reportcli.model.BatchJob;
import vn.trace.reportcli.util.JsonSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JasperBatchRendererTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    @Test
    void rendersIndividualAndMergedPdfs(@TempDir Path outputDir) throws Exception {
        List<BatchJob> jobs = JsonSupport.JSON.readValue(
                PROJECT_ROOT.resolve("examples/batch-inline.json").toFile(),
                new TypeReference<>() {});
        TemplateRegistry registry = new TemplateRegistry(PROJECT_ROOT.resolve("config/templates"));
        JasperBatchRenderer renderer = new JasperBatchRenderer(registry, PROJECT_ROOT);
        List<JasperBatchRenderer.RenderResult> results = new ArrayList<>();

        for (BatchJob job : jobs) {
            JasperBatchRenderer.RenderResult result = renderer.render(job);
            Path pdf = renderer.exportSingle(result, outputDir);
            results.add(result);
            assertPdf(pdf);
        }

        Path merged = renderer.exportMerged(results, outputDir.resolve("merged.pdf"));
        assertPdf(merged);
        assertEquals(2, results.size());
    }

    @Test
    void cachesCompiledTemplates() throws Exception {
        TemplateRegistry registry = new TemplateRegistry(PROJECT_ROOT.resolve("config/templates"));

        assertSame(
                registry.compiled("DakaoCerN1"),
                registry.compiled("DakaoCerN1"));
    }

    @Test
    void rejectsUnknownTemplateCode() throws Exception {
        TemplateRegistry registry = new TemplateRegistry(PROJECT_ROOT.resolve("config/templates"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> registry.definition("DOES_NOT_EXIST"));
        assertTrue(error.getMessage().contains("Unknown template code"));
    }

    private static void assertPdf(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path));
        byte[] bytes = Files.readAllBytes(path);
        assertTrue(bytes.length > 100);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    }
}

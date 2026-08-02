package vn.trace.reportcli.sign;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.trace.reportcli.model.BatchJob;
import vn.trace.reportcli.render.JasperBatchRenderer;
import vn.trace.reportcli.render.TemplateRegistry;
import vn.trace.reportcli.util.JsonSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSignVerifyRoundTripTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final char[] PASSWORD = "test-password".toCharArray();

    @Test
    void generatesKeySignsAndVerifiesWithLocalTrust(@TempDir Path tempDir) throws Exception {
        Path input = renderPdf(tempDir);
        Path key = tempDir.resolve("signer.p12");
        Path signed = tempDir.resolve("signed.pdf");

        new SelfSignedKeyGenerator().generate(
                key, PASSWORD, "signer", "CN=Test PDF Signer", Duration.ofDays(30), 2048);
        new PdfSigner().sign(
                input, signed, key, PASSWORD, "signer", "B", null,
                null, null, false);

        PdfVerifier.VerificationResult trusted =
                new PdfVerifier().verify(signed, key, PASSWORD, false);
        PdfVerifier.VerificationResult untrusted =
                new PdfVerifier().verify(signed, null, null, false);

        assertTrue(Files.size(signed) > Files.size(input));
        assertTrue(trusted.valid(), trusted.summary());
        assertTrue(trusted.summary().contains("PAdES-BASELINE-B"), trusted.summary());
        assertFalse(untrusted.valid(), untrusted.summary());
    }

    @Test
    void rejectsWrongKeystorePassword(@TempDir Path tempDir) throws Exception {
        Path input = renderPdf(tempDir);
        Path key = tempDir.resolve("signer.p12");
        new SelfSignedKeyGenerator().generate(
                key, PASSWORD, "signer", "CN=Test PDF Signer", Duration.ofDays(30), 2048);

        assertThrows(Exception.class, () -> new PdfSigner().sign(
                input, tempDir.resolve("signed.pdf"), key, "wrong".toCharArray(), null,
                "B", null, null, null, false));
    }

    @Test
    void detectsChangedSignedBytes(@TempDir Path tempDir) throws Exception {
        Path input = renderPdf(tempDir);
        Path key = tempDir.resolve("signer.p12");
        Path signed = tempDir.resolve("signed.pdf");
        new SelfSignedKeyGenerator().generate(
                key, PASSWORD, "signer", "CN=Test PDF Signer", Duration.ofDays(30), 2048);
        new PdfSigner().sign(
                input, signed, key, PASSWORD, null, "B", null,
                null, null, false);

        byte[] bytes = Files.readAllBytes(signed);
        bytes[bytes.length / 3] ^= 1;
        Path changed = tempDir.resolve("changed.pdf");
        Files.write(changed, bytes);

        try {
            assertFalse(new PdfVerifier().verify(changed, key, PASSWORD, false).valid());
        } catch (RuntimeException expectedForStructurallyBrokenPdf) {
            assertTrue(expectedForStructurallyBrokenPdf.getMessage() != null);
        }
    }

    private static Path renderPdf(Path tempDir) throws Exception {
        List<BatchJob> jobs = JsonSupport.JSON.readValue(
                PROJECT_ROOT.resolve("examples/batch-inline.json").toFile(),
                new TypeReference<>() {});
        JasperBatchRenderer renderer = new JasperBatchRenderer(
                new TemplateRegistry(PROJECT_ROOT.resolve("config/templates")), PROJECT_ROOT);
        return renderer.exportSingle(renderer.render(jobs.getFirst()), tempDir.resolve("rendered"));
    }
}

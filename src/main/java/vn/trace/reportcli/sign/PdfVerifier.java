package vn.trace.reportcli.sign;

import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public final class PdfVerifier {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    public VerificationResult verify(
            Path input,
            Path truststore,
            char[] truststorePassword,
            boolean online
    ) throws Exception {
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("Signed PDF does not exist: " + input);
        }

        CommonCertificateVerifier verifier =
                DssSupport.certificateVerifier(truststore, truststorePassword, online);
        SignedDocumentValidator validator =
                SignedDocumentValidator.fromDocument(new FileDocument(input.toFile()));
        validator.setCertificateVerifier(verifier);
        Reports reports = validator.validateDocument();
        SimpleReport simple = reports.getSimpleReport();

        StringBuilder summary = new StringBuilder();
        summary.append("Document: ").append(input.toAbsolutePath()).append('\n');
        summary.append("Signatures: ").append(simple.getSignaturesCount())
                .append(", valid: ").append(simple.getValidSignaturesCount()).append('\n');

        boolean valid = simple.getSignaturesCount() > 0;
        for (String id : simple.getSignatureIdList()) {
            boolean signatureValid = simple.isValid(id);
            valid &= signatureValid;
            summary.append("- ID: ").append(id).append('\n');
            summary.append("  Signer: ").append(value(simple.getSignedBy(id))).append('\n');
            summary.append("  Profile: ").append(value(simple.getSignatureFormat(id))).append('\n');
            summary.append("  Signing time: ").append(date(simple.getSigningTime(id))).append('\n');
            summary.append("  Best signature time: ")
                    .append(date(simple.getBestSignatureTime(id))).append('\n');
            summary.append("  Result: ").append(value(simple.getIndication(id)));
            if (simple.getSubIndication(id) != null) {
                summary.append(" / ").append(simple.getSubIndication(id));
            }
            summary.append('\n');
        }
        if (simple.getSignaturesCount() == 0) {
            summary.append("Result: NO_SIGNATURE_FOUND\n");
        }
        return new VerificationResult(valid, summary.toString());
    }

    private static String date(Date date) {
        return date == null ? "-" : DATE_FORMAT.format(date.toInstant());
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }

    public record VerificationResult(boolean valid, String summary) {}
}

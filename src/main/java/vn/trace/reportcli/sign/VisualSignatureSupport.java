package vn.trace.reportcli.sign;

import eu.europa.esig.dss.enumerations.SignerTextHorizontalAlignment;
import eu.europa.esig.dss.enumerations.TextWrapping;
import eu.europa.esig.dss.enumerations.VisualSignatureAlignmentHorizontal;
import eu.europa.esig.dss.enumerations.VisualSignatureAlignmentVertical;
import eu.europa.esig.dss.enumerations.VisualSignatureRotation;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.pades.DSSFileFont;
import eu.europa.esig.dss.pades.DSSFont;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;

import java.awt.Color;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

final class VisualSignatureSupport {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private VisualSignatureSupport() {}

    static void apply(
            PAdESSignatureParameters parameters,
            DSSPrivateKeyEntry key,
            VisualSignatureOptions options
    ) throws Exception {
        if (!options.enabled()) return;

        SignatureImageParameters imageParameters = new SignatureImageParameters();
        SignatureFieldParameters fieldParameters = new SignatureFieldParameters();
        fieldParameters.setPage(options.page());
        fieldParameters.setWidth(options.width());
        fieldParameters.setHeight(options.height());
        fieldParameters.setRotation(VisualSignatureRotation.AUTOMATIC);
        if (options.bottomRight()) {
            fieldParameters.setOriginX(options.originX());
            fieldParameters.setOriginY(options.originY());
            imageParameters.setAlignmentHorizontal(VisualSignatureAlignmentHorizontal.RIGHT);
            imageParameters.setAlignmentVertical(VisualSignatureAlignmentVertical.BOTTOM);
        } else {
            fieldParameters.setOriginX(options.originX());
            fieldParameters.setOriginY(options.originY());
            imageParameters.setAlignmentHorizontal(VisualSignatureAlignmentHorizontal.NONE);
            imageParameters.setAlignmentVertical(VisualSignatureAlignmentVertical.NONE);
        }
        imageParameters.setFieldParameters(fieldParameters);
        imageParameters.setBackgroundColor(TRANSPARENT);

        if (options.image() != null) {
            if (!Files.isRegularFile(options.image())) {
                throw new IllegalArgumentException("Signature image does not exist: " + options.image());
            }
            imageParameters.setImage(new FileDocument(options.image().toFile()));
        }

        SignatureImageTextParameters textParameters = new SignatureImageTextParameters();
        textParameters.setFont(signatureFont(options.fontSize()));
        textParameters.setText(resolveText(parameters, key, options.text()));
        textParameters.setTextColor(new Color(30, 30, 30));
        textParameters.setBackgroundColor(TRANSPARENT);
        textParameters.setPadding(2f);
        textParameters.setTextWrapping(TextWrapping.FONT_BASED);
        textParameters.setSignerTextHorizontalAlignment(SignerTextHorizontalAlignment.RIGHT);
        imageParameters.setTextParameters(textParameters);

        parameters.setImageParameters(imageParameters);
        parameters.setSignerName(extractCommonName(key));
    }

    private static String resolveText(
            PAdESSignatureParameters parameters,
            DSSPrivateKeyEntry key,
            String customText
    ) {
        Date signingDate = parameters.bLevel().getSigningDate();
        String when = signingDate == null ? "-" : DATE_FORMAT.format(signingDate.toInstant());
        if (customText != null && !customText.isBlank()) {
            return customText.trim() + "\n" + when;
        }
        return extractCommonName(key) + "\n" + when;
    }

    private static DSSFont signatureFont(float size) throws Exception {
        try (InputStream stream = VisualSignatureSupport.class.getResourceAsStream("/fonts/TimesNewRoman-Regular.ttf")) {
            if (stream == null) {
                throw new IllegalStateException("Bundled font not found: /fonts/TimesNewRoman-Regular.ttf");
            }
            DSSFileFont font = new DSSFileFont(stream);
            font.setSize(size);
            return font;
        }
    }

    private static String extractCommonName(DSSPrivateKeyEntry key) {
        String subject = key.getCertificate().getSubject().getPrettyPrintRFC2253();
        for (String part : subject.split(",")) {
            String trimmed = part.trim();
            if (trimmed.regionMatches(true, 0, "CN=", 0, 3)) {
                return trimmed.substring(3).trim();
            }
        }
        return subject;
    }
}

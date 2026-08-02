package vn.trace.reportcli.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import vn.trace.reportcli.sign.PdfSigner;
import vn.trace.reportcli.sign.VisualSignatureOptions;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "sign",
        mixinStandardHelpOptions = true,
        description = "Sign a PDF with a PKCS#12 key using a PAdES baseline profile."
)
public final class SignCommand implements Callable<Integer> {
    @Option(names = {"-i", "--input"}, required = true, description = "Input PDF")
    private Path input;

    @Option(names = {"-o", "--output"}, required = true, description = "Signed output PDF")
    private Path output;

    @Option(names = {"-k", "--keystore"}, required = true, description = "PKCS#12/PFX signing key")
    private Path keystore;

    @Option(names = {"-p", "--password"}, description = "Keystore password (prefer JASPER_KEYSTORE_PASSWORD)")
    private String password;

    @Option(names = "--alias", description = "Key alias; required when the keystore has multiple keys")
    private String alias;

    @Option(names = "--level", defaultValue = "B", description = "PAdES baseline level: B, T, LT or LTA")
    private String level;

    @Option(names = "--tsa-url", description = "RFC 3161 TSA URL; required for T, LT and LTA")
    private String tsaUrl;

    @Option(names = "--truststore", description = "PKCS#12/JKS truststore used while building LT/LTA material")
    private Path truststore;

    @Option(names = "--truststore-password", description = "Truststore password (prefer JASPER_TRUSTSTORE_PASSWORD)")
    private String truststorePassword;

    @Option(names = "--online", description = "Enable AIA, OCSP and CRL downloads for LT/LTA")
    private boolean online;

    @Option(names = "--invisible", description = "Sign without a visible signature box on the PDF")
    private boolean invisible;

    @Option(names = "--sign-page", defaultValue = "1", description = "Page number for the visible signature box (1-based)")
    private int signPage;

    @Option(names = "--sign-x", defaultValue = "12", description = "Margin from page edge in PDF points; with bottom-right anchor this is horizontal inset")
    private float signX;

    @Option(names = "--sign-y", defaultValue = "12", description = "Margin from page edge in PDF points; with bottom-right anchor this is vertical inset")
    private float signY;

    @Option(names = "--sign-width", defaultValue = "180", description = "Visible signature width in PDF points")
    private float signWidth;

    @Option(names = "--sign-height", defaultValue = "42", description = "Visible signature height in PDF points")
    private float signHeight;

    @Option(names = "--sign-font-size", defaultValue = "8", description = "Visible signature font size")
    private float signFontSize;

    @Option(names = "--force", description = "Overwrite an existing output PDF")
    private boolean force;

    @Option(names = "--sign-text", description = "Optional signer label; signing date/time is appended automatically")
    private String signText;

    @Option(names = "--sign-image", description = "Optional image shown inside the visible signature box")
    private Path signImage;

    @Override
    public Integer call() throws Exception {
        if (signPage < 1) throw new IllegalArgumentException("--sign-page must be >= 1");
        if (signWidth <= 0 || signHeight <= 0) {
            throw new IllegalArgumentException("--sign-width and --sign-height must be positive");
        }
        if (signFontSize < 8f || signFontSize > 12f) {
            throw new IllegalArgumentException("--sign-font-size must be between 8 and 12");
        }

        char[] keyPassword = CliPasswords.resolve(password, "JASPER_KEYSTORE_PASSWORD");
        char[] trustPassword = truststore == null ? null : CliPasswords.resolveTruststore(truststorePassword);
        VisualSignatureOptions visualSignature = invisible
                ? VisualSignatureOptions.disabled()
                : new VisualSignatureOptions(
                        true, signPage, signX, signY, signWidth, signHeight,
                        signText, signImage, true, signFontSize);
        try {
            PdfSigner signer = new PdfSigner();
            signer.sign(input, output, keystore, keyPassword, alias, level, tsaUrl,
                    truststore, trustPassword, online, visualSignature, force);
            System.out.println("Signed PDF -> " + output.toAbsolutePath());
            return 0;
        } finally {
            java.util.Arrays.fill(keyPassword, '\0');
            if (trustPassword != null) java.util.Arrays.fill(trustPassword, '\0');
        }
    }
}

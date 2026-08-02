package vn.trace.reportcli.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import vn.trace.reportcli.sign.PdfSigner;

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

    @Override
    public Integer call() throws Exception {
        char[] keyPassword = CliPasswords.resolve(password, "JASPER_KEYSTORE_PASSWORD");
        char[] trustPassword = truststore == null ? null : CliPasswords.resolveTruststore(truststorePassword);
        try {
            PdfSigner signer = new PdfSigner();
            signer.sign(input, output, keystore, keyPassword, alias, level, tsaUrl,
                    truststore, trustPassword, online);
            System.out.println("Signed PDF -> " + output.toAbsolutePath());
            return 0;
        } finally {
            java.util.Arrays.fill(keyPassword, '\0');
            if (trustPassword != null) java.util.Arrays.fill(trustPassword, '\0');
        }
    }
}

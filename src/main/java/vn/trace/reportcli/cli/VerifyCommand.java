package vn.trace.reportcli.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import vn.trace.reportcli.sign.PdfVerifier;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "verify",
        mixinStandardHelpOptions = true,
        description = "Validate signatures and trust in a signed PDF."
)
public final class VerifyCommand implements Callable<Integer> {
    @Option(names = {"-i", "--input"}, required = true, description = "Signed PDF")
    private Path input;

    @Option(names = "--truststore", description = "Trusted certificates in PKCS#12 or JKS format")
    private Path truststore;

    @Option(names = "--truststore-password", description = "Truststore password (prefer JASPER_TRUSTSTORE_PASSWORD)")
    private String truststorePassword;

    @Option(names = "--online", description = "Enable AIA, OCSP and CRL downloads")
    private boolean online;

    @Override
    public Integer call() throws Exception {
        char[] password = truststore == null ? null : CliPasswords.resolveTruststore(truststorePassword);
        try {
            PdfVerifier.VerificationResult result =
                    new PdfVerifier().verify(input, truststore, password, online);
            System.out.print(result.summary());
            return result.valid() ? 0 : 2;
        } finally {
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }
}

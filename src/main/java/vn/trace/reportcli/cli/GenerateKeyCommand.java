package vn.trace.reportcli.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import vn.trace.reportcli.sign.SelfSignedKeyGenerator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

@Command(
        name = "generate-key",
        mixinStandardHelpOptions = true,
        description = "Generate a development PKCS#12 key with a self-signed certificate."
)
public final class GenerateKeyCommand implements Callable<Integer> {
    @Option(names = {"-o", "--output"}, required = true, description = "Output PKCS#12 file")
    private Path output;

    @Option(names = {"-p", "--password"}, description = "Keystore password (prefer JASPER_KEYSTORE_PASSWORD)")
    private String password;

    @Option(names = "--alias", defaultValue = "signing-key", description = "Private key alias")
    private String alias;

    @Option(names = "--dn", defaultValue = "CN=Local PDF Signer", description = "X.500 subject name")
    private String distinguishedName;

    @Option(names = "--days", defaultValue = "365", description = "Certificate validity in days")
    private long validityDays;

    @Option(names = "--key-size", defaultValue = "3072", description = "RSA key size (minimum 2048)")
    private int keySize;

    @Override
    public Integer call() throws Exception {
        if (validityDays < 1) throw new IllegalArgumentException("--days must be positive");
        if (keySize < 2048) throw new IllegalArgumentException("--key-size must be at least 2048");

        char[] keyPassword = CliPasswords.resolve(password, "JASPER_KEYSTORE_PASSWORD");
        try {
            new SelfSignedKeyGenerator().generate(
                    output, keyPassword, alias, distinguishedName, Duration.ofDays(validityDays), keySize);
            System.out.println("Development PKCS#12 -> " + output.toAbsolutePath());
            System.out.println("WARNING: self-signed certificates are for local development only.");
            return 0;
        } finally {
            java.util.Arrays.fill(keyPassword, '\0');
        }
    }
}

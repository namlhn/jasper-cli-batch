package vn.trace.reportcli.sign;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.SecureRandomNonceSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class PdfSigner {
    public void sign(
            Path input,
            Path output,
            Path keystore,
            char[] password,
            String alias,
            String levelName,
            String tsaUrl,
            Path truststore,
            char[] truststorePassword,
            boolean online
    ) throws Exception {
        requireRegularFile(input, "Input PDF");
        requireRegularFile(keystore, "Signing keystore");
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Refusing to overwrite existing output: " + output);
        }

        SignatureLevel level = parseLevel(levelName);
        boolean timestamped = level != SignatureLevel.PAdES_BASELINE_B;
        boolean longTerm = level == SignatureLevel.PAdES_BASELINE_LT
                || level == SignatureLevel.PAdES_BASELINE_LTA;
        if (timestamped && (tsaUrl == null || tsaUrl.isBlank())) {
            throw new IllegalArgumentException("--tsa-url is required for level " + levelName);
        }
        if (longTerm && (truststore == null || !online)) {
            throw new IllegalArgumentException(
                    "LT/LTA signing requires --truststore and --online for trust and revocation data");
        }

        CommonCertificateVerifier verifier =
                DssSupport.certificateVerifier(truststore, truststorePassword, online);
        PAdESService service = new PAdESService(verifier);
        if (timestamped) service.setTspSource(timestampSource(tsaUrl));

        DSSDocument document = new FileDocument(input.toFile());
        try (Pkcs12SignatureToken token = new Pkcs12SignatureToken(
                keystore.toFile(), new KeyStore.PasswordProtection(password))) {
            DSSPrivateKeyEntry key = selectKey(token, keystore, password, alias);
            PAdESSignatureParameters parameters = new PAdESSignatureParameters();
            parameters.setSignatureLevel(level);
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(key.getCertificate());
            parameters.setCertificateChain(key.getCertificateChain());
            parameters.bLevel().setSigningDate(new java.util.Date());

            ToBeSigned dataToSign = service.getDataToSign(document, parameters);
            SignatureValue signatureValue =
                    token.sign(dataToSign, parameters.getDigestAlgorithm(), key);
            DSSDocument signed = service.signDocument(document, parameters, signatureValue);

            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (OutputStream stream = Files.newOutputStream(output)) {
                signed.writeTo(stream);
            }
        }
    }

    private static DSSPrivateKeyEntry selectKey(
            Pkcs12SignatureToken token,
            Path keystore,
            char[] password,
            String alias
    ) throws Exception {
        List<DSSPrivateKeyEntry> keys = token.getKeys();
        if (keys.isEmpty()) throw new IllegalArgumentException("No private key found in " + keystore);
        if (alias == null || alias.isBlank()) {
            if (keys.size() == 1) return keys.getFirst();
            throw new IllegalArgumentException(
                    "Keystore contains multiple private keys; select one with --alias");
        }

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(keystore)) {
            store.load(stream, password);
        }
        if (!store.isKeyEntry(alias)) {
            throw new IllegalArgumentException("Private key alias not found: " + alias);
        }
        Certificate selected = store.getCertificate(alias);
        byte[] encoded = selected.getEncoded();
        return keys.stream()
                .filter(key -> Arrays.equals(encoded, key.getCertificate().getEncoded()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unable to match DSS private key for alias: " + alias));
    }

    private static OnlineTSPSource timestampSource(String tsaUrl) {
        CommonsDataLoader loader = new CommonsDataLoader();
        loader.setTimeoutConnection(10_000);
        loader.setTimeoutConnectionRequest(10_000);
        loader.setTimeoutResponse(20_000);
        loader.setTimeoutSocket(20_000);
        OnlineTSPSource source = new OnlineTSPSource(tsaUrl, loader);
        source.setNonceSource(new SecureRandomNonceSource());
        return source;
    }

    private static SignatureLevel parseLevel(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "B", "BASELINE_B", "PADES_BASELINE_B" -> SignatureLevel.PAdES_BASELINE_B;
            case "T", "BASELINE_T", "PADES_BASELINE_T" -> SignatureLevel.PAdES_BASELINE_T;
            case "LT", "BASELINE_LT", "PADES_BASELINE_LT" -> SignatureLevel.PAdES_BASELINE_LT;
            case "LTA", "BASELINE_LTA", "PADES_BASELINE_LTA" -> SignatureLevel.PAdES_BASELINE_LTA;
            default -> throw new IllegalArgumentException(
                    "Unsupported PAdES level '" + value + "'; use B, T, LT or LTA");
        };
    }

    private static void requireRegularFile(Path path, String label) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(label + " does not exist: " + path);
        }
    }
}

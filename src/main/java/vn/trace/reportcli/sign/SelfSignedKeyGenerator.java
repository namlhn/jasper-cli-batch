package vn.trace.reportcli.sign;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public final class SelfSignedKeyGenerator {
    public void generate(
            Path output,
            char[] password,
            String alias,
            String distinguishedName,
            Duration validity,
            int keySize
    ) throws Exception {
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Refusing to overwrite existing keystore: " + output);
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(keySize, SecureRandom.getInstanceStrong());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        Instant now = Instant.now();
        X500Name subject = new X500Name(distinguishedName);
        BigInteger serial = new BigInteger(160, new SecureRandom()).abs().add(BigInteger.ONE);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                Date.from(now.minus(Duration.ofMinutes(5))),
                Date.from(now.plus(validity)),
                subject,
                keyPair.getPublic());

        JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(
                Extension.subjectKeyIdentifier, false,
                extensions.createSubjectKeyIdentifier(keyPair.getPublic()));
        builder.addExtension(
                Extension.authorityKeyIdentifier, false,
                extensions.createAuthorityKeyIdentifier(keyPair.getPublic()));

        ContentSigner certificateSigner =
                new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(certificateSigner);
        X509Certificate certificate = (X509Certificate) CertificateFactory
                .getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(holder.getEncoded()));
        certificate.verify(keyPair.getPublic());

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password);
        keyStore.setKeyEntry(
                alias, keyPair.getPrivate(), password, new Certificate[]{certificate});
        try (OutputStream stream = Files.newOutputStream(output)) {
            keyStore.store(stream, password);
        }
    }
}

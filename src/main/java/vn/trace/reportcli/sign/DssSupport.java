package vn.trace.reportcli.sign;

import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.spi.x509.KeyStoreCertificateSource;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;

import java.nio.file.Path;
import java.util.Locale;

final class DssSupport {
    private DssSupport() {}

    static CommonCertificateVerifier certificateVerifier(
            Path truststore,
            char[] truststorePassword,
            boolean online
    ) throws Exception {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        if (truststore != null) {
            String type = truststore.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jks")
                    ? "JKS" : "PKCS12";
            KeyStoreCertificateSource source =
                    new KeyStoreCertificateSource(truststore.toFile(), type, truststorePassword);
            CommonTrustedCertificateSource trusted = new CommonTrustedCertificateSource();
            trusted.importAsTrusted(source);
            verifier.setTrustedCertSources(trusted);
        }

        if (online) {
            CommonsDataLoader loader = new CommonsDataLoader();
            loader.setTimeoutConnection(10_000);
            loader.setTimeoutConnectionRequest(10_000);
            loader.setTimeoutResponse(15_000);
            loader.setTimeoutSocket(15_000);
            verifier.setAIASource(new DefaultAIASource(loader));
            verifier.setOcspSource(new OnlineOCSPSource(loader));
            verifier.setCrlSource(new OnlineCRLSource(loader));
        }
        return verifier;
    }
}

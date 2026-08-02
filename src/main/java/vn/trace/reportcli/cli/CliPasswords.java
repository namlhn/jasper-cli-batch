package vn.trace.reportcli.cli;

final class CliPasswords {
    private CliPasswords() {}

    static char[] resolve(String optionValue, String environmentName) {
        String value = optionValue;
        if (value == null || value.isEmpty()) {
            value = System.getenv(environmentName);
        }
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Password is required via --password or environment variable " + environmentName);
        }
        return value.toCharArray();
    }

    static char[] resolveTruststore(String optionValue) {
        String value = optionValue;
        if (value == null || value.isEmpty()) {
            value = System.getenv("JASPER_TRUSTSTORE_PASSWORD");
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "Truststore password is required via --truststore-password "
                            + "or environment variable JASPER_TRUSTSTORE_PASSWORD");
        }
        return value.toCharArray();
    }
}

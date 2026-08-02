package vn.trace.reportcli.sign;

import java.nio.file.Path;

public record VisualSignatureOptions(
        boolean enabled,
        int page,
        float originX,
        float originY,
        float width,
        float height,
        String text,
        Path image,
        boolean bottomRight,
        float fontSize
) {
    public static VisualSignatureOptions disabled() {
        return new VisualSignatureOptions(false, 1, 0, 0, 0, 0, null, null, true, 8f);
    }
}

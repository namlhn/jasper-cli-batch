package vn.trace.reportcli.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import vn.trace.reportcli.config.ValueSpec;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public final class ValueResolver {
    private final Path assetRoot;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ValueResolver(Path assetRoot) {
        this.assetRoot = assetRoot.toAbsolutePath().normalize();
    }

    public Object resolve(JsonNode data, ValueSpec spec) throws Exception {
        JsonNode node = select(data, spec.path());
        if ((node == null || node.isMissingNode() || node.isNull()) && spec.defaultValue() != null) {
            node = spec.defaultValue();
        }
        if (node == null || node.isMissingNode() || node.isNull()) return null;

        return switch (spec.normalizedType()) {
            case "string" -> node.isTextual() ? node.asText() : node.toString();
            case "integer", "int" -> node.asInt();
            case "long" -> node.asLong();
            case "double", "number" -> node.asDouble();
            case "boolean", "bool" -> node.asBoolean();
            case "date" -> formatDate(node.asText(), spec.format());
            case "assetpath", "asset_path" -> resolveAssetPath(node.asText());
            case "image" -> loadImage(node.asText());
            case "qr" -> createQr(node.asText(), spec.width(), spec.height());
            default -> throw new IllegalArgumentException("Unsupported parameter type: " + spec.type());
        };
    }

    public JsonNode select(JsonNode root, String path) {
        if (path == null || path.isBlank() || path.equals("$")) return root;
        if (path.startsWith("/")) return root.at(path);
        JsonNode current = root;
        for (String token : path.split("\\.")) {
            if (current == null) return null;
            current = current.path(token);
        }
        return current;
    }

    private String formatDate(String raw, String pattern) {
        DateTimeFormatter output = DateTimeFormatter.ofPattern(
                pattern == null || pattern.isBlank() ? "dd/MM/yyyy" : pattern);
        try {
            return OffsetDateTime.parse(raw).format(output);
        } catch (Exception ignored) {
            return LocalDate.parse(raw).format(output);
        }
    }

    private String resolveAssetPath(String location) throws Exception {
        Path path = assetRoot.resolve(location).normalize();
        if (!path.startsWith(assetRoot)) {
            throw new IllegalArgumentException("Path escapes asset root: " + location);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Asset file not found: " + location);
        }
        return path.toString();
    }

    private BufferedImage createQr(String content, Integer width, Integer height) throws Exception {
        int w = width == null ? 240 : width;
        int h = height == null ? 240 : height;
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, w, h);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private BufferedImage loadImage(String location) throws Exception {
        byte[] bytes;
        if (location.startsWith("data:")) {
            int comma = location.indexOf(',');
            if (comma < 0) throw new IllegalArgumentException("Invalid data URI image");
            bytes = Base64.getDecoder().decode(location.substring(comma + 1));
        } else if (location.startsWith("http://") || location.startsWith("https://")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(location))
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Cannot download image " + location + ": HTTP " + response.statusCode());
            }
            bytes = response.body();
        } else {
            Path path = assetRoot.resolve(location).normalize();
            if (!path.startsWith(assetRoot)) throw new IllegalArgumentException("Image path escapes asset root: " + location);
            bytes = Files.readAllBytes(path);
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IllegalArgumentException("Unsupported image: " + location);
        return image;
    }
}

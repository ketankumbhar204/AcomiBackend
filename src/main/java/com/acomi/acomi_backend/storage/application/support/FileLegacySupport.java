package com.acomi.acomi_backend.storage.application.support;

import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class FileLegacySupport {

    public static final String PENDING_UPLOAD = "pending-upload";
    public static final String MARKER_PREFIX = "acomi-file:";

    private static final Pattern DATA_URI = Pattern.compile("^data:([^;,]+)?(;base64)?,(.+)$", Pattern.DOTALL);

    private FileLegacySupport() {}

    public static String marker(UUID fileId) {
        return MARKER_PREFIX + fileId;
    }

    public static boolean isMarker(String value) {
        return StringUtils.hasText(value) && value.startsWith(MARKER_PREFIX);
    }

    public static UUID parseMarker(String value) {
        if (!isMarker(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.substring(MARKER_PREFIX.length()).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static boolean isPendingPlaceholder(String value) {
        return PENDING_UPLOAD.equalsIgnoreCase(value == null ? "" : value.trim());
    }

    public static boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
    }

    public static boolean isInlinePayload(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("data:image/")
                || (trimmed.length() >= 32 && trimmed.matches("^[A-Za-z0-9+/=\r\n]+$"));
    }

    public static boolean isLocalFileUri(String value) {
        return StringUtils.hasText(value) && value.trim().toLowerCase(Locale.ROOT).startsWith("file://");
    }

    public static boolean isDisplayableLegacy(String value) {
        return isHttpUrl(value) || isInlinePayload(value);
    }

    public static DecodedImage decodeImagePayload(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        Matcher matcher = DATA_URI.matcher(trimmed);
        if (matcher.matches()) {
            String mime = matcher.group(1);
            String payload = matcher.group(3);
            byte[] bytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
            return new DecodedImage(mime == null || mime.isBlank() ? "image/jpeg" : mime, bytes);
        }
        if (trimmed.matches("^[A-Za-z0-9+/=\r\n]+$")) {
            byte[] bytes = Base64.getDecoder().decode(trimmed.replaceAll("\\s", ""));
            return new DecodedImage("image/jpeg", bytes);
        }
        return null;
    }

    public record DecodedImage(String contentType, byte[] bytes) {}
}

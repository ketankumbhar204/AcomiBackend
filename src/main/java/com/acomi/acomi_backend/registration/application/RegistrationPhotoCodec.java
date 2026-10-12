package com.acomi.acomi_backend.registration.application;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Server-side checks for listing photos. The public registration flow has no user account, so
 * photos arrive as data URLs on the lead and are validated here before they are stored.
 */
public final class RegistrationPhotoCodec {

    public static final int MAX_PHOTOS = 3;
    public static final int MAX_BYTES = 3 * 1024 * 1024;

    private RegistrationPhotoCodec() {}

    public record DecodedPhoto(String contentType, String filename, byte[] bytes) {}

    public static List<DecodedPhoto> decodeAll(List<String> rawPhotos) {
        if (rawPhotos == null || rawPhotos.isEmpty()) {
            return List.of();
        }
        if (rawPhotos.size() > MAX_PHOTOS) {
            throw new BusinessException("You can upload at most 3 photos");
        }
        List<DecodedPhoto> decoded = new ArrayList<>();
        for (int index = 0; index < rawPhotos.size(); index++) {
            decoded.add(decodeOne(rawPhotos.get(index), index + 1));
        }
        return List.copyOf(decoded);
    }

    static DecodedPhoto decodeOne(String raw, int position) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("Photo " + position + " is empty");
        }
        String value = raw.trim();
        String declared = "image/jpeg";
        String encoded = value;
        if (value.regionMatches(true, 0, "data:", 0, 5)) {
            int comma = value.indexOf(',');
            if (comma < 0) {
                throw new BusinessException("Photo " + position + " is not a valid image");
            }
            String header = value.substring(5, comma).toLowerCase(Locale.ROOT);
            if (!header.contains(";base64")) {
                throw new BusinessException("Photo " + position + " must be a base64 image");
            }
            declared = header.substring(0, header.indexOf(';')).trim();
            encoded = value.substring(comma + 1);
        }
        String contentType = normalizeType(declared);
        if (contentType == null) {
            throw new BusinessException("Photo " + position + " must be a JPEG, PNG, or WebP image");
        }
        // Reject obviously oversized payloads before allocating the decoded bytes.
        if (encoded.length() > ((MAX_BYTES + 2) / 3) * 4 + 8) {
            throw new BusinessException("Each photo must be 3 MB or smaller");
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.replaceAll("\\s", ""));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Photo " + position + " is not a valid image");
        }
        if (bytes.length == 0) {
            throw new BusinessException("Photo " + position + " is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException("Each photo must be 3 MB or smaller");
        }
        String sniffed = sniff(bytes);
        if (sniffed == null || !sniffed.equals(contentType)) {
            throw new BusinessException("Photo " + position + " must be a JPEG, PNG, or WebP image");
        }
        return new DecodedPhoto(contentType, "photo-" + position + extension(contentType), bytes);
    }

    static String normalizeType(String declared) {
        if (declared == null) {
            return null;
        }
        return switch (declared.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "image/jpeg";
            case "image/png" -> "image/png";
            case "image/webp" -> "image/webp";
            default -> null;
        };
    }

    static String sniff(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}

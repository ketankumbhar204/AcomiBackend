package com.acomi.acomi_backend.storage.application.support;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.storage.domain.model.FileErrorCodes;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FileValidationService {

    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public String normalizeContentType(String declared) {
        if (!StringUtils.hasText(declared)) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TYPE_NOT_ALLOWED, "Content type is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = declared.trim().toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if (!IMAGE_TYPES.contains(normalized)) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TYPE_NOT_ALLOWED,
                    "Only JPEG, PNG, and WebP images are allowed",
                    HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    public void validateSize(FilePurpose purpose, long byteSize) {
        if (byteSize <= 0) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TOO_LARGE, "File size is required", HttpStatus.BAD_REQUEST);
        }
        if (byteSize > purpose.maxBytes()) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TOO_LARGE,
                    "File exceeds the maximum size of " + purpose.maxBytes() + " bytes",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public String sanitizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return null;
        }
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\x00-\\x1F<>:\"|?*]", "_").trim();
        if (name.contains("..") || name.isBlank()) {
            return null;
        }
        if (name.length() > 255) {
            name = name.substring(0, 255);
        }
        return name;
    }

    public void validateMagicBytes(String contentType, byte[] prefix) {
        if (prefix == null || prefix.length < 12) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TYPE_NOT_ALLOWED,
                    "File signature could not be verified",
                    HttpStatus.BAD_REQUEST);
        }
        boolean ok =
                switch (contentType) {
                    case "image/jpeg" -> prefix[0] == (byte) 0xFF
                            && prefix[1] == (byte) 0xD8
                            && prefix[2] == (byte) 0xFF;
                    case "image/png" -> prefix[0] == (byte) 0x89
                            && prefix[1] == 0x50
                            && prefix[2] == 0x4E
                            && prefix[3] == 0x47;
                    case "image/webp" -> prefix[0] == 'R'
                            && prefix[1] == 'I'
                            && prefix[2] == 'F'
                            && prefix[3] == 'F'
                            && prefix[8] == 'W'
                            && prefix[9] == 'E'
                            && prefix[10] == 'B'
                            && prefix[11] == 'P';
                    default -> false;
                };
        if (!ok) {
            throw new BusinessException(
                    FileErrorCodes.FILE_TYPE_NOT_ALLOWED,
                    "File contents do not match the declared image type",
                    HttpStatus.BAD_REQUEST);
        }
    }
}

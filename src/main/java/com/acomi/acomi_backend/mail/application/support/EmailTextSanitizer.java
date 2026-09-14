package com.acomi.acomi_backend.mail.application.support;

/**
 * Strips CR/LF and control characters from header-sensitive values so
 * user-controlled text cannot inject extra SMTP headers.
 */
public final class EmailTextSanitizer {

    private static final int MAX_SUBJECT_LENGTH = 180;
    private static final int MAX_HEADER_LENGTH = 255;

    private EmailTextSanitizer() {}

    public static String subject(String raw) {
        String cleaned = header(raw);
        if (cleaned.length() > MAX_SUBJECT_LENGTH) {
            return cleaned.substring(0, MAX_SUBJECT_LENGTH).trim();
        }
        return cleaned;
    }

    public static String header(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace('\0', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll(" +", " ")
                .trim();
        if (cleaned.length() > MAX_HEADER_LENGTH) {
            return cleaned.substring(0, MAX_HEADER_LENGTH).trim();
        }
        return cleaned;
    }

    /** Single body line: keep text, flatten CR/LF so fields cannot look like headers. */
    public static String plainLine(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\0', ' ').replace('\r', ' ').replace('\n', ' ').trim();
    }

    /** Escapes text for HTML email bodies. Uses {@link #plainLine} first. */
    public static String html(String raw) {
        String line = plainLine(raw);
        if (line.isEmpty()) {
            return "";
        }
        return line.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

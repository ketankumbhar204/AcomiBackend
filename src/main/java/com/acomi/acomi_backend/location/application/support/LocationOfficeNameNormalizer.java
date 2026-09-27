package com.acomi.acomi_backend.location.application.support;

import java.util.regex.Pattern;

/**
 * Narrow display/search normalization for India Post office-type suffixes.
 * Does not mutate the original {@code officeName}.
 *
 * <p>Strips a trailing {@code S.O}, {@code B.O}, or {@code H.O} (optional trailing
 * period) and an optional parenthetical that follows that suffix only, e.g.
 * {@code "Bhainsa S.O (Adilabad)"} → {@code "Bhainsa"}. Parentheticals that appear
 * before the suffix, such as {@code "Arli (T) B.O"}, are preserved.
 */
public final class LocationOfficeNameNormalizer {

    private static final Pattern POSTAL_OFFICE_SUFFIX = Pattern.compile(
            "(?i)\\s+(S\\.O|B\\.O|H\\.O)\\.?(?:\\s*\\([^)]*\\))?\\s*$");

    private LocationOfficeNameNormalizer() {}

    public static String toDisplayName(String officeName) {
        if (officeName == null) {
            return "";
        }
        String trimmed = officeName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return POSTAL_OFFICE_SUFFIX.matcher(trimmed).replaceFirst("").trim();
    }
}

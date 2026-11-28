package com.acomi.acomi_backend.admin.application.support;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Allowed {@code {{placeholder}}} tokens for custom listing marketing templates.
 * Keep in sync with web/mobile client allowlists.
 */
public final class ListingMarketingPlaceholders {

    public static final Set<String> ALLOWED = Set.of(
            "ownerName",
            "propertyName",
            "messName",
            "name",
            "spaceType",
            "location",
            "address",
            "rent",
            "rentOrPrice",
            "sharing",
            "sharingNotes",
            "amenities",
            "foodIncluded",
            "genderPolicy",
            "description");

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*\\}\\}");

    private ListingMarketingPlaceholders() {}

    public static Set<String> extract(String body) {
        Set<String> found = new LinkedHashSet<>();
        if (body == null || body.isBlank()) {
            return found;
        }
        Matcher matcher = TOKEN.matcher(body);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    public static Set<String> unsupported(String body) {
        Set<String> unsupported = new LinkedHashSet<>();
        for (String token : extract(body)) {
            if (!ALLOWED.contains(token)) {
                unsupported.add(token);
            }
        }
        return unsupported;
    }

    public static boolean isAllowed(String token) {
        return token != null && ALLOWED.contains(token.trim());
    }

    public static String normalizeApplicabilityLabel(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}

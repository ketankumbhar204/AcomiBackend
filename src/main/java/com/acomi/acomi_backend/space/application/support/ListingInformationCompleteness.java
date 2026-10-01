package com.acomi.acomi_backend.space.application.support;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.space.application.service.DiscoverListingSanitizer;
import java.util.Collection;
import java.util.Locale;

/**
 * Derived information-completeness score for customer discovery.
 * This ranks how much enquiry-useful information a listing has. It is not a quality,
 * trust, or recommendation score. Numeric weights live only here.
 */
public final class ListingInformationCompleteness {

    public static final int MOBILE_CONTACT_SCORE = 4;
    public static final int ADDRESS_SCORE = 3;
    public static final int MAP_URL_SCORE = 2;
    public static final int AMENITIES_SCORE = 1;
    public static final int MAX_SCORE =
            MOBILE_CONTACT_SCORE + ADDRESS_SCORE + MAP_URL_SCORE + AMENITIES_SCORE;

    private static final String FOOD_INCLUDED = "FOOD_INCLUDED";

    private ListingInformationCompleteness() {}

    public static int score(boolean mobileContact, boolean address, boolean mapUrl, boolean amenities) {
        int total = 0;
        if (mobileContact) {
            total += MOBILE_CONTACT_SCORE;
        }
        if (address) {
            total += ADDRESS_SCORE;
        }
        if (mapUrl) {
            total += MAP_URL_SCORE;
        }
        if (amenities) {
            total += AMENITIES_SCORE;
        }
        return total;
    }

    public static boolean hasUsableMobile(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (isUsableMobile(value)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUsableAddress(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (isUsableAddress(value)) {
                return true;
            }
        }
        return false;
    }

    /** Stored http(s) map URL only. Coordinates alone do not count. */
    public static boolean hasValidMapUrl(String mapUrl) {
        return DiscoverListingSanitizer.mapUrl(mapUrl) != null;
    }

    /**
     * At least one real amenity. {@code FOOD_INCLUDED} is food, not an amenity,
     * and extra amenities do not increase the score past {@link #AMENITIES_SCORE}.
     */
    public static boolean hasUsableAmenity(Collection<String> amenityCodes) {
        if (amenityCodes == null) {
            return false;
        }
        for (String code : amenityCodes) {
            if (isUsableAmenityCode(code)) {
                return true;
            }
        }
        return false;
    }

    static boolean isUsableMobile(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.indexOf('@') >= 0 || isPlaceholder(trimmed)) {
            return false;
        }
        try {
            String national = MobileNumberNormalizer.normalize(trimmed);
            return !AdminLeadDefaults.PLACEHOLDER_MOBILE.equals(national);
        } catch (BusinessException ex) {
            return false;
        }
    }

    static boolean isUsableAddress(String value) {
        String text = DiscoverListingSanitizer.text(value);
        if (text == null || isPlaceholder(text)) {
            return false;
        }
        return true;
    }

    static boolean isUsableAmenityCode(String code) {
        if (code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty() || isPlaceholder(trimmed)) {
            return false;
        }
        return !FOOD_INCLUDED.equalsIgnoreCase(trimmed);
    }

    private static boolean isPlaceholder(String trimmed) {
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return "-".equals(lower)
                || "–".equals(trimmed)
                || "—".equals(trimmed)
                || "na".equals(lower)
                || "n/a".equals(lower);
    }
}

package com.acomi.acomi_backend.space.application.service;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Resolves listing map location from either coordinates, a Google Maps URL, or both.
 * Some source rows have latitude/longitude only; others have a pasted Maps link.
 */
public final class ListingMapLocation {

    private static final Pattern AT_COORDS =
            Pattern.compile("@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern QUERY_COORDS =
            Pattern.compile("[?&](?:q|query)=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern PLACE_DATA_COORDS =
            Pattern.compile("!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)");

    private ListingMapLocation() {}

    public record Resolved(BigDecimal latitude, BigDecimal longitude, String mapUrl) {}

    public static Resolved resolve(BigDecimal latitude, BigDecimal longitude, String mapUrl) {
        BigDecimal lat = DiscoverListingSanitizer.latitude(latitude);
        BigDecimal lng = DiscoverListingSanitizer.longitude(longitude);
        String url = DiscoverListingSanitizer.mapUrl(mapUrl);

        if (lat == null || lng == null) {
            Coordinates extracted = extractCoordinates(url);
            if (extracted != null) {
                if (lat == null) {
                    lat = extracted.latitude();
                }
                if (lng == null) {
                    lng = extracted.longitude();
                }
            }
        }

        if (url == null && lat != null && lng != null) {
            url = mapsUrlFromCoordinates(lat, lng);
        }

        if (lat == null || lng == null) {
            lat = null;
            lng = null;
        }

        return new Resolved(lat, lng, url);
    }

    public static String mapsUrlFromCoordinates(BigDecimal latitude, BigDecimal longitude) {
        BigDecimal lat = DiscoverListingSanitizer.latitude(latitude);
        BigDecimal lng = DiscoverListingSanitizer.longitude(longitude);
        if (lat == null || lng == null) {
            return null;
        }
        return "https://maps.google.com/?q="
                + lat.stripTrailingZeros().toPlainString()
                + ","
                + lng.stripTrailingZeros().toPlainString();
    }

    public static Coordinates extractCoordinates(String mapUrl) {
        String sanitized = DiscoverListingSanitizer.mapUrl(mapUrl);
        if (sanitized == null) {
            return null;
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(sanitized, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            decoded = sanitized;
        }

        Coordinates fromPlace = match(PLACE_DATA_COORDS, decoded);
        if (fromPlace != null) {
            return fromPlace;
        }
        Coordinates fromAt = match(AT_COORDS, decoded);
        if (fromAt != null) {
            return fromAt;
        }
        return match(QUERY_COORDS, decoded);
    }

    private static Coordinates match(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            BigDecimal lat = DiscoverListingSanitizer.latitude(new BigDecimal(matcher.group(1)));
            BigDecimal lng = DiscoverListingSanitizer.longitude(new BigDecimal(matcher.group(2)));
            if (lat == null || lng == null) {
                return null;
            }
            return new Coordinates(lat, lng);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static boolean looksLikeHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String lower = value.trim().toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    public record Coordinates(BigDecimal latitude, BigDecimal longitude) {}
}

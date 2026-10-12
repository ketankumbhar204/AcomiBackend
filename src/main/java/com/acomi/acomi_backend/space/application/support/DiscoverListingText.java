package com.acomi.acomi_backend.space.application.support;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes Excel free text the same way discovery filters interpret it.
 * Missing text is never treated as a default value.
 */
public final class DiscoverListingText {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private DiscoverListingText() {}

    public static String pad(String raw) {
        if (raw == null || raw.isBlank()) {
            return " ";
        }
        String normalized = NON_ALNUM.matcher(raw.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        return normalized.isEmpty() ? " " : " " + normalized + " ";
    }

    public static boolean matchesSharing(String raw, SharingOccupancy occupancy) {
        String text = pad(raw);
        if (text.isBlank()) {
            return false;
        }
        boolean sharingContext = text.contains(" shar")
                || text.contains(" occup")
                || text.contains(" bed")
                || text.contains(" person")
                || text.contains(" room");
        return switch (occupancy) {
            case SINGLE -> text.contains(" single ") || (sharingContext && hasNumber(text, 1));
            case TWO -> text.contains(" double ") || text.contains(" twin ") || (sharingContext && hasNumber(text, 2));
            case THREE -> text.contains(" triple ") || (sharingContext && hasNumber(text, 3));
            case FOUR -> sharingContext && hasNumber(text, 4);
            case FIVE -> sharingContext && hasNumber(text, 5);
            case SIX_PLUS -> sharingContext && hasNumberAtLeast(text, 6);
        };
    }

    public static boolean matchesFurnishing(String raw, FurnishingLevel level) {
        String text = pad(raw);
        if (" ".equals(text)) {
            return false;
        }
        boolean unfurnished = text.contains(" unfurnished ") || text.contains(" not furnished ");
        boolean semi = text.contains(" semi furnished ") || text.contains(" semifurnished ");
        boolean full = !semi && (text.contains(" fully furnished ") || text.contains(" full furnished "));
        return switch (level) {
            case UNFURNISHED -> unfurnished;
            case SEMI -> semi;
            case FULL -> full && !unfurnished;
        };
    }

    public static boolean matchesFoodAvailable(String raw) {
        String text = pad(raw);
        return text.contains(" food available ")
                || text.contains(" meals available ")
                || text.contains(" meal available ")
                || text.contains(" food optional ")
                || text.contains(" food extra ")
                || text.contains(" meals extra ")
                || text.contains(" tiffin available ");
    }

    public static boolean matchesServicedApartment(String raw) {
        String text = pad(raw);
        return text.contains(" serviced ")
                || text.contains(" service apartment ")
                || text.contains(" service apartments ")
                || text.contains(" corporate apartment ")
                || text.contains(" corporate apartments ");
    }

    public static List<String> servicedLikePatterns() {
        return List.of(
                "% serviced %",
                "% service apartment %",
                "% service apartments %",
                "% corporate apartment %",
                "% corporate apartments %");
    }

    public static List<String> foodAvailableLikePatterns() {
        return List.of(
                "% food available %",
                "% meals available %",
                "% meal available %",
                "% food optional %",
                "% food extra %",
                "% meals extra %",
                "% tiffin available %");
    }

    public static List<String> furnishingLikePatterns(FurnishingLevel level) {
        return switch (level) {
            case FULL -> List.of("% fully furnished %", "% full furnished %");
            case SEMI -> List.of("% semi furnished %", "% semifurnished %");
            case UNFURNISHED -> List.of("% unfurnished %", "% not furnished %");
        };
    }

    public static List<String> amenityLikePatterns(String code) {
        if (code == null) {
            return List.of();
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "AC" -> List.of("% ac %", "% a c %", "% air condition%", "% air conditioned %", "% air conditioning %");
            case "KITCHEN" -> List.of("% kitchen %");
            case "CCTV" -> List.of("% cctv %", "% security %");
            default -> List.of();
        };
    }

    public static boolean matchesAmenityAlias(String raw, String code) {
        String text = pad(raw);
        if (" ".equals(text) || code == null) {
            return false;
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "AC" -> text.contains(" ac ")
                    || text.contains(" a c ")
                    || text.contains(" air condition ")
                    || text.contains(" air conditioned ")
                    || text.contains(" air conditioning ");
            case "KITCHEN" -> text.contains(" kitchen ");
            case "CCTV" -> text.contains(" cctv ") || text.contains(" security ");
            default -> false;
        };
    }

    private static boolean hasNumber(String padded, int number) {
        String target = Integer.toString(number);
        for (String token : padded.trim().split(" ")) {
            if (target.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNumberAtLeast(String padded, int minimum) {
        for (String token : padded.trim().split(" ")) {
            if (!token.isEmpty() && token.chars().allMatch(Character::isDigit) && Integer.parseInt(token) >= minimum) {
                return true;
            }
        }
        return false;
    }
}

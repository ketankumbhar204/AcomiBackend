package com.acomi.acomi_backend.space.application.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Derives address-match needles from a selected postal/location label.
 *
 * <p>{@code Infotech Park (Hinjawadi)} becomes {@code infotech park}, {@code hinjawadi},
 * and the explicit alias {@code hinjewadi}. State/district-scale terms are not used as
 * standalone extra needles when a more specific term exists.
 */
public final class LocationFilterTokens {

    private static final Pattern PARENTHETICAL = Pattern.compile("\\(([^)]+)\\)");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{Alnum}\\s]+");
    private static final int MIN_TOKEN_LENGTH = 3;

    private static final Set<String> BROAD_GEO_TERMS = Set.of(
            "andaman and nicobar islands",
            "andhra pradesh",
            "arunachal pradesh",
            "assam",
            "bihar",
            "chandigarh",
            "chhattisgarh",
            "dadra and nagar haveli and daman and diu",
            "delhi",
            "goa",
            "gujarat",
            "haryana",
            "himachal pradesh",
            "jammu and kashmir",
            "jharkhand",
            "karnataka",
            "kerala",
            "ladakh",
            "lakshadweep",
            "madhya pradesh",
            "maharashtra",
            "manipur",
            "meghalaya",
            "mizoram",
            "nagaland",
            "nct of delhi",
            "odisha",
            "orissa",
            "puducherry",
            "pondicherry",
            "punjab",
            "rajasthan",
            "sikkim",
            "tamil nadu",
            "telangana",
            "tripura",
            "uttar pradesh",
            "uttarakhand",
            "west bengal",
            "india",
            "bharat",
            "pune",
            "mumbai",
            "bombay",
            "bengaluru",
            "bangalore",
            "hyderabad",
            "chennai",
            "madras",
            "kolkata",
            "calcutta",
            "ahmedabad",
            "jaipur",
            "surat",
            "lucknow",
            "kanpur",
            "nagpur",
            "indore",
            "thane",
            "bhopal",
            "new delhi",
            "noida",
            "gurugram",
            "gurgaon",
            "navi mumbai");

    private LocationFilterTokens() {}

    public static List<String> needles(String location) {
        return needles(location, null, null);
    }

    public static List<String> needles(String location, String district, String state) {
        if (!StringUtils.hasText(location)) {
            return List.of();
        }
        List<String> extracted = extractTerms(location);
        Set<String> broadContext = new LinkedHashSet<>(BROAD_GEO_TERMS);
        addNormalized(broadContext, district);
        addNormalized(broadContext, state);

        boolean hasSpecific = extracted.stream().anyMatch(term -> !broadContext.contains(term));
        Set<String> tokens = new LinkedHashSet<>();
        for (String term : extracted) {
            if (hasSpecific && broadContext.contains(term)) {
                continue;
            }
            tokens.addAll(LocationLocalityAliases.expand(term));
        }
        tokens.removeIf(token -> token.length() < MIN_TOKEN_LENGTH);
        if (tokens.isEmpty()) {
            String fallback = normalize(PARENTHETICAL.matcher(location).replaceAll(" "));
            if (fallback.length() >= MIN_TOKEN_LENGTH) {
                tokens.addAll(LocationLocalityAliases.expand(fallback));
            }
        }
        return List.copyOf(tokens);
    }

    public static boolean addressContains(String address, String location) {
        return addressContains(address, location, null, null);
    }

    public static boolean addressContains(String address, String location, String district, String state) {
        List<String> terms = needles(location, district, state);
        if (terms.isEmpty()) {
            return !StringUtils.hasText(location);
        }
        String haystack = normalize(address);
        return terms.stream().anyMatch(haystack::contains);
    }

    private static List<String> extractTerms(String location) {
        List<String> terms = new ArrayList<>();
        String outer = normalize(PARENTHETICAL.matcher(location).replaceAll(" "));
        if (outer.length() >= MIN_TOKEN_LENGTH) {
            terms.add(outer);
        }
        Matcher matcher = PARENTHETICAL.matcher(location);
        while (matcher.find()) {
            String inner = normalize(matcher.group(1));
            if (inner.length() >= MIN_TOKEN_LENGTH) {
                terms.add(inner);
            }
        }
        if (terms.isEmpty()) {
            String normalized = normalize(location);
            if (normalized.length() >= MIN_TOKEN_LENGTH) {
                terms.add(normalized);
            }
        }
        return terms;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        String stripped = NON_ALNUM.matcher(lowered).replaceAll(" ");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private static void addNormalized(Set<String> target, String value) {
        String normalized = normalize(value);
        if (normalized.length() >= MIN_TOKEN_LENGTH) {
            target.add(normalized);
        }
    }
}

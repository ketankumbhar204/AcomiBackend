package com.acomi.acomi_backend.space.application.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Explicit locality spelling aliases for address matching.
 *
 * <p>Only listed groups are expanded. This is not phonetic or fuzzy matching.
 */
public final class LocationLocalityAliases {

    private static final List<List<String>> GROUPS = List.of(List.of("hinjawadi", "hinjewadi"));

    private LocationLocalityAliases() {}

    public static List<String> expand(String normalizedTerm) {
        if (normalizedTerm == null || normalizedTerm.isBlank()) {
            return List.of();
        }
        Set<String> expanded = new LinkedHashSet<>();
        expanded.add(normalizedTerm);
        for (List<String> group : GROUPS) {
            for (String alias : group) {
                if (normalizedTerm.equals(alias)) {
                    expanded.addAll(group);
                    continue;
                }
                if (containsWord(normalizedTerm, alias)) {
                    for (String other : group) {
                        expanded.add(replaceWord(normalizedTerm, alias, other));
                    }
                }
            }
        }
        return List.copyOf(expanded);
    }

    static List<List<String>> groups() {
        return GROUPS;
    }

    private static boolean containsWord(String text, String word) {
        for (String part : text.split(" ")) {
            if (part.equals(word)) {
                return true;
            }
        }
        return false;
    }

    private static String replaceWord(String text, String from, String to) {
        List<String> parts = new ArrayList<>();
        for (String part : text.split(" ")) {
            parts.add(part.equals(from) ? to : part);
        }
        return String.join(" ", parts);
    }
}

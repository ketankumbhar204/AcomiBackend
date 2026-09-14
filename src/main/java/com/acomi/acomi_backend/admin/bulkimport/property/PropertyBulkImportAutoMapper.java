package com.acomi.acomi_backend.admin.bulkimport.property;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Suggests Excel header → field mappings. Ambiguous matches are left unmapped
 * (two headers for one field, or one header matching multiple fields).
 */
public final class PropertyBulkImportAutoMapper {

    private PropertyBulkImportAutoMapper() {}

    public static Map<String, String> suggestMapping(List<String> headers) {
        Map<PropertyBulkImportField, List<String>> candidates = new EnumMap<>(PropertyBulkImportField.class);
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            candidates.put(field, new ArrayList<>());
        }

        if (headers != null) {
            for (String header : headers) {
                if (header == null || header.isBlank()) {
                    continue;
                }
                List<PropertyBulkImportField> matches = new ArrayList<>();
                for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
                    if (field.matchesHeader(header)) {
                        matches.add(field);
                    }
                }
                if (matches.size() == 1) {
                    candidates.get(matches.get(0)).add(header.trim());
                }
            }
        }

        Map<String, String> suggested = new LinkedHashMap<>();
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            List<String> matchedHeaders = candidates.get(field);
            if (matchedHeaders.size() == 1) {
                suggested.put(field.name(), matchedHeaders.get(0));
            } else {
                suggested.put(field.name(), null);
            }
        }
        return suggested;
    }
}

package com.acomi.acomi_backend.admin.bulkimport.property.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyBulkImportPreviewRow {
    private int rowNumber;
    /** VALID | WARNING | INVALID | BLANK | DUPLICATE */
    private String status;
    /** Original Excel values. */
    private Map<String, String> values;
    /** Canonical display values for cells that were normalized. */
    private Map<String, String> normalizedValues;
    private List<PropertyBulkImportFieldError> errors;
    /** NORMALIZED notes and WARNING notes. Warnings do not block import. */
    private List<PropertyBulkImportFieldNotice> notices;
    /** Present when status is DUPLICATE. */
    private PropertyBulkImportDuplicateMatch duplicateMatch;
}

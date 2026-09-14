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
    /** VALID | INVALID | BLANK | DUPLICATE */
    private String status;
    private Map<String, String> values;
    private List<PropertyBulkImportFieldError> errors;
    /** Present when status is DUPLICATE. */
    private PropertyBulkImportDuplicateMatch duplicateMatch;
}

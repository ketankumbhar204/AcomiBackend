package com.acomi.acomi_backend.admin.bulkimport.property.dto;

import java.util.List;
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
public class PropertyBulkImportResultRow {
    private int rowNumber;
    /** CONVERTED | IMPORTED | FAILED | BLANK | INVALID | SKIPPED */
    private String status;
    /** PROPERTY | MESS when classified; null for blank rows. */
    private String targetKind;
    private String reference;
    /** Set when the lead was converted to a live Space in the same import. */
    private String spaceId;
    private String spaceName;
    /** True when a live Space was created for this row. */
    private Boolean converted;
    /** Present when the lead was saved but auto-convert could not run. */
    private String conversionError;
    private List<PropertyBulkImportFieldError> errors;
    /** Present when status is SKIPPED because of a duplicate decision. */
    private PropertyBulkImportDuplicateMatch duplicateMatch;
}

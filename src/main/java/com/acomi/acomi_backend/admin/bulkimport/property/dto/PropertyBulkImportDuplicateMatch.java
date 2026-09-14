package com.acomi.acomi_backend.admin.bulkimport.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Why a preview/import row was flagged as a likely duplicate (not yet persisted). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyBulkImportDuplicateMatch {
    /** HIGH | MEDIUM */
    private String confidence;
    /** IN_FILE | EXISTING_PROPERTY | EXISTING_MESS | EXISTING_SPACE */
    private String source;
    /** Excel row number when source is IN_FILE. */
    private Integer matchedRowNumber;
    private String matchedReference;
    private String matchedSpaceId;
    private String matchedName;
    private String reason;
}

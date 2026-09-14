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
public class PropertyBulkImportResultResponse {
    private int totalRows;
    /** Leads persisted (includes both CONVERTED and IMPORTED-only rows). */
    private int imported;
    /** Rows that became live Spaces in this import. */
    private int converted;
    /** Leads saved but not converted (e.g. no ACOMI user for the mobile). */
    private int importedOnly;
    private int failed;
    private int blankSkipped;
    /** Duplicate rows the admin chose to skip (never persisted). */
    private int duplicateSkipped;
    private List<PropertyBulkImportResultRow> rows;
}

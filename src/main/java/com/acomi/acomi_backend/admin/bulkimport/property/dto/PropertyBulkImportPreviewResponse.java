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
public class PropertyBulkImportPreviewResponse {
    private int totalRows;
    /** Valid rows that are not flagged as duplicates. */
    private int valid;
    private int invalid;
    private int blank;
    /** Likely duplicates awaiting Keep/Skip decision (not saved yet). */
    private int duplicate;
    private List<PropertyBulkImportPreviewRow> rows;
}

package com.acomi.acomi_backend.admin.bulkimport.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A preview note for one cell.
 * NORMALIZED means the value was understood. WARNING means it was understood and should be reviewed.
 * Neither blocks import. Errors stay on {@link PropertyBulkImportFieldError}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyBulkImportFieldNotice {
    private String field;
    /** NORMALIZED or WARNING. */
    private String level;
    private String message;
    private String originalValue;
    private String normalizedValue;
}

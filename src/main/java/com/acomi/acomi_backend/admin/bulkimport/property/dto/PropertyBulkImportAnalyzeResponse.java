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
public class PropertyBulkImportAnalyzeResponse {
    private List<String> headers;
    /** fieldKey → excel header or null */
    private Map<String, String> suggestedMapping;
    private int dataRowCount;
    private List<Map<String, String>> sampleRows;
}

package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportAnalyzeResponse;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportPreviewResponse;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportResultResponse;
import com.acomi.acomi_backend.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Separate controller under {@code /bulk-import} so paths do not collide with
 * {@code /api/v1/admin/property-registrations/{id}}.
 */
@RestController
@RequestMapping("/api/v1/admin/property-registrations/bulk-import")
@RequiredArgsConstructor
@Tag(name = "Admin Property Bulk Import")
@SecurityRequirement(name = "bearerAuth")
public class PropertyBulkImportController {

    private final PropertyBulkImportService propertyBulkImportService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] bytes = propertyBulkImportService.buildTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"property-leads-bulk-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PropertyBulkImportAnalyzeResponse>> analyze(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(propertyBulkImportService.analyze(file)));
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PropertyBulkImportPreviewResponse>> preview(
            @RequestPart("file") MultipartFile file,
            @RequestPart("mapping") String mapping,
            @RequestParam(value = "markAsTestLead", defaultValue = "false") boolean markAsTestLead) {
        return ResponseEntity.ok(
                ApiResponse.success(propertyBulkImportService.preview(file, mapping, markAsTestLead)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PropertyBulkImportResultResponse>> importLeads(
            @RequestPart("file") MultipartFile file,
            @RequestPart("mapping") String mapping,
            @RequestParam(value = "markAsTestLead", defaultValue = "false") boolean markAsTestLead,
            @RequestPart(value = "keepDuplicateRowNumbers", required = false)
                    String keepDuplicateRowNumbers) {
        return ResponseEntity.ok(ApiResponse.success(
                "Bulk import completed",
                propertyBulkImportService.importRows(
                        file, mapping, markAsTestLead, keepDuplicateRowNumbers)));
    }
}

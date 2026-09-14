package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.admin.application.service.AdminMessRegistrationService;
import com.acomi.acomi_backend.admin.application.service.AdminPropertyRegistrationService;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportAnalyzeResponse;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportDuplicateMatch;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportFieldError;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportPreviewResponse;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportPreviewRow;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportResultResponse;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportResultRow;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PropertyBulkImportService {

    private final AdminPropertyRegistrationService adminPropertyRegistrationService;
    private final AdminMessRegistrationService adminMessRegistrationService;
    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final PropertyBulkImportDuplicateDetector duplicateDetector;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public byte[] buildTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet("Sheet1");
            Row header = data.createRow(0);
            Row example = data.createRow(1);
            PropertyBulkImportField[] fields = PropertyBulkImportField.values();
            for (int i = 0; i < fields.length; i++) {
                header.createCell(i).setCellValue(fields[i].getTemplateHeader());
            }
            // Sample values must follow PropertyBulkImportField ordinal order.
            example.createCell(PropertyBulkImportField.PROPERTY_TYPE.ordinal()).setCellValue("PG");
            example.createCell(PropertyBulkImportField.PROPERTY_NAME.ordinal()).setCellValue("Sunrise PG Demo");
            example.createCell(PropertyBulkImportField.OWNER_NAME.ordinal()).setCellValue("Ravi Kumar");
            example.createCell(PropertyBulkImportField.MOBILE_NUMBER.ordinal()).setCellValue("9876543210");
            example.createCell(PropertyBulkImportField.ALTERNATE_MOBILE_NUMBER.ordinal()).setCellValue("9123456780");
            example.createCell(PropertyBulkImportField.CONTACT_3.ordinal()).setCellValue("9988776655");
            example.createCell(PropertyBulkImportField.ADDRESS_LINE.ordinal()).setCellValue("12 MG Road");
            example.createCell(PropertyBulkImportField.CITY.ordinal()).setCellValue("Bengaluru");
            example.createCell(PropertyBulkImportField.STATE.ordinal()).setCellValue("Karnataka");
            example.createCell(PropertyBulkImportField.PINCODE.ordinal()).setCellValue("560001");
            example.createCell(PropertyBulkImportField.MAP_URL.ordinal())
                    .setCellValue("https://maps.google.com/?q=12.97,77.59");
            example.createCell(PropertyBulkImportField.STARTING_PRICE.ordinal()).setCellValue("8500");
            example.createCell(PropertyBulkImportField.GENDER.ordinal()).setCellValue("Both");
            example.createCell(PropertyBulkImportField.SHARING.ordinal()).setCellValue("2 Sharing");
            example.createCell(PropertyBulkImportField.AMENITIES.ordinal()).setCellValue("WiFi, Parking");
            example.createCell(PropertyBulkImportField.FOOD_INCLUDED.ordinal()).setCellValue("yes");
            example.createCell(PropertyBulkImportField.LATITUDE.ordinal()).setCellValue("12.9716");
            example.createCell(PropertyBulkImportField.LONGITUDE.ordinal()).setCellValue("77.5946");
            example.createCell(PropertyBulkImportField.TEST_LEAD.ordinal()).setCellValue("false");

            for (int i = 0; i < fields.length; i++) {
                data.autoSizeColumn(i);
            }

            Sheet instructions = workbook.createSheet("Instructions");
            int r = 0;
            instructions.createRow(r++).createCell(0).setCellValue("Admin property lead bulk import");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "All columns are optional. Blank cells are omitted; the server applies placeholders for incomplete leads.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Allowed types: PG, Hostel, Co-living / CO_LIVING, Rental, Mess. Mess rows create mess_registrations.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Leave unknown/optional cells blank. Placeholders like -, —, n/a, none are treated as blank.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Boolean (Test Lead / Food Included): true/false, yes/no, y/n, 1/0 (case insensitive). Leave blank for default.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Gender: Male/Gents, Female/Ladies, Mixed/Both. Contact 3 is stored as additional mobile.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Mobile numbers: 10-digit Indian numbers starting with 6–9. 91-prefixed 12-digit values are accepted.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Pincode: 6-digit Indian pincode. Starting price / Rent starts from: non-negative number.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Completely blank rows are skipped. Invalid rows do not block valid rows on import.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Likely duplicates are flagged in Preview (not saved). Admin Keep/Skip decides before import.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "After import, admin-verified rows become live discoverable Spaces automatically (test leads stay hidden).");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Owner name is optional. Space owner is resolved by mobile when an ACOMI user exists, otherwise the importing admin.");
            instructions.createRow(r++).createCell(0).setCellValue(
                    "Only .xlsx files are supported. Maximum 1000 data rows and 5MB file size.");
            instructions.autoSizeColumn(0);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("Failed to build template: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public PropertyBulkImportAnalyzeResponse analyze(MultipartFile file) {
        PropertyBulkImportExcelReader.ParsedWorkbook parsed = PropertyBulkImportExcelReader.parse(file);
        Map<String, String> suggested = PropertyBulkImportAutoMapper.suggestMapping(parsed.headers());
        List<Map<String, String>> samples = new ArrayList<>();
        int limit = Math.min(5, parsed.dataRows().size());
        for (int i = 0; i < limit; i++) {
            samples.add(new LinkedHashMap<>(parsed.dataRows().get(i)));
        }
        return PropertyBulkImportAnalyzeResponse.builder()
                .headers(parsed.headers())
                .suggestedMapping(suggested)
                .dataRowCount(parsed.dataRows().size())
                .sampleRows(samples)
                .build();
    }

    public PropertyBulkImportPreviewResponse preview(
            MultipartFile file, String mappingJson, boolean markAsTestLead) {
        Map<String, String> mapping = parseMapping(mappingJson);
        PropertyBulkImportExcelReader.ParsedWorkbook parsed = PropertyBulkImportExcelReader.parse(file);

        List<ProcessedEntry> entries = processAll(parsed, mapping, markAsTestLead);
        DuplicateAnnotations annotations = annotateDuplicates(entries);

        List<PropertyBulkImportPreviewRow> rows = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        int blank = 0;
        int duplicate = 0;

        for (ProcessedEntry entry : entries) {
            PropertyBulkImportDuplicateMatch match = annotations.byRowNumber().get(entry.rowNumber());
            String status = entry.processed().status().name();
            if (match != null && entry.processed().status() == PropertyBulkImportRowProcessor.RowStatus.VALID) {
                status = "DUPLICATE";
                duplicate++;
            } else {
                switch (entry.processed().status()) {
                    case VALID -> valid++;
                    case INVALID -> invalid++;
                    case BLANK -> blank++;
                }
            }
            rows.add(PropertyBulkImportPreviewRow.builder()
                    .rowNumber(entry.rowNumber())
                    .status(status)
                    .values(entry.processed().valuesSnapshot())
                    .errors(entry.processed().errors())
                    .duplicateMatch(match)
                    .build());
        }

        return PropertyBulkImportPreviewResponse.builder()
                .totalRows(parsed.dataRows().size())
                .valid(valid)
                .invalid(invalid)
                .blank(blank)
                .duplicate(duplicate)
                .rows(rows)
                .build();
    }

    public PropertyBulkImportResultResponse importRows(
            MultipartFile file,
            String mappingJson,
            boolean markAsTestLead,
            String keepDuplicateRowNumbersJson) {
        Map<String, String> mapping = parseMapping(mappingJson);
        Set<Integer> keepDuplicates = parseKeepDuplicateRowNumbers(keepDuplicateRowNumbersJson);
        PropertyBulkImportExcelReader.ParsedWorkbook parsed = PropertyBulkImportExcelReader.parse(file);

        List<ProcessedEntry> entries = processAll(parsed, mapping, markAsTestLead);
        DuplicateAnnotations annotations = annotateDuplicates(entries);

        List<PropertyBulkImportResultRow> rows = new ArrayList<>();
        int imported = 0;
        int converted = 0;
        int importedOnly = 0;
        int failed = 0;
        int blankSkipped = 0;
        int duplicateSkipped = 0;

        for (ProcessedEntry entry : entries) {
            int rowNumber = entry.rowNumber();
            var processed = entry.processed();

            if (processed.status() == PropertyBulkImportRowProcessor.RowStatus.BLANK) {
                blankSkipped++;
                rows.add(PropertyBulkImportResultRow.builder()
                        .rowNumber(rowNumber)
                        .status("BLANK")
                        .build());
                continue;
            }

            if (processed.status() == PropertyBulkImportRowProcessor.RowStatus.INVALID) {
                failed++;
                rows.add(PropertyBulkImportResultRow.builder()
                        .rowNumber(rowNumber)
                        .status("INVALID")
                        .targetKind(processed.targetKind().name())
                        .errors(processed.errors())
                        .build());
                continue;
            }

            PropertyBulkImportDuplicateMatch match = annotations.byRowNumber().get(rowNumber);
            if (match != null && !keepDuplicates.contains(rowNumber)) {
                duplicateSkipped++;
                rows.add(PropertyBulkImportResultRow.builder()
                        .rowNumber(rowNumber)
                        .status("SKIPPED")
                        .targetKind(processed.targetKind().name())
                        .duplicateMatch(match)
                        .errors(List.of(new PropertyBulkImportFieldError(
                                null, "Skipped as duplicate: " + match.getReason())))
                        .build());
                continue;
            }

            try {
                if (processed.targetKind() == PropertyBulkImportRowProcessor.TargetKind.MESS) {
                    var created = adminMessRegistrationService.create(processed.messRequest());
                    imported++;
                    rows.add(resultRowAfterCreate(rowNumber, "MESS", created.getReference(), true));
                } else {
                    var created = adminPropertyRegistrationService.create(processed.propertyRequest());
                    imported++;
                    rows.add(resultRowAfterCreate(rowNumber, "PROPERTY", created.getReference(), false));
                }
                PropertyBulkImportResultRow last = rows.get(rows.size() - 1);
                if (Boolean.TRUE.equals(last.getConverted())) {
                    converted++;
                } else {
                    importedOnly++;
                }
            } catch (BusinessException ex) {
                failed++;
                rows.add(PropertyBulkImportResultRow.builder()
                        .rowNumber(rowNumber)
                        .status("FAILED")
                        .targetKind(processed.targetKind().name())
                        .errors(List.of(new PropertyBulkImportFieldError(null, ex.getMessage())))
                        .build());
            } catch (Exception ex) {
                failed++;
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                rows.add(PropertyBulkImportResultRow.builder()
                        .rowNumber(rowNumber)
                        .status("FAILED")
                        .targetKind(processed.targetKind().name())
                        .errors(List.of(new PropertyBulkImportFieldError(null, message)))
                        .build());
            }
        }

        return PropertyBulkImportResultResponse.builder()
                .totalRows(parsed.dataRows().size())
                .imported(imported)
                .converted(converted)
                .importedOnly(importedOnly)
                .failed(failed)
                .blankSkipped(blankSkipped)
                .duplicateSkipped(duplicateSkipped)
                .rows(rows)
                .build();
    }

    /** Backward-compatible overload used by older callers/tests. */
    public PropertyBulkImportResultResponse importRows(
            MultipartFile file, String mappingJson, boolean markAsTestLead) {
        return importRows(file, mappingJson, markAsTestLead, null);
    }

    private List<ProcessedEntry> processAll(
            PropertyBulkImportExcelReader.ParsedWorkbook parsed,
            Map<String, String> mapping,
            boolean markAsTestLead) {
        List<ProcessedEntry> entries = new ArrayList<>();
        for (int i = 0; i < parsed.dataRows().size(); i++) {
            int rowNumber = i + 2;
            var processed = PropertyBulkImportRowProcessor.process(
                    parsed.dataRows().get(i), mapping, validator, markAsTestLead);
            entries.add(new ProcessedEntry(rowNumber, processed));
        }
        return entries;
    }

    private DuplicateAnnotations annotateDuplicates(List<ProcessedEntry> entries) {
        List<PropertyBulkImportDuplicateDetector.Fingerprint> fingerprints = new ArrayList<>();
        for (ProcessedEntry entry : entries) {
            if (entry.processed().status() != PropertyBulkImportRowProcessor.RowStatus.VALID) {
                continue;
            }
            fingerprints.add(PropertyBulkImportDuplicateDetector.fingerprintFromProcessed(
                    entry.rowNumber(), entry.processed()));
        }

        List<PropertyBulkImportDuplicateDetector.ExistingRecord> existing =
                fingerprints.isEmpty() ? List.of() : duplicateDetector.loadExisting(fingerprints);

        Map<Integer, PropertyBulkImportDuplicateMatch> byRowNumber = new LinkedHashMap<>();
        List<PropertyBulkImportDuplicateDetector.Fingerprint> earlier = new ArrayList<>();
        for (PropertyBulkImportDuplicateDetector.Fingerprint fingerprint : fingerprints) {
            Optional<PropertyBulkImportDuplicateMatch> match =
                    duplicateDetector.findMatch(fingerprint, earlier, existing);
            match.ifPresent(value -> byRowNumber.put(fingerprint.rowNumber(), value));
            earlier.add(fingerprint);
        }
        return new DuplicateAnnotations(byRowNumber);
    }

    private PropertyBulkImportResultRow resultRowAfterCreate(
            int rowNumber, String targetKind, String reference, boolean mess) {
        if (mess) {
            return messRegistrationRepository
                    .findByReference(reference)
                    .map(entity -> {
                        boolean converted = entity.getConvertedSpaceId() != null;
                        return PropertyBulkImportResultRow.builder()
                                .rowNumber(rowNumber)
                                .status(converted ? "CONVERTED" : "IMPORTED")
                                .targetKind(targetKind)
                                .reference(reference)
                                .spaceId(
                                        entity.getConvertedSpaceId() == null
                                                ? null
                                                : entity.getConvertedSpaceId().toString())
                                .converted(converted)
                                .conversionError(
                                        converted
                                                ? null
                                                : "Lead saved but was not published to a live Space")
                                .build();
                    })
                    .orElseGet(() -> PropertyBulkImportResultRow.builder()
                            .rowNumber(rowNumber)
                            .status("IMPORTED")
                            .targetKind(targetKind)
                            .reference(reference)
                            .converted(false)
                            .conversionError("Lead saved but registration was not found after create")
                            .build());
        }
        return propertyRegistrationRepository
                .findByReference(reference)
                .map(entity -> {
                    boolean converted = entity.getConvertedSpaceId() != null;
                    return PropertyBulkImportResultRow.builder()
                            .rowNumber(rowNumber)
                            .status(converted ? "CONVERTED" : "IMPORTED")
                            .targetKind(targetKind)
                            .reference(reference)
                            .spaceId(
                                    entity.getConvertedSpaceId() == null
                                            ? null
                                            : entity.getConvertedSpaceId().toString())
                            .converted(converted)
                            .conversionError(
                                    converted ? null : "Lead saved but was not published to a live Space")
                            .build();
                })
                .orElseGet(() -> PropertyBulkImportResultRow.builder()
                        .rowNumber(rowNumber)
                        .status("IMPORTED")
                        .targetKind(targetKind)
                        .reference(reference)
                        .converted(false)
                        .conversionError("Lead saved but registration was not found after create")
                        .build());
    }

    Map<String, String> parseMapping(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            throw new BusinessException("mapping is required", HttpStatus.BAD_REQUEST);
        }
        try {
            Map<String, String> raw =
                    objectMapper.readValue(mappingJson, new TypeReference<Map<String, String>>() {});
            Map<String, String> normalized = new LinkedHashMap<>();
            for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
                String header = raw.get(field.name());
                if (header != null && header.isBlank()) {
                    header = null;
                }
                normalized.put(field.name(), header);
            }
            return normalized;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    "mapping must be a JSON object of fieldKey → excelHeader|null",
                    HttpStatus.BAD_REQUEST);
        }
    }

    Set<Integer> parseKeepDuplicateRowNumbers(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<Integer> values = objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
            return new HashSet<>(values);
        } catch (Exception ex) {
            throw new BusinessException(
                    "keepDuplicateRowNumbers must be a JSON array of Excel row numbers",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private record ProcessedEntry(int rowNumber, PropertyBulkImportRowProcessor.ProcessedRow processed) {}

    private record DuplicateAnnotations(Map<Integer, PropertyBulkImportDuplicateMatch> byRowNumber) {}
}

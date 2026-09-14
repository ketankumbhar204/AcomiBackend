package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.admin.application.service.AdminMessRegistrationService;
import com.acomi.acomi_backend.admin.application.service.AdminPropertyRegistrationService;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportPreviewResponse;
import com.acomi.acomi_backend.admin.bulkimport.property.dto.PropertyBulkImportResultResponse;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class PropertyBulkImportServiceTest {

    @Mock
    private AdminPropertyRegistrationService adminPropertyRegistrationService;

    @Mock
    private AdminMessRegistrationService adminMessRegistrationService;

    @Mock
    private PropertyRegistrationRepository propertyRegistrationRepository;

    @Mock
    private MessRegistrationRepository messRegistrationRepository;

    @Mock
    private PropertyBulkImportDuplicateDetector duplicateDetector;

    private PropertyBulkImportService service;
    private ObjectMapper objectMapper;
    private String mappingJson;

    @BeforeEach
    void setUp() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        objectMapper = new ObjectMapper();
        service = new PropertyBulkImportService(
                adminPropertyRegistrationService,
                adminMessRegistrationService,
                propertyRegistrationRepository,
                messRegistrationRepository,
                duplicateDetector,
                validator,
                objectMapper);

        Map<String, String> mapping = new LinkedHashMap<>();
        for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
            mapping.put(field.name(), null);
        }
        mapping.put("PROPERTY_TYPE", "Property Type");
        mapping.put("PROPERTY_NAME", "Property Name");
        mapping.put("MOBILE_NUMBER", "Mobile Number");
        mapping.put("CITY", "City");
        mappingJson = objectMapper.writeValueAsString(mapping);

        lenient().when(duplicateDetector.loadExisting(any())).thenReturn(List.of());
        lenient().when(duplicateDetector.findMatch(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void preview_mixedValidInvalidAndBlank() throws Exception {
        MockMultipartFile file = xlsxFile(
                new String[] {"Property Type", "Property Name", "Mobile Number", "City"},
                new String[] {"PG", "Alpha PG", "9876543210", "Pune"},
                new String[] {"MESS", "Good Mess", "9876543211", "Pune"},
                new String[] {"", "", "", ""},
                new String[] {"Hostel", "Beta Hostel", "123", "Pune"});

        PropertyBulkImportPreviewResponse preview = service.preview(file, mappingJson, false);

        assertThat(preview.getTotalRows()).isEqualTo(4);
        assertThat(preview.getValid()).isEqualTo(2);
        assertThat(preview.getInvalid()).isEqualTo(1);
        assertThat(preview.getBlank()).isEqualTo(1);
        assertThat(preview.getRows()).extracting(r -> r.getStatus())
                .containsExactly("VALID", "VALID", "BLANK", "INVALID");
    }

    @Test
    void import_partialSuccess_reportsConvertedWhenCreatePublishedSpace() throws Exception {
        MockMultipartFile file = xlsxFile(
                new String[] {"Property Type", "Property Name", "Mobile Number", "City"},
                new String[] {"PG", "Alpha PG", "9876543210", "Pune"},
                new String[] {"Villa", "Bad Villa", "9876543211", "Pune"},
                new String[] {"", "", "", ""},
                new String[] {"Hostel", "Beta Hostel", "9123456780", "Mumbai"});

        UUID spaceId = UUID.randomUUID();
        PropertyRegistrationEntity alphaEntity = PropertyRegistrationEntity.builder()
                .convertedSpaceId(spaceId)
                .build();

        when(adminPropertyRegistrationService.create(any(AdminCreatePropertyRegistrationRequest.class)))
                .thenAnswer(invocation -> {
                    AdminCreatePropertyRegistrationRequest req = invocation.getArgument(0);
                    if ("Beta Hostel".equals(req.getPropertyName())) {
                        throw new BusinessException("simulated failure");
                    }
                    return PropertyRegistrationResponse.builder()
                            .reference("PR-2026-000001")
                            .priceBasis(com.acomi.acomi_backend.property.domain.model.PriceBasis.PER_BED)
                            .submittedAt(LocalDateTime.now())
                            .build();
                });
        when(propertyRegistrationRepository.findByReference("PR-2026-000001"))
                .thenReturn(Optional.of(alphaEntity));

        PropertyBulkImportResultResponse result = service.importRows(file, mappingJson, false);

        assertThat(result.getTotalRows()).isEqualTo(4);
        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getConverted()).isEqualTo(1);
        assertThat(result.getImportedOnly()).isZero();
        assertThat(result.getFailed()).isEqualTo(2);
        assertThat(result.getBlankSkipped()).isEqualTo(1);
        assertThat(result.getRows()).extracting(r -> r.getStatus())
                .containsExactly("CONVERTED", "INVALID", "BLANK", "FAILED");
        assertThat(result.getRows().get(0).getSpaceId()).isEqualTo(spaceId.toString());

        verify(adminPropertyRegistrationService, times(2)).create(any());
    }

    @Test
    void import_messRow_reportsConverted() throws Exception {
        MockMultipartFile file = xlsxFile(
                new String[] {"Property Type", "Property Name", "Mobile Number", "City"},
                new String[] {"MESS", "Campus Mess", "9876543210", "Pune"});

        UUID spaceId = UUID.randomUUID();
        MessRegistrationEntity messEntity = MessRegistrationEntity.builder()
                .convertedSpaceId(spaceId)
                .build();

        when(adminMessRegistrationService.create(any()))
                .thenReturn(com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationResponse.builder()
                        .reference("MR-2026-000001")
                        .submittedAt(LocalDateTime.now())
                        .build());
        when(messRegistrationRepository.findByReference("MR-2026-000001"))
                .thenReturn(Optional.of(messEntity));

        PropertyBulkImportResultResponse result = service.importRows(file, mappingJson, false);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getConverted()).isEqualTo(1);
        assertThat(result.getRows().get(0).getStatus()).isEqualTo("CONVERTED");
        verify(adminMessRegistrationService, times(1)).create(any());
        verify(adminPropertyRegistrationService, never()).create(any());
    }

    @Test
    void import_doesNotCallCreateForInvalidOrBlank() throws Exception {
        MockMultipartFile file = xlsxFile(
                new String[] {"Property Type", "Property Name", "Mobile Number", "City"},
                new String[] {"Villa", "Nope", "9876543210", "Pune"},
                new String[] {"", "", "", ""});

        PropertyBulkImportResultResponse result = service.importRows(file, mappingJson, false);

        assertThat(result.getImported()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getBlankSkipped()).isEqualTo(1);
        verify(adminPropertyRegistrationService, never()).create(any());
        verify(adminMessRegistrationService, never()).create(any());
    }

    @Test
    void analyze_returnsSuggestedMappingAndSamples() throws Exception {
        MockMultipartFile file = xlsxFile(
                new String[] {"Property Name", "City", "Mobile Number"},
                new String[] {"Alpha", "Pune", "9876543210"},
                new String[] {"Beta", "Mumbai", "9123456780"});

        var analyzed = service.analyze(file);
        assertThat(analyzed.getHeaders()).containsExactly("Property Name", "City", "Mobile Number");
        assertThat(analyzed.getDataRowCount()).isEqualTo(2);
        assertThat(analyzed.getSampleRows()).hasSize(2);
        assertThat(analyzed.getSuggestedMapping().get("PROPERTY_NAME")).isEqualTo("Property Name");
        assertThat(analyzed.getSuggestedMapping().get("CITY")).isEqualTo("City");
        assertThat(analyzed.getSuggestedMapping().get("MOBILE_NUMBER")).isEqualTo("Mobile Number");
    }

    private static MockMultipartFile xlsxFile(String[] headers, String[]... dataRows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                headerRow.createCell(c).setCellValue(headers[c]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < dataRows[r].length; c++) {
                    row.createCell(c).setCellValue(dataRows[r][c]);
                }
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "leads.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new ByteArrayInputStream(out.toByteArray()));
        }
    }
}

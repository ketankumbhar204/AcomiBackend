package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PropertyBulkImportTemplateAlignmentTest {

    @Test
    void sampleRowMatchesHeaderOrdinals() throws Exception {
        PropertyBulkImportService service = new PropertyBulkImportService(
                Mockito.mock(com.acomi.acomi_backend.admin.application.service.AdminPropertyRegistrationService.class),
                Mockito.mock(com.acomi.acomi_backend.admin.application.service.AdminMessRegistrationService.class),
                Mockito.mock(com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository.class),
                Mockito.mock(com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository.class),
                Mockito.mock(PropertyBulkImportDuplicateDetector.class),
                Mockito.mock(jakarta.validation.Validator.class),
                Mockito.mock(com.fasterxml.jackson.databind.ObjectMapper.class));

        byte[] bytes = service.buildTemplate();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Sheet1");
            Row header = sheet.getRow(0);
            Row sample = sheet.getRow(1);
            DataFormatter fmt = new DataFormatter();

            PropertyBulkImportField[] fields = PropertyBulkImportField.values();
            assertThat(header.getLastCellNum()).isEqualTo((short) fields.length);

            for (int i = 0; i < fields.length; i++) {
                assertThat(fmt.formatCellValue(header.getCell(i))).isEqualTo(fields[i].getTemplateHeader());
            }

            assertThat(fmt.formatCellValue(sample.getCell(PropertyBulkImportField.CONTACT_3.ordinal())))
                    .isEqualTo("9988776655");
            assertThat(fmt.formatCellValue(sample.getCell(PropertyBulkImportField.ADDRESS_LINE.ordinal())))
                    .isEqualTo("12 MG Road");
            assertThat(fmt.formatCellValue(sample.getCell(PropertyBulkImportField.CITY.ordinal())))
                    .isEqualTo("Bengaluru");
            assertThat(fmt.formatCellValue(sample.getCell(PropertyBulkImportField.GENDER.ordinal())))
                    .isEqualTo("Both");
            assertThat(fmt.formatCellValue(sample.getCell(PropertyBulkImportField.TEST_LEAD.ordinal())))
                    .isEqualTo("false");
            assertThat(fmt.formatCellValue(sample.getCell(PropertyBulkImportField.MAP_URL.ordinal())))
                    .contains("12.97,77.59");
        }
    }
}

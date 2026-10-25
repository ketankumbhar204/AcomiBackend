package com.acomi.acomi_backend.admin.bulkimport.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class PropertyBulkImportExcelReaderTest {

    @Test
    void parse_accepts1999FilledRows() throws IOException {
        MockMultipartFile file = workbook(1999, false);
        var parsed = PropertyBulkImportExcelReader.parse(file);
        assertThat(filledCount(parsed)).isEqualTo(1999);
    }

    @Test
    void parse_accepts2000FilledRows() throws IOException {
        MockMultipartFile file = workbook(2000, false);
        var parsed = PropertyBulkImportExcelReader.parse(file);
        assertThat(filledCount(parsed)).isEqualTo(2000);
    }

    @Test
    void parse_accepts2000FilledRows_andIgnoresBlankRows() throws IOException {
        MockMultipartFile file = workbook(2000, true);
        var parsed = PropertyBulkImportExcelReader.parse(file);
        assertThat(parsed.dataRows()).hasSize(2001);
        assertThat(filledCount(parsed)).isEqualTo(2000);
    }

    @Test
    void preflight_rejects2001stFilledRow_beforeFullParse() throws IOException {
        MockMultipartFile file = workbook(2001, false);
        String limitMessage = "File exceeds maximum of " + PropertyBulkImportExcelReader.MAX_DATA_ROWS + " data rows";
        assertThatThrownBy(() -> PropertyBulkImportExcelReader.assertWithinDataRowLimit(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(limitMessage)
                .hasMessageContaining("Import stopped");
        assertThatThrownBy(() -> PropertyBulkImportExcelReader.parse(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Import stopped")
                .hasMessageContaining(limitMessage);
    }

    @Test
    void preflight_rejects5000Rows_withoutFullParse() throws IOException {
        MockMultipartFile file = workbook(5000, false);
        long started = System.nanoTime();
        assertThatThrownBy(() -> PropertyBulkImportExcelReader.assertWithinDataRowLimit(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Import stopped")
                .hasMessageContaining("Maximum allowed: " + PropertyBulkImportExcelReader.MAX_DATA_ROWS);
        long millis = (System.nanoTime() - started) / 1_000_000L;
        assertThat(millis).isLessThan(5_000L);
    }

    @Test
    void sampleWorkbooks_preflightBeforeParse() throws IOException {
        auditSample(Path.of("K:/Acomi_Leads_Data/Docs/Telangana/Warangal/Warangal.xlsx"));
        auditSample(Path.of("K:/Acomi_Leads_Data/Docs/chennai/Chennai.xlsx"));
    }

    @Test
    void parse_readsHeaderAfterBlankRows_andSkipsSummarySheet() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summary = workbook.createSheet("Export List_Contact 1");
            Row summaryHeader = summary.createRow(0);
            summaryHeader.createCell(0).setCellValue("Count");
            summaryHeader.createCell(1).setCellValue("Percent");
            summary.createRow(1).createCell(0).setCellValue("10");

            Sheet leads = workbook.createSheet("Leads");
            leads.createRow(0);
            Row header = leads.createRow(4);
            header.createCell(0).setCellValue("Sr. No.");
            header.createCell(1).setCellValue("Space type");
            header.createCell(2).setCellValue("Property Name");
            header.createCell(3).setCellValue("Contact 1");
            header.createCell(4).setCellValue("Full Google Maps Link");
            Row data = leads.createRow(5);
            data.createCell(1).setCellValue("PG");
            data.createCell(2).setCellValue("Sunrise PG");
            data.createCell(3).setCellValue(9876543210d);

            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "late.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new ByteArrayInputStream(out.toByteArray()));
            var parsed = PropertyBulkImportExcelReader.parse(file);
            assertThat(parsed.headerRowNumber()).isEqualTo(5);
            assertThat(parsed.headers()).contains("Full Google Maps Link");
            assertThat(parsed.dataRows()).hasSize(1);
            assertThat(parsed.dataRows().get(0).get("Contact 1")).isEqualTo("9876543210");
            assertThat(PropertyBulkImportField.MAP_URL.matchesHeader("Full Google Maps Link")).isTrue();
        }
    }

    @Test
    void parse_rejectsDuplicateHeaders() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Leads");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Property Name");
            header.createCell(1).setCellValue("Space type");
            header.createCell(2).setCellValue("Contact 1");
            header.createCell(3).setCellValue("Full Google Maps Link");
            header.createCell(4).setCellValue("Google Maps Link");
            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dup.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new ByteArrayInputStream(out.toByteArray()));
            assertThatThrownBy(() -> PropertyBulkImportExcelReader.parse(file))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("both map to MAP_URL");
        }
    }

    @Test
    void parse_rejectsWorkbookWithOnlyASummarySheet() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summary = workbook.createSheet("Summary");
            summary.createRow(0).createCell(0).setCellValue("Count");
            workbook.write(out);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "summary.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new ByteArrayInputStream(out.toByteArray()));
            assertThatThrownBy(() -> PropertyBulkImportExcelReader.parse(file))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("No worksheet has the property import columns");
        }
    }

    private static long filledCount(PropertyBulkImportExcelReader.ParsedWorkbook parsed) {
        return parsed.dataRows().stream()
                .filter(row -> row.values().stream().anyMatch(value -> value != null && !value.isBlank()))
                .count();
    }

    private static void auditSample(Path path) throws IOException {
        Assumptions.assumeTrue(Files.exists(path), "sample workbook missing: " + path);
        byte[] bytes = Files.readAllBytes(path);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                path.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
        long started = System.nanoTime();
        try {
            PropertyBulkImportExcelReader.assertWithinDataRowLimit(file);
        } catch (BusinessException ex) {
            long millis = (System.nanoTime() - started) / 1_000_000L;
            assertThat(ex.getMessage()).contains("Import stopped");
            assertThat(millis).isLessThan(5_000L);
            System.out.println(path.getFileName() + " rejected in " + millis + "ms");
            return;
        }
        var parsed = PropertyBulkImportExcelReader.parse(file);
        long filled = filledCount(parsed);
        assertThat(filled).isLessThanOrEqualTo(PropertyBulkImportExcelReader.MAX_DATA_ROWS);
        Map<String, Integer> food = new LinkedHashMap<>();
        Map<String, Integer> types = new LinkedHashMap<>();
        int availableInvalid = 0;
        int unknownType = 0;
        String typeHeader = headerContaining(parsed, "space type", "property type");
        String foodHeader = headerContaining(parsed, "food");
        for (var row : parsed.dataRows()) {
            if (typeHeader != null) {
                var type = PropertyBulkImportValueParser.parsePropertyType(row.get(typeHeader));
                String label = type.isInvalid() ? "INVALID:" + type.error() : String.valueOf(type.value());
                types.merge(label, 1, Integer::sum);
                if (type.isInvalid() && type.error() != null && type.error().startsWith("Unknown property type:")) {
                    unknownType++;
                }
            }
            if (foodHeader != null) {
                var foodResult = PropertyBulkImportValueParser.parseFoodIncluded(row.get(foodHeader));
                String raw = row.get(foodHeader) == null ? "" : row.get(foodHeader).trim();
                String label = foodResult.isInvalid() ? "INVALID:" + raw : String.valueOf(foodResult.value());
                food.merge(label, 1, Integer::sum);
                if (foodResult.isInvalid()
                        && raw.equalsIgnoreCase("Available")
                        && foodResult.error() != null
                        && foodResult.error().contains("does not confirm")) {
                    availableInvalid++;
                }
            }
        }
        System.out.println(path.getFileName() + " filled=" + filled
                + " types=" + types + " food=" + food
                + " unknownTypes=" + unknownType + " availableInvalid=" + availableInvalid);
        if (food.containsKey("INVALID:Available")) {
            assertThat(availableInvalid).isGreaterThan(0);
        }
    }

    private static String headerContaining(
            PropertyBulkImportExcelReader.ParsedWorkbook parsed, String... needles) {
        for (String header : parsed.headers()) {
            if (header == null) {
                continue;
            }
            String lower = header.toLowerCase();
            for (String needle : needles) {
                if (lower.contains(needle)) {
                    return header;
                }
            }
        }
        return null;
    }

    private static MockMultipartFile workbook(int filledRows, boolean insertBlank) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Property Name");
            header.createCell(1).setCellValue("Space type");
            header.createCell(2).setCellValue("Contact 1");
            int excelRow = 1;
            for (int i = 0; i < filledRows; i++) {
                if (insertBlank && i == 1) {
                    sheet.createRow(excelRow++);
                }
                sheet.createRow(excelRow++).createCell(0).setCellValue("Lead " + (i + 1));
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

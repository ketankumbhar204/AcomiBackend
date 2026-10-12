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

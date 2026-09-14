package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/** Reads admin property bulk-import .xlsx workbooks. */
public final class PropertyBulkImportExcelReader {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_DATA_ROWS = 1000;

    private static final DataFormatter FORMATTER = new DataFormatter();

    private PropertyBulkImportExcelReader() {}

    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Excel file is required", HttpStatus.BAD_REQUEST);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BusinessException("Excel file name is required", HttpStatus.BAD_REQUEST);
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xls") || lower.endsWith(".csv")) {
            throw new BusinessException(
                    "Only .xlsx files are supported (got: " + filename + ")", HttpStatus.BAD_REQUEST);
        }
        if (!lower.endsWith(".xlsx")) {
            throw new BusinessException(
                    "Only .xlsx files are supported (got: " + filename + ")", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(
                    "File exceeds maximum size of 5MB", HttpStatus.BAD_REQUEST);
        }
    }

    public record ParsedWorkbook(List<String> headers, List<Map<String, String>> dataRows) {}

    public static ParsedWorkbook parse(MultipartFile file) {
        validateFile(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("Workbook has no sheets", HttpStatus.BAD_REQUEST);
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException("Header row is missing", HttpStatus.BAD_REQUEST);
            }
            List<String> headers = readHeaderCells(headerRow);
            if (headers.stream().allMatch(h -> h == null || h.isBlank())) {
                throw new BusinessException("Header row is empty", HttpStatus.BAD_REQUEST);
            }

            List<Map<String, String>> dataRows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header == null || header.isBlank()) {
                        continue;
                    }
                    String cellValue = row == null ? "" : cellAsString(row.getCell(c));
                    values.put(header, cellValue);
                }
                dataRows.add(values);
            }
            if (dataRows.size() > MAX_DATA_ROWS) {
                throw new BusinessException(
                        "File exceeds maximum of " + MAX_DATA_ROWS + " data rows",
                        HttpStatus.BAD_REQUEST);
            }
            return new ParsedWorkbook(headers, dataRows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("Failed to read Excel file: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            throw new BusinessException(
                    "Invalid or corrupt .xlsx file: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private static List<String> readHeaderCells(Row headerRow) {
        int lastCell = headerRow.getLastCellNum();
        if (lastCell < 0) {
            return List.of();
        }
        List<String> headers = new ArrayList<>(lastCell);
        for (int c = 0; c < lastCell; c++) {
            headers.add(cellAsString(headerRow.getCell(c)).trim());
        }
        // Trim trailing blank headers
        int end = headers.size();
        while (end > 0 && (headers.get(end - 1) == null || headers.get(end - 1).isBlank())) {
            end--;
        }
        return new ArrayList<>(headers.subList(0, end));
    }

    static String cellAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return FORMATTER.formatCellValue(cell);
    }
}

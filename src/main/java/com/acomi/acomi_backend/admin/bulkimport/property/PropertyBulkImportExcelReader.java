package com.acomi.acomi_backend.admin.bulkimport.property;

import com.acomi.acomi_backend.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

/** Reads admin property bulk-import .xlsx workbooks. */
public final class PropertyBulkImportExcelReader {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_DATA_ROWS = 2000;

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

    public record ParsedWorkbook(List<String> headers, List<Map<String, String>> dataRows, int headerRowNumber) {}

    public static String dataRowLimitMessage() {
        return "File exceeds maximum of " + MAX_DATA_ROWS
                + " data rows. Import stopped. Maximum allowed: " + MAX_DATA_ROWS
                + ". Please upload a smaller file.";
    }

    /**
     * Counts populated data rows from worksheet XML and stops at {@code MAX_DATA_ROWS + 1}.
     * The header row is ignored. Blank rows are not counted. Does not validate cell values.
     */
    public static void assertWithinDataRowLimit(MultipartFile file) {
        validateFile(file);
        try (InputStream in = file.getInputStream(); OPCPackage pkg = OPCPackage.open(in)) {
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                throw new BusinessException("Workbook has no sheets", HttpStatus.BAD_REQUEST);
            }
            try (InputStream sheet = sheets.next()) {
                countFilledDataRows(sheet, strings);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    "Invalid or corrupt .xlsx file: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public static ParsedWorkbook parse(MultipartFile file) {
        assertWithinDataRowLimit(file);
        try (InputStream in = file.getInputStream(); Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = findLeadSheet(workbook);
            int headerIndex = findHeaderRow(sheet);
            if (headerIndex < 0) {
                throw new BusinessException(
                        "No worksheet has the property import columns", HttpStatus.BAD_REQUEST);
            }
            Row headerRow = sheet.getRow(headerIndex);
            List<String> headers = readHeaderCells(headerRow);
            rejectDuplicateHeaders(headers);

            List<Map<String, String>> dataRows = new ArrayList<>();
            int lastRow = lastNonEmptyRow(sheet, headers, headerIndex);
            for (int r = headerIndex + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                dataRows.add(readDataRow(row, headers));
            }
            long filledRows = dataRows.stream().filter(row -> !isEmptyRow(row)).count();
            if (filledRows > MAX_DATA_ROWS) {
                throw new BusinessException(dataRowLimitMessage(), HttpStatus.BAD_REQUEST);
            }
            return new ParsedWorkbook(headers, dataRows, headerIndex + 1);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("Failed to read Excel file: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            throw new BusinessException(
                    "Invalid or corrupt .xlsx file: " + ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private static Sheet findLeadSheet(Workbook workbook) {
        if (workbook.getNumberOfSheets() == 0) {
            throw new BusinessException("Workbook has no sheets", HttpStatus.BAD_REQUEST);
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (findHeaderRow(sheet) >= 0) {
                return sheet;
            }
        }
        throw new BusinessException("No worksheet has the property import columns", HttpStatus.BAD_REQUEST);
    }

    /** First row that matches at least three known import columns. Summary sheets do not. */
    static int findHeaderRow(Sheet sheet) {
        if (sheet == null) {
            return -1;
        }
        int last = Math.min(sheet.getLastRowNum(), 499);
        for (int r = 0; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int matches = 0;
            java.util.Set<PropertyBulkImportField> seen = java.util.EnumSet.noneOf(PropertyBulkImportField.class);
            int lastCell = row.getLastCellNum();
            for (int c = 0; c < lastCell; c++) {
                String text = cellAsString(row.getCell(c)).trim();
                if (text.isEmpty()) {
                    continue;
                }
                for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
                    if (field.matchesHeader(text) && seen.add(field)) {
                        matches++;
                        break;
                    }
                }
            }
            if (matches >= 3) {
                return r;
            }
        }
        return -1;
    }

    private static void rejectDuplicateHeaders(List<String> headers) {
        java.util.Map<PropertyBulkImportField, String> used = new java.util.EnumMap<>(PropertyBulkImportField.class);
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                continue;
            }
            for (PropertyBulkImportField field : PropertyBulkImportField.values()) {
                if (!field.matchesHeader(header)) {
                    continue;
                }
                String previous = used.putIfAbsent(field, header);
                if (previous != null && !previous.equalsIgnoreCase(header)) {
                    throw new BusinessException(
                            "Columns \"" + previous + "\" and \"" + header + "\" both map to " + field.name(),
                            HttpStatus.BAD_REQUEST);
                }
                break;
            }
        }
    }

    private static int lastNonEmptyRow(Sheet sheet, List<String> headers, int headerIndex) {
        int last = sheet.getLastRowNum();
        while (last > headerIndex && isEmptyRow(readDataRow(sheet.getRow(last), headers))) {
            last--;
        }
        return last;
    }

    private static Map<String, String> readDataRow(Row row, List<String> headers) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int c = 0; c < headers.size(); c++) {
            String header = headers.get(c);
            if (header == null || header.isBlank()) {
                continue;
            }
            values.put(header, row == null ? "" : cellAsString(row.getCell(c)));
        }
        return values;
    }

    private static boolean isEmptyRow(Map<String, String> values) {
        return values.values().stream().allMatch(value -> value == null || value.isBlank());
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

    private static void countFilledDataRows(InputStream sheet, ReadOnlySharedStringsTable strings)
            throws Exception {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader xml = factory.createXMLStreamReader(sheet);
        try {
            int filled = 0;
            int sequentialRow = 0;
            boolean inRow = false;
            int rowNumber = 0;
            boolean rowHasValue = false;
            boolean inCell = false;
            String cellType = null;
            boolean captureText = false;
            StringBuilder text = new StringBuilder();
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = xml.getLocalName();
                    if ("row".equals(name)) {
                        inRow = true;
                        sequentialRow++;
                        String r = attribute(xml, "r");
                        rowNumber = r == null ? sequentialRow : Integer.parseInt(r);
                        rowHasValue = false;
                    } else if (inRow && "c".equals(name)) {
                        inCell = true;
                        cellType = attribute(xml, "t");
                        text.setLength(0);
                    } else if (inCell && ("v".equals(name) || "t".equals(name))) {
                        captureText = true;
                        text.setLength(0);
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && captureText) {
                    text.append(xml.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = xml.getLocalName();
                    if ("v".equals(name) || "t".equals(name)) {
                        captureText = false;
                    } else if ("c".equals(name) && inCell) {
                        if (cellHasText(cellType, text.toString(), strings)) {
                            rowHasValue = true;
                        }
                        inCell = false;
                        cellType = null;
                    } else if ("row".equals(name) && inRow) {
                        if (rowNumber > 1 && rowHasValue) {
                            filled++;
                            if (filled > MAX_DATA_ROWS) {
                                throw new BusinessException(dataRowLimitMessage(), HttpStatus.BAD_REQUEST);
                            }
                        }
                        inRow = false;
                    }
                }
            }
        } finally {
            xml.close();
        }
    }

    private static String attribute(XMLStreamReader xml, String localName) {
        for (int i = 0; i < xml.getAttributeCount(); i++) {
            if (localName.equals(xml.getAttributeLocalName(i))) {
                return xml.getAttributeValue(i);
            }
        }
        return null;
    }

    private static boolean cellHasText(String type, String raw, ReadOnlySharedStringsTable strings) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw;
        if ("s".equals(type)) {
            try {
                value = strings.getItemAt(Integer.parseInt(raw.trim())).getString();
            } catch (RuntimeException ex) {
                return true;
            }
        }
        return value != null && !value.isBlank();
    }

    static String cellAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double number = cell.getNumericCellValue();
            if (Double.isFinite(number) && number == Math.rint(number) && Math.abs(number) < 1e15d) {
                return String.format(Locale.US, "%.0f", number);
            }
        }
        return FORMATTER.formatCellValue(cell);
    }
}

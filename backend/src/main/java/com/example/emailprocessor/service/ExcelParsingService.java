package com.example.emailprocessor.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ExcelParsingService {

    public Map<String, String> extractData(File excelFile) throws IOException {
        Map<String, String> data = new HashMap<>();
        StringBuilder textBuilder = new StringBuilder();

        try (Workbook workbook = WorkbookFactory.create(excelFile)) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowNum = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowNum);

            // If it's a structured Excel with specific columns, prioritize those
            int valueCol = findColumn(headerRow, "vr cuota más 4x1000", "vr cuota mas 4x1000", "valor deducción", "valor deduccion");
            
            if (valueCol != -1) {
                double totalSum = 0;
                for (int i = headerRowNum + 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null) {
                        totalSum += getCellValueAsDouble(row.getCell(valueCol));
                    }
                }
                data.put("total", String.valueOf(totalSum));
            }

            // Still build text for other fields and rawText
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet s = workbook.getSheetAt(i);
                for (Row row : s) {
                    for (Cell cell : row) {
                        CellType type = cell.getCellType();
                        if (type == CellType.FORMULA) {
                            type = cell.getCachedFormulaResultType();
                        }
                        switch (type) {
                            case STRING -> textBuilder.append(cell.getStringCellValue()).append(" ");
                            case NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    textBuilder.append(cell.getDateCellValue()).append(" ");
                                } else {
                                    textBuilder.append(cell.getNumericCellValue()).append(" ");
                                }
                            }
                            case BOOLEAN -> textBuilder.append(cell.getBooleanCellValue()).append(" ");
                            default -> {}
                        }
                    }
                    textBuilder.append("\n");
                }
            }
        }

        String text = textBuilder.toString();
        
        // Reuse the same logic as PDF for basic extraction
        data.put("nro factura", findValue(text, "(?:nro factura|factura nro|nro de factura|invoice number|invoice|factura|nro)\\s*[:#]?\\s*([a-zA-Z0-9-]+)"));
        data.put("fecha", findValue(text, "(?:fecha factura|fecha hora factura|fecha de emision|fecha|date|invoice date)\\s*[:]?\\s*([\\d]{1,2}[/-][\\d]{1,2}[/-][\\d]{2,4}|[\\d]{4}[/-][\\d]{1,2}[/-][\\d]{1,2})"));
        
        if (!data.containsKey("total")) {
            data.put("total", findValue(text, "(?:total a pagar|total factura|valor total|monto total|total amount|total|valor|monto|amount)\\s*[:$]?\\s*([\\d.,]+)"));
        }
        
        data.put("rawText", text);
        return data;
    }

    private String findValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Not found";
    }

    private final DataFormatter dataFormatter = new DataFormatter();

    public Map<String, Double> parseBaseFile(String path) throws IOException {
        Map<String, Double> baseData = new HashMap<>();
        File file = new File(path);
        if (!file.exists()) {
            log.error("Base file not found at: {}", path);
            return baseData;
        }

        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            log.info("Parsing base file: {}. Sheet: {}. Rows: {}", path, sheet.getSheetName(), sheet.getLastRowNum());
            
            int headerRowNum = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowNum);
            
            // User specified: 'Empleado' for ID
            int idCol = findColumn(headerRow, "empleado", "cedula", "identific", "documento");
            if (idCol == -1) {
                log.warn("ID column not found by name, using column 0");
                idCol = 0;
            }

            // User requested to take the value from the LAST column of the file
            int valueCol = headerRow.getLastCellNum() - 1;
            
            log.info("Base file columns (Row {}) - ID: {}, Value (Last Col): {}", headerRowNum, idCol, valueCol);

            for (int i = headerRowNum + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                String id = getCleanId(row.getCell(idCol));
                Double value = getCellValueAsDouble(row.getCell(valueCol));
                
                if (id != null && !id.isEmpty()) {
                    baseData.put(id, value);
                }
            }
            
            if (baseData.isEmpty()) {
                log.warn("No data was extracted from base file. Trying fallback extraction...");
                for (int i = headerRowNum + 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null && row.getCell(0) != null) {
                        String id = getCleanId(row.getCell(0));
                        Double value = getCellValueAsDouble(row.getCell(1));
                        if (!id.isEmpty()) baseData.put(id, value);
                    }
                }
            }
            log.info("Extracted {} records from base file", baseData.size());
        } catch (Exception e) {
            log.error("Error parsing base file", e);
        }
        return baseData;
    }

    public List<Map<String, Object>> parseIncomingFile(File file) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            log.info("Parsing incoming file: {}. Rows: {}", file.getName(), sheet.getLastRowNum());
            
            int headerRowNum = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowNum);
            
            // User specified: 'cedula' for ID and 'Vr Cuota más 4x1000' for value
            int idCol = findColumn(headerRow, "cedula", "empleado", "documento", "nit", "id");
            int valueCol = findColumn(headerRow, "vr cuota mas 4x1000", "vr cuota mas 4x1000", "valor deduccion", "cuota", "valor");

            // Fallback logic if columns not found by name
            if (idCol == -1) {
                log.warn("ID column not found in incoming file, using column 0");
                idCol = 0;
            }
            if (valueCol == -1) {
                log.warn("Value column not found in incoming file, using column 1");
                valueCol = 1;
            }

            log.info("Incoming file columns (Row {}) - ID: {}, Value: {}", headerRowNum, idCol, valueCol);

            for (int i = headerRowNum + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                String id = getCleanId(row.getCell(idCol));
                Double value = getCellValueAsDouble(row.getCell(valueCol));
                
                if (id != null && !id.isEmpty()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("id", id);
                    record.put("value", value);
                    records.add(record);
                }
            }
            log.info("Extracted {} records from incoming file", records.size());
        }
        return records;
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                .trim()
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ñ", "n");
    }

    private int findHeaderRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 50); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            boolean hasId = false;
            boolean hasValue = false;
            
            for (Cell cell : row) {
                String val = normalize(getCellValueAsString(cell));
                // Check for common ID column names
                if (val.contains("cedula") || val.contains("identific") || val.contains("documento") || 
                    val.contains("empleado") || val.contains("nit") || val.contains("id")) {
                    hasId = true;
                }
                // Check for common value column names
                if (val.contains("cuota") || val.contains("valor") || val.contains("deduccion") || 
                    val.contains("monto") || val.contains("total")) {
                    hasValue = true;
                }
            }
            
            if (hasId && hasValue) {
                log.info("Detected header row at index: {}", i);
                return i;
            }
        }
        log.warn("Could not detect header row with confidence, defaulting to row 0");
        return 0;
    }

    private int findColumn(Row row, String... keywords) {
        if (row == null) return -1;
        
        // First pass: look for exact or very close matches
        for (Cell cell : row) {
            String header = normalize(getCellValueAsString(cell));
            for (String keyword : keywords) {
                if (header.equals(normalize(keyword))) {
                    return cell.getColumnIndex();
                }
            }
        }
        
        // Second pass: look for contains
        for (Cell cell : row) {
            String header = normalize(getCellValueAsString(cell));
            for (String keyword : keywords) {
                if (header.contains(normalize(keyword))) {
                    return cell.getColumnIndex();
                }
            }
        }
        return -1;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return dataFormatter.formatCellValue(cell).trim();
    }

    private String getCleanId(Cell cell) {
        if (cell == null) return "";
        
        String value;
        if (cell.getCellType() == CellType.NUMERIC) {
            // For numeric cells, get the raw value to avoid scientific notation or formatting issues
            double numericValue = cell.getNumericCellValue();
            // Convert to long to remove any decimals (IDs shouldn't have them)
            value = String.valueOf((long) numericValue);
        } else {
            value = getCellValueAsString(cell);
        }

        if (value == null || value.trim().isEmpty()) return "";
        
        // Remove any non-digit characters for IDs that are purely numeric
        String clean = value.trim().replaceAll("[^0-9]", "");
        
        if (clean.isEmpty()) {
            // If it's not purely numeric, return the trimmed original string
            return value.trim();
        }
        
        return clean;
    }

    public Double parseStringToDouble(String val) {
        if (val == null || val.trim().isEmpty()) return 0.0;
        try {
            // Clean currency symbols and spaces, but keep minus sign
            String cleanVal = val.trim().replaceAll("[^\\d,.-]", "");
            
            if (cleanVal.isEmpty() || cleanVal.equals("-")) return 0.0;

            int lastComma = cleanVal.lastIndexOf(',');
            int lastDot = cleanVal.lastIndexOf('.');
            
            if (lastComma != -1 && lastDot != -1) {
                if (lastComma > lastDot) {
                    // Format like 1.234,56 -> convert to 1234.56
                    cleanVal = cleanVal.replace(".", "").replace(",", ".");
                } else {
                    // Format like 1,234.56 -> convert to 1234.56
                    cleanVal = cleanVal.replace(",", "");
                }
            } else if (lastComma != -1) {
                // Only commas present. Check if it's a decimal separator or thousands separator
                if (cleanVal.indexOf(',') != lastComma) {
                    // Multiple commas -> thousands separators
                    cleanVal = cleanVal.replace(",", "");
                } else {
                    // Single comma -> could be decimal. In many locales it is.
                    cleanVal = cleanVal.replace(",", ".");
                }
            } else if (lastDot != -1) {
                // Only dots present. Check if it's a decimal separator or thousands separator
                if (cleanVal.indexOf('.') != lastDot) {
                    // Multiple dots -> thousands separators
                    cleanVal = cleanVal.replace(".", "");
                }
                // If single dot, Double.parseDouble handles it correctly as decimal
            }
            
            return Double.parseDouble(cleanVal);
        } catch (Exception e) {
            log.warn("Failed to parse numeric value from string: '{}'", val);
            return 0.0;
        }
    }

    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null) return 0.0;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }

        if (type == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (type == CellType.STRING) {
            return parseStringToDouble(cell.getStringCellValue());
        }
        return 0.0;
    }
}

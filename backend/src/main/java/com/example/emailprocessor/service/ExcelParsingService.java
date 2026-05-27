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
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING -> textBuilder.append(cell.getStringCellValue()).append(" ");
                            case NUMERIC -> {
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    textBuilder.append(cell.getDateCellValue()).append(" ");
                                } else {
                                    textBuilder.append(cell.getNumericCellValue()).append(" ");
                                }
                            }
                            case BOOLEAN -> textBuilder.append(cell.getBooleanCellValue()).append(" ");
                            case FORMULA -> textBuilder.append(cell.getCellFormula()).append(" ");
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
        data.put("total", findValue(text, "(?:total a pagar|total factura|valor total|monto total|total amount|total|valor|monto|amount)\\s*[:$]?\\s*([\\d.,]+)"));
        
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

    public Map<String, Double> parseBaseFile(String path) throws IOException {
        Map<String, Double> baseData = new HashMap<>();
        File file = new File(path);
        if (!file.exists()) {
            log.error("Base file not found at: {}", path);
            return baseData;
        }

        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            int idCol = -1;
            int valueCol = -1;

            for (Cell cell : headerRow) {
                String header = getCellValueAsString(cell).toLowerCase().trim();
                if (header.contains("identific") || header.contains("cedula") || header.equals("id") || header.contains("documento") || header.contains("nit")) {
                    idCol = cell.getColumnIndex();
                } else if (header.contains("vr") || header.contains("cuota") || header.contains("4x1000") || header.contains("valor")) {
                    valueCol = cell.getColumnIndex();
                }
            }

            if (idCol == -1 || valueCol == -1) {
                log.warn("Could not find required columns in base file. ID col: {}, Value col: {}", idCol, valueCol);
                // Fallback to common indices if headers don't match
                idCol = 0;
                valueCol = 1;
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String id = getCellValueAsString(row.getCell(idCol));
                Double value = getCellValueAsDouble(row.getCell(valueCol));
                if (id != null && !id.isEmpty()) {
                    baseData.put(id, value);
                }
            }
        }
        return baseData;
    }

    public List<Map<String, Object>> parseIncomingFile(File file) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            int idCol = -1;
            int valueCol = -1;

            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String header = getCellValueAsString(cell).toLowerCase().trim();
                    if (header.contains("identific") || header.contains("cedula") || header.equals("id") || header.contains("documento") || header.contains("nit")) {
                        idCol = cell.getColumnIndex();
                    } else if (header.contains("vr") || header.contains("cuota") || header.contains("4x1000") || header.contains("valor")) {
                        valueCol = cell.getColumnIndex();
                    }
                }
            }

            // Fallback indices if headers not found
            if (idCol == -1) idCol = 0;
            if (valueCol == -1) valueCol = 1;

            log.info("Parsing incoming file. ID col: {}, Value col: {}", idCol, valueCol);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Map<String, Object> record = new HashMap<>();
                record.put("id", getCellValueAsString(row.getCell(idCol)));
                record.put("value", getCellValueAsDouble(row.getCell(valueCol)));
                
                if (record.get("id") != null && !((String)record.get("id")).isEmpty()) {
                    records.add(record);
                }
            }
        }
        return records;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                // Avoid scientific notation for IDs
                yield String.format("%.0f", cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String val = cell.getStringCellValue().trim();
            if (val.isEmpty()) return 0.0;
            try {
                // Clean currency symbols and spaces
                val = val.replaceAll("[^\\d,.]", "");
                
                int lastComma = val.lastIndexOf(',');
                int lastDot = val.lastIndexOf('.');
                
                if (lastComma > lastDot) {
                    // Format like 1.234,56 -> convert to 1234.56
                    val = val.replace(".", "").replace(",", ".");
                } else if (lastDot > lastComma) {
                    // Format like 1,234.56 -> convert to 1234.56
                    val = val.replace(",", "");
                }
                // If no separators or only one type, parse as is (Double.parseDouble handles single dot)
                
                return Double.parseDouble(val);
            } catch (Exception e) {
                log.warn("Failed to parse numeric value from string: '{}'", cell.getStringCellValue());
                return 0.0;
            }
        }
        return 0.0;
    }
}

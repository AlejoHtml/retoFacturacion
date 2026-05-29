package com.example.emailprocessor.service;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExcelParsingService {

    private static final Logger log = LoggerFactory.getLogger(ExcelParsingService.class);

    public Map<String, String> extractData(File excelFile) throws IOException {
        Map<String, String> data = new HashMap<>();
        StringBuilder textBuilder = new StringBuilder();

        try (Workbook workbook = WorkbookFactory.create(excelFile)) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowNum = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowNum);

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

    public List<Map<String, Object>> parseIncomingFile(File file) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            log.info("Parsing incoming file: {}. Rows: {}", file.getName(), sheet.getLastRowNum());
            
            int headerRowNum = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowNum);
            
            int idCol = findColumn(headerRow, "cedula", "empleado", "documento", "nit", "id");
            int valueCol = findColumn(headerRow, "vr cuota mas 4x1000", "vr cuota mas 4x1000", "cuota", "valor");
            int deductionCol = findColumn(headerRow, "valor deduccion", "valor deduccion", "vr deduccion", "deduccion");

            if (idCol == -1) { idCol = 0; }
            if (valueCol == -1) { valueCol = 1; }

            for (int i = headerRowNum + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                String id = getCleanId(row.getCell(idCol));
                Double value = getCellValueAsDouble(row.getCell(valueCol));
                Double deduction = deductionCol != -1 ? getCellValueAsDouble(row.getCell(deductionCol)) : null;
                
                if (id != null && !id.isEmpty()) {
                    Map<String, Object> record = new HashMap<>();
                    record.put("id", id);
                    record.put("value", value);
                    record.put("deduction", deduction);
                    records.add(record);
                }
            }
        }
        return records;
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase().trim().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n");
    }

    private int findHeaderRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 50); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            boolean hasId = false;
            boolean hasValue = false;
            for (Cell cell : row) {
                String val = normalize(getCellValueAsString(cell));
                if (val.contains("cedula") || val.contains("identific") || val.contains("documento") || val.contains("empleado") || val.contains("nit") || val.contains("id")) { hasId = true; }
                if (val.contains("cuota") || val.contains("valor") || val.contains("deduccion") || val.contains("monto") || val.contains("total")) { hasValue = true; }
            }
            if (hasId && hasValue) { return i; }
        }
        return 0;
    }

    private int findColumn(Row row, String... keywords) {
        if (row == null) return -1;
        for (Cell cell : row) {
            String header = normalize(getCellValueAsString(cell));
            for (String keyword : keywords) {
                if (header.equals(normalize(keyword))) { return cell.getColumnIndex(); }
            }
        }
        for (Cell cell : row) {
            String header = normalize(getCellValueAsString(cell));
            for (String keyword : keywords) {
                if (header.contains(normalize(keyword))) { return cell.getColumnIndex(); }
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
            value = String.valueOf((long) cell.getNumericCellValue());
        } else {
            value = getCellValueAsString(cell);
        }
        if (value == null || value.trim().isEmpty()) return "";
        String clean = value.trim().replaceAll("[^0-9]", "");
        return clean.isEmpty() ? value.trim() : clean;
    }

    public Double parseStringToDouble(String val) {
        if (val == null || val.trim().isEmpty()) return 0.0;
        try {
            String cleanVal = val.trim().replaceAll("[^\\d,.-]", "");
            if (cleanVal.isEmpty() || cleanVal.equals("-")) return 0.0;
            int lastComma = cleanVal.lastIndexOf(',');
            int lastDot = cleanVal.lastIndexOf('.');
            if (lastComma != -1 && lastDot != -1) {
                if (lastComma > lastDot) { cleanVal = cleanVal.replace(".", "").replace(",", "."); }
                else { cleanVal = cleanVal.replace(",", ""); }
            } else if (lastComma != -1) {
                if (cleanVal.indexOf(',') != lastComma) { cleanVal = cleanVal.replace(",", ""); }
                else { cleanVal = cleanVal.replace(",", "."); }
            } else if (lastDot != -1) {
                if (cleanVal.indexOf('.') != lastDot) { cleanVal = cleanVal.replace(".", ""); }
            }
            return Double.parseDouble(cleanVal);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null) return 0.0;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) { type = cell.getCachedFormulaResultType(); }
        if (type == CellType.NUMERIC) { return cell.getNumericCellValue(); }
        if (type == CellType.STRING) { return parseStringToDouble(cell.getStringCellValue()); }
        return 0.0;
    }
}

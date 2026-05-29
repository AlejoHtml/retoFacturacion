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

    private final DataFormatter dataFormatter = new DataFormatter();

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
                        textBuilder.append(getCellValueAsString(cell)).append(" ");
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

    public Map<String, Double> parseBaseFile(String path) throws IOException {
        log.info("CRITICAL DEBUG: Starting parseBaseFile with path: [{}]", path);
        Map<String, Double> baseData = new HashMap<>();
        File file = new File(path);
        
        if (!file.exists()) {
            log.error("CRITICAL DEBUG: File DOES NOT EXIST at path: {}", file.getAbsolutePath());
            // Intento alternativo con ruta relativa
            file = new File("EmailProcessor/archivos/VALIDACIÓN DE FACTURAS - FEBRERO.xlsx");
            if (!file.exists()) {
                log.error("CRITICAL DEBUG: File also NOT FOUND at relative path: {}", file.getAbsolutePath());
                return baseData;
            }
            log.info("CRITICAL DEBUG: Found file at relative path: {}", file.getAbsolutePath());
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            
            // Intentar en todas las hojas por si los datos no están en la primera
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                log.info("CRITICAL DEBUG: Checking Sheet {}: [{}], Total rows: {}", s, sheet.getSheetName(), sheet.getLastRowNum());
                
                if (sheet.getLastRowNum() < 1) continue;

                int headerRowNum = findHeaderRow(sheet);
                Row headerRow = sheet.getRow(headerRowNum);
                if (headerRow == null) continue;

                // Buscar columnas por nombre
                int idCol = findColumn(headerRow, "empleado", "cedula", "identific", "documento", "nit");
                int valueCol = findColumn(headerRow, "valor deduccion", "valor deducción", "deduccion", "deducción", "cuota");
                
                if (idCol == -1) idCol = 0;
                // Si no encuentra la columna de valor por nombre, usa la última como pidió el usuario
                if (valueCol == -1) valueCol = headerRow.getLastCellNum() - 1;

                log.info("CRITICAL DEBUG: Sheet {} - Using ID Col: {} and Value Col: {}", s, idCol, valueCol);

                for (int i = headerRowNum + 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    String cleanId = getCleanId(row.getCell(idCol));
                    
                    // Intentar obtener el valor de la columna identificada o de la última celda de la fila
                    Double value = 0.0;
                    Cell valueCell = row.getCell(valueCol);
                    if (valueCell == null || valueCell.getCellType() == CellType.BLANK) {
                        // Fallback a la última celda real de la fila
                        valueCell = row.getCell(row.getLastCellNum() - 1);
                    }
                    value = getCellValueAsDouble(valueCell);
                    
                if (!cleanId.isEmpty()) {
                    baseData.put(cleanId, value);
                }
                }
                
                if (!baseData.isEmpty()) {
                    log.info("CRITICAL DEBUG: Successfully extracted {} records from sheet {}", baseData.size(), s);
                    break; // Si encontramos datos en esta hoja, paramos
                }
            }
            log.info("CRITICAL DEBUG: Final total records extracted: {}", baseData.size());
        } catch (Exception e) {
            log.error("CRITICAL DEBUG: Error during Excel processing", e);
        }
        return baseData;
    }

    public List<Map<String, Object>> parseIncomingFile(File file) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowNum = findHeaderRow(sheet);
            Row headerRow = sheet.getRow(headerRowNum);
            if (headerRow == null) return records;
            
            int idCol = findColumn(headerRow, "cedula", "empleado", "documento", "nit", "id");
            int valueCol = findColumn(headerRow, "vr cuota mas 4x1000", "vr cuota más 4x1000", "valor deduccion", "cuota", "valor");

            if (idCol == -1) idCol = 0;
            if (valueCol == -1) valueCol = 1;

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
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = normalize(getCellValueAsString(cell));
                if (val.contains("cedula") || val.contains("empleado") || val.contains("identific") || val.contains("documento") || val.contains("nit")) {
                    return i;
                }
            }
        }
        return 0;
    }

    private int findColumn(Row row, String... keywords) {
        if (row == null) return -1;
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
        try {
            return dataFormatter.formatCellValue(cell).trim();
        } catch (Exception e) {
            return "";
        }
    }

    public String getCleanId(Cell cell) {
        if (cell == null) return "";
        try {
            String value;
            if (cell.getCellType() == CellType.NUMERIC || (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC)) {
                // Usar DataFormatter para obtener la representación visual y luego limpiar
                value = dataFormatter.formatCellValue(cell);
            } else {
                value = getCellValueAsString(cell);
            }
            
            // Limpieza agresiva: solo números. 
            // Si el resultado es notación científica (contiene E), usamos BigDecimal
            if (value.toUpperCase().contains("E") && value.matches(".*\\d.*")) {
                try {
                    value = new java.math.BigDecimal(value).toPlainString();
                } catch (Exception e) {
                    // Ignorar error de parseo
                }
            }
            
            String clean = value.trim().replaceAll("[^0-9]", "");
            // Si después de limpiar queda vacío (ej. era un nombre), devolvemos el original trim
            return clean.isEmpty() ? value.trim() : clean;
        } catch (Exception e) {
            return "";
        }
    }

    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null) return 0.0;
        try {
            if (cell.getCellType() == CellType.NUMERIC || (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC)) {
                return cell.getNumericCellValue();
            }
            String val = getCellValueAsString(cell);
            return parseStringToDouble(val);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public Double parseStringToDouble(String val) {
        if (val == null || val.isEmpty()) return 0.0;
        try {
            String clean = val.replaceAll("[^\\d,.-]", "");
            if (clean.contains(",") && clean.contains(".")) {
                if (clean.lastIndexOf(",") > clean.lastIndexOf(".")) {
                    clean = clean.replace(".", "").replace(",", ".");
                } else {
                    clean = clean.replace(",", "");
                }
            } else if (clean.contains(",")) {
                clean = clean.replace(",", ".");
            }
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
package com.example.emailprocessor.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
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
}
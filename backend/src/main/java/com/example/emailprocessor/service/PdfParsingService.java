package com.example.emailprocessor.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfParsingService {

    public Map<String, String> extractData(File pdfFile) throws IOException {
        Map<String, String> data = new HashMap<>();
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // Basic regex patterns for extraction (more flexible with word boundaries)
            data.put("nro factura", findValue(text, "\\b(?:nro factura|factura nro|nro de factura|invoice number|invoice|factura|nro|no)\\b\\s*[:#]?\\s*([a-zA-Z0-9-]+)"));
            data.put("fecha", findValue(text, "\\b(?:fecha factura|fecha hora factura|fecha de emision|fecha|date|invoice date)\\b\\s*[:]?\\s*([\\d]{1,2}[/-][\\d]{1,2}[/-][\\d]{2,4}|[\\d]{4}[/-][\\d]{1,2}[/-][\\d]{1,2})"));
            
            // Store the full text for AI processing
            data.put("Descripción", text);
        }
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
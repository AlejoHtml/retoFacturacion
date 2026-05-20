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

            // Basic regex patterns for extraction
            data.put("nro factura", findValue(text, "nro factura\\s*:\\s*(\\d+)"));
            data.put("fecha", findValue(text, "fecha\\s*:\\s*([\\d/-]+)"));
            data.put("total", findValue(text, "total\\s*:\\s*([\\d.,]+)"));
            
            // Store the full text for AI processing
            data.put("rawText", text);
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
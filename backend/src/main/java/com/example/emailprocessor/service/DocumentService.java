package com.example.emailprocessor.service;

import com.example.emailprocessor.model.ProcessedDocument;
import com.example.emailprocessor.repository.ProcessedDocumentRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final ProcessedDocumentRepository repository;
    private final PdfParsingService pdfParsingService;
    private final AiExtractionService aiExtractionService;
    private final JavaMailSender mailSender;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public void processEmailAttachment(File tempFile, String sender) throws IOException {
        // 1. Parse PDF
        Map<String, String> extractedData = pdfParsingService.extractData(tempFile);
        
        // 1.1 Try to enhance with AI if possible
        String rawText = extractedData.get("rawText");
        if (rawText != null && !rawText.isEmpty()) {
            try {
                Map<String, Object> aiData = aiExtractionService.extractData(rawText);
                // Merge AI data into extractedData, converting values to String
                aiData.forEach((k, v) -> extractedData.putIfAbsent(k, String.valueOf(v)));
            } catch (Exception e) {
                // Fallback to basic extraction if AI fails
            }
        }

        String invoiceNumber = extractedData.getOrDefault("nro factura", "UNKNOWN_" + System.currentTimeMillis());
        String date = extractedData.getOrDefault("fecha", LocalDateTime.now().toString());

        // 2. Save file to permanent location
        Path targetPath = Paths.get(uploadDir, invoiceNumber + ".pdf");
        Files.createDirectories(targetPath.getParent());
        Files.move(tempFile.toPath(), targetPath);

        // 3. Save to MongoDB
        ProcessedDocument doc = new ProcessedDocument();
        doc.setInvoiceNumber(invoiceNumber);
        doc.setDate(date);
        doc.setSender(sender);
        doc.setFilePath(targetPath.toString());
        doc.setExtractedData(extractedData);
        doc.setProcessedAt(LocalDateTime.now());

        repository.save(doc);
    }

    public List<ProcessedDocument> getAllDocuments() {
        return repository.findAll();
    }

    public List<ProcessedDocument> searchDocuments(String criteria) {
        if (criteria == null || criteria.trim().isEmpty()) {
            return getAllDocuments();
        }
        return repository.searchDocuments(criteria);
    }

    public List<ProcessedDocument> searchDocuments(String invoiceNumber, String sender, String date) {
        return repository.findByFilters(
                invoiceNumber != null ? invoiceNumber : "",
                sender != null ? sender : "",
                date != null ? date : ""
        );
    }

    public void deleteDocument(String id) throws IOException {
        ProcessedDocument doc = repository.findById(id).orElseThrow();
        File file = new File(doc.getFilePath());
        if (file.exists()) {
            file.delete();
        }
        repository.deleteById(id);
    }

    public void resendDocument(String id, String targetEmail) {
        ProcessedDocument doc = repository.findById(id).orElseThrow();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setTo(targetEmail);
            helper.setSubject("Reenvío de Factura: " + doc.getInvoiceNumber());
            helper.setText("Se adjunta la información de la factura " + doc.getInvoiceNumber() + 
                            "\nDatos extraídos: " + doc.getExtractedData().toString());
            
            File file = new File(doc.getFilePath());
            if (file.exists()) {
                helper.addAttachment(file.getName(), file);
            }
            
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Error al reenviar el documento", e);
        }
    }
}
package com.example.emailprocessor.service;

import com.example.emailprocessor.model.ProcessedDocument;
import com.example.emailprocessor.repository.ProcessedDocumentRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final ProcessedDocumentRepository repository;
    private final PdfParsingService pdfParsingService;
    private final ExcelParsingService excelParsingService;
    private final AiExtractionService aiExtractionService;
    private final JavaMailSender mailSender;
    private final MongoTemplate mongoTemplate;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public void processEmailAttachment(File tempFile, String sender) throws IOException {
        String fileName = tempFile.getName().toLowerCase();
        Map<String, String> extractedData;

        // 1. Parse File based on extension
        if (fileName.endsWith(".pdf")) {
            extractedData = pdfParsingService.extractData(tempFile);
        } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            extractedData = excelParsingService.extractData(tempFile);
        } else {
            log.warn("Unsupported file type: {}", fileName);
            return;
        }
        
        // 1.1 Try to enhance with AI if possible
        String rawText = extractedData.get("rawText");
        Map<String, Object> aiData = new HashMap<>();
        if (rawText != null && !rawText.isEmpty()) {
            try {
                aiData = aiExtractionService.extractData(rawText);
                // Merge AI data into extractedData for the legacy repository
                aiData.forEach((k, v) -> extractedData.putIfAbsent(k, String.valueOf(v)));
            } catch (Exception e) {
                // Fallback to basic extraction if AI fails
            }
        }

        String invoiceNumber = extractedData.get("nro factura");
        if (invoiceNumber == null || "Not found".equals(invoiceNumber) || 
            "do".equalsIgnoreCase(invoiceNumber) || "electr".equalsIgnoreCase(invoiceNumber)) {
            
            invoiceNumber = (String) aiData.getOrDefault("invoice_number", 
                            aiData.getOrDefault("nro_factura", 
                            aiData.getOrDefault("factura", null)));
            
            if (invoiceNumber == null || "Not found".equals(invoiceNumber) || 
                "do".equalsIgnoreCase(invoiceNumber) || "electr".equalsIgnoreCase(invoiceNumber)) {
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy/HH:mm:ss");
                invoiceNumber = "factura" + LocalDateTime.now().format(formatter);
            }
            extractedData.put("nro factura", String.valueOf(invoiceNumber));
        }
        // Clean invoice number for filename
        invoiceNumber = invoiceNumber.replaceAll("[^a-zA-Z0-9-_]", "_");

        String date = extractedData.get("fecha");
        if (date == null || "Not found".equals(date)) {
            date = (String) aiData.getOrDefault("date", LocalDateTime.now().toString());
            extractedData.put("fecha", date);
        }

        String total = extractedData.get("total");
        if (total == null || "Not found".equals(total)) {
            total = String.valueOf(aiData.getOrDefault("total", "0"));
            extractedData.put("total", total);
        }

        // 2. Save file to permanent location
        String extension = fileName.endsWith(".pdf") ? ".pdf" : (fileName.endsWith(".xlsx") ? ".xlsx" : ".xls");
        Path targetPath = Paths.get(uploadDir, invoiceNumber + "_" + System.currentTimeMillis() + extension);
        Files.createDirectories(targetPath.getParent());
        Files.move(tempFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("File saved to: {}", targetPath);

        // 3. Save to MongoDB (Legacy fixed collection)
        ProcessedDocument doc = new ProcessedDocument();
        doc.setInvoiceNumber(invoiceNumber);
        doc.setDate(date);
        doc.setSender(sender);
        doc.setFilePath(targetPath.toString());
        doc.setExtractedData(extractedData);
        doc.setProcessedAt(LocalDateTime.now());
        repository.save(doc);
        log.info("Saved document to legacy collection 'processed_documents'");

        // 4. Save to MongoDB (Dynamic collection based on AI key-value pairs)
        // We use the "document_type" or "category" from AI to create/use a collection
        String collectionName = (String) aiData.getOrDefault("document_type", "generic_documents");
        // Clean collection name (no spaces, lowercase)
        collectionName = collectionName.toLowerCase().replaceAll("\\s+", "_");
        
        Document dynamicDoc = new Document();
        dynamicDoc.append("sender", sender);
        dynamicDoc.append("processedAt", LocalDateTime.now());
        dynamicDoc.append("filePath", targetPath.toString());
        
        // Add all AI extracted key-value pairs at the root level
        for (Map.Entry<String, Object> entry : aiData.entrySet()) {
            dynamicDoc.append(entry.getKey().replaceAll("\\.", "_"), entry.getValue());
        }
        
        mongoTemplate.save(dynamicDoc, collectionName);
        log.info("Saved dynamic document to collection '{}'", collectionName);
    }

    public List<ProcessedDocument> getAllDocuments() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "processedAt"));
    }

    public List<ProcessedDocument> searchDocuments(String criteria, String startDate, String endDate) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.with(Sort.by(Sort.Direction.DESC, "processedAt"));
        boolean hasCriteria = false;

        if (criteria != null && !criteria.trim().isEmpty()) {
            query.addCriteria(new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                org.springframework.data.mongodb.core.query.Criteria.where("invoiceNumber").regex(criteria, "i"),
                org.springframework.data.mongodb.core.query.Criteria.where("sender").regex(criteria, "i")
            ));
            hasCriteria = true;
        }

        if ((startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty())) {
            org.springframework.data.mongodb.core.query.Criteria dateCriteria = org.springframework.data.mongodb.core.query.Criteria.where("processedAt");
            
            if (startDate != null && !startDate.isEmpty()) {
                dateCriteria.gte(LocalDateTime.parse(startDate + "T00:00:00"));
            }
            if (endDate != null && !endDate.isEmpty()) {
                dateCriteria.lte(LocalDateTime.parse(endDate + "T23:59:59"));
            }
            query.addCriteria(dateCriteria);
            hasCriteria = true;
        }

        if (!hasCriteria) {
            return getAllDocuments();
        }

        log.info("Executing dynamic query: {}", query);
        return mongoTemplate.find(query, ProcessedDocument.class);
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
        log.info("Resending document ID: {} to: {}. Invoice Number: {}", id, targetEmail, doc.getInvoiceNumber());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            
            helper.setTo(targetEmail);
            helper.setSubject("Reenvío de Factura: " + doc.getInvoiceNumber());
            helper.setText("Se adjunta la información de la factura " + doc.getInvoiceNumber() + 
                            "\nDatos extraídos: " + doc.getExtractedData().toString());
            
            File file = new File(doc.getFilePath());
            if (file.exists()) {
                String originalName = file.getName();
                String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : "";
                
                // Ensure invoice number is valid for a filename
                String invoiceNum = doc.getInvoiceNumber();
                if (invoiceNum == null || invoiceNum.trim().isEmpty() || invoiceNum.equals("Not found")) {
                    invoiceNum = "Factura_" + id;
                }
                String cleanInvoiceNum = invoiceNum.replaceAll("[^a-zA-Z0-9-_]", "_");
                String newFileName = cleanInvoiceNum + extension;
                
                log.info("Attaching file as: {}", newFileName);
                helper.addAttachment(newFileName, file);
            } else {
                log.error("File not found at path: {}", doc.getFilePath());
            }
            
            mailSender.send(message);
            log.info("Email sent successfully to {}", targetEmail);
        } catch (Exception e) {
            log.error("Error resending document", e);
            throw new RuntimeException("Error al reenviar el documento: " + e.getMessage(), e);
        }
    }
}
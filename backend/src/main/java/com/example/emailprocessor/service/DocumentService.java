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

    @Value("${file.base-path:C:/Users/alejandro.ospina/Downloads/documentoBase.xlsx}")
    private String basePath;

    public void processEmailAttachment(File tempFile, String sender) throws IOException {
        String fileName = tempFile.getName().toLowerCase();
        
        if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            processExcelBusinessLogic(tempFile, sender);
        } else if (fileName.endsWith(".pdf")) {
            processPdfLegacyLogic(tempFile, sender);
        } else {
            log.warn("Unsupported file type: {}", fileName);
        }
    }

    private void processExcelBusinessLogic(File tempFile, String sender) throws IOException {
        log.info("Processing Excel with new business logic from sender: {}", sender);
        
        // 1. Load base data
        Map<String, Double> baseData = excelParsingService.parseBaseFile(basePath);
        
        // 2. Parse incoming records
        List<Map<String, Object>> incomingRecords = excelParsingService.parseIncomingFile(tempFile);
        
        // 3. Save file to permanent location
        String extension = tempFile.getName().substring(tempFile.getName().lastIndexOf("."));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path targetPath = Paths.get(uploadDir, "Libranza_" + timestamp + extension);
        Files.createDirectories(targetPath.getParent());
        Files.copy(tempFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 4. Process each record, compare and save
        for (Map<String, Object> record : incomingRecords) {
            String id = (String) record.get("id");
            Double incomingValue = (Double) record.get("value");
            Double baseValue = baseData.getOrDefault(id, 0.0);
            Double difference = incomingValue - baseValue;

            ProcessedDocument doc = new ProcessedDocument();
            doc.setIdentification(id);
            doc.setInstallmentValue(incomingValue);
            doc.setDifference(difference);
            doc.setSender(sender);
            doc.setStatus("En Gestión");
            doc.setProcessedAt(LocalDateTime.now());
            doc.setFilePath(targetPath.toString());
            doc.setDate(LocalDateTime.now().toString());
            doc.setInvoiceNumber("LIBRANZA_" + id + "_" + timestamp);
            
            repository.save(doc);
        }
        
        log.info("Processed {} employee records from Excel", incomingRecords.size());
        // Delete temp file
        tempFile.delete();
    }

    private void processPdfLegacyLogic(File tempFile, String sender) throws IOException {
        String fileName = tempFile.getName().toLowerCase();
        Map<String, String> extractedData = pdfParsingService.extractData(tempFile);
        
        // Try to enhance with AI if possible
        String rawText = extractedData.get("Descripción");
        Map<String, Object> aiData = new HashMap<>();
        if (rawText != null && !rawText.isEmpty()) {
            try {
                aiData = aiExtractionService.extractData(rawText);
                aiData.forEach((k, v) -> extractedData.putIfAbsent(k, String.valueOf(v)));
            } catch (Exception e) {
                log.error("AI extraction failed", e);
            }
        }

        String invoiceNumber = extractedData.get("nro factura");
        if (invoiceNumber == null || "Not found".equals(invoiceNumber)) {
            invoiceNumber = "factura_" + System.currentTimeMillis();
        }
        invoiceNumber = invoiceNumber.replaceAll("[^a-zA-Z0-9-_]", "_");

        Path targetPath = Paths.get(uploadDir, invoiceNumber + "_" + System.currentTimeMillis() + ".pdf");
        Files.createDirectories(targetPath.getParent());
        Files.move(tempFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        ProcessedDocument doc = new ProcessedDocument();
        doc.setInvoiceNumber(invoiceNumber);
        doc.setSender(sender);
        doc.setFilePath(targetPath.toString());
        doc.setExtractedData(extractedData);
        doc.setStatus("En Gestión");
        doc.setProcessedAt(LocalDateTime.now());
        repository.save(doc);
    }

    public List<ProcessedDocument> getAllDocuments() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "processedAt"));
    }

    public void updateStatus(String id, String status) {
        ProcessedDocument doc = repository.findById(id).orElseThrow();
        doc.setStatus(status);
        repository.save(doc);
        log.info("Updated document ID: {} status to: {}", id, status);
    }

    public List<String> getUniqueSenders() {
        return mongoTemplate.query(ProcessedDocument.class)
                .distinct("sender")
                .as(String.class)
                .all();
    }

    public List<ProcessedDocument> searchDocuments(String criteria, String startDate, String endDate, String status) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.with(Sort.by(Sort.Direction.DESC, "processedAt"));

        if (criteria != null && !criteria.trim().isEmpty()) {
            query.addCriteria(new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                org.springframework.data.mongodb.core.query.Criteria.where("identification").regex(criteria, "i"),
                org.springframework.data.mongodb.core.query.Criteria.where("sender").regex(criteria, "i")
            ));
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
        }

        if (status != null && !status.isEmpty()) {
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("status").is(status));
        } else {
            // Default to "En Gestión" if no status specified
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("status").is("En Gestión"));
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
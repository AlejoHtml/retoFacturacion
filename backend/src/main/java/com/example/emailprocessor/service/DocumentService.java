package com.example.emailprocessor.service;

import com.example.emailprocessor.model.ProcessedDocument;
import com.example.emailprocessor.repository.ProcessedDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final ProcessedDocumentRepository repository;
    private final PdfParsingService pdfParsingService;
    private final ExcelParsingService excelParsingService;
    private final AiExtractionService aiExtractionService;
    private final MongoTemplate mongoTemplate;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public DocumentService(ProcessedDocumentRepository repository, PdfParsingService pdfParsingService, 
                           ExcelParsingService excelParsingService, AiExtractionService aiExtractionService, 
                           MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.pdfParsingService = pdfParsingService;
        this.excelParsingService = excelParsingService;
        this.aiExtractionService = aiExtractionService;
        this.mongoTemplate = mongoTemplate;
    }

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
        
        List<Map<String, Object>> incomingRecords = excelParsingService.parseIncomingFile(tempFile);
        
        String extension = tempFile.getName().substring(tempFile.getName().lastIndexOf("."));
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path targetPath = Paths.get(uploadDir, "Libranza_" + timestamp + extension);
        Files.createDirectories(targetPath.getParent());
        Files.copy(tempFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        for (Map<String, Object> record : incomingRecords) {
            String id = (String) record.get("id");
            Double incomingValue = (Double) record.get("value");
            Double deductionValue = record.get("deduction") != null ? (Double) record.get("deduction") : 0.0;
            Double difference = incomingValue - deductionValue;

            ProcessedDocument doc = new ProcessedDocument();
            doc.setIdentification(id);
            doc.setInstallmentValue(incomingValue);
            doc.setDeduction(deductionValue);
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
        tempFile.delete();
    }

    private void processPdfLegacyLogic(File tempFile, String sender) throws IOException {
        String fileName = tempFile.getName().toLowerCase();
        Map<String, String> extractedData = pdfParsingService.extractData(tempFile);
        
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
        
        String extractedDate = extractedData.get("fecha");
        if (extractedDate == null || "Not found".equals(extractedDate)) {
            doc.setDate(LocalDateTime.now().toString());
        } else {
            doc.setDate(extractedDate);
        }

        String totalStr = extractedData.get("total");
        if (totalStr != null && !"Not found".equals(totalStr)) {
            doc.setInstallmentValue(excelParsingService.parseStringToDouble(totalStr));
        }

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
                org.springframework.data.mongodb.core.query.Criteria.where("sender").regex(criteria, "i"),
                org.springframework.data.mongodb.core.query.Criteria.where("invoiceNumber").regex(criteria, "i")
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
            query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("status").is("En Gestión"));
        }

        log.info("Executing dynamic query: {}", query);
        List<ProcessedDocument> documents = mongoTemplate.find(query, ProcessedDocument.class);
        
        for (ProcessedDocument doc : documents) {
            if (doc.getIdentification() != null && !doc.getIdentification().isEmpty()) {
                Double currentDeduction = doc.getDeduction() != null ? doc.getDeduction() : 0.0;
                Double currentValue = doc.getInstallmentValue() != null ? doc.getInstallmentValue() : 0.0;
                doc.setDifference(currentValue - currentDeduction);
            }
        }
        
        return documents;
    }

    public void deleteDocument(String id) throws IOException {
        ProcessedDocument doc = repository.findById(id).orElseThrow();
        File file = new File(doc.getFilePath());
        if (file.exists()) {
            file.delete();
        }
        repository.deleteById(id);
    }
}

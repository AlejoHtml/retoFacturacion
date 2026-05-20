package com.example.emailprocessor.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Document(collection = "processed_documents")
public class ProcessedDocument {
    @Id
    private String id;
    private String invoiceNumber;
    private String date;
    private String sender;
    private String filePath;
    private Map<String, String> extractedData;
    private LocalDateTime processedAt;
}
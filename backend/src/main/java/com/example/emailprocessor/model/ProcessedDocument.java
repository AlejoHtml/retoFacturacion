package com.example.emailprocessor.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

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
    private String identification;
    private Double installmentValue;
    private Double deduction;
    private Double difference;
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Map<String, String> getExtractedData() { return extractedData; }
    public void setExtractedData(Map<String, String> extractedData) { this.extractedData = extractedData; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public String getIdentification() { return identification; }
    public void setIdentification(String identification) { this.identification = identification; }
    public Double getInstallmentValue() { return installmentValue; }
    public void setInstallmentValue(Double installmentValue) { this.installmentValue = installmentValue; }
    public Double getDeduction() { return deduction; }
    public void setDeduction(Double deduction) { this.deduction = deduction; }
    public Double getDifference() { return difference; }
    public void setDifference(Double difference) { this.difference = difference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

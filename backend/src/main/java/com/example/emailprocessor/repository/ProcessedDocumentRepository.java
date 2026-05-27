package com.example.emailprocessor.repository;

import com.example.emailprocessor.model.ProcessedDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProcessedDocumentRepository extends MongoRepository<ProcessedDocument, String> {
    List<ProcessedDocument> findByInvoiceNumberContainingIgnoreCase(String invoiceNumber);
    List<ProcessedDocument> findBySenderContainingIgnoreCase(String sender);
    List<ProcessedDocument> findByDate(String date);

    @Query("{ '$and': [ " +
            "{ 'invoiceNumber': { '$regex': ?0, '$options': 'i' } }, " +
            "{ 'sender': { '$regex': ?1, '$options': 'i' } }, " +
            "{ 'date': { '$regex': ?2, '$options': 'i' } } " +
            "] }")
    List<ProcessedDocument> findByFilters(String invoiceNumber, String sender, String date);

}

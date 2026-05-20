package com.example.emailprocessor.controller;

import com.example.emailprocessor.model.ProcessedDocument;
import com.example.emailprocessor.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public List<ProcessedDocument> getDocuments(
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String search) {
        
        if (search != null && !search.isEmpty()) {
            return documentService.searchDocuments(search);
        }
        
        if (invoiceNumber == null && sender == null && date == null) {
            return documentService.getAllDocuments();
        }
        return documentService.searchDocuments(invoiceNumber, sender, date);
    }

    @DeleteMapping("/{id}")
    public void deleteDocument(@PathVariable String id) throws IOException {
        documentService.deleteDocument(id);
    }

    @PostMapping("/{id}/resend")
    public void resendDocument(@PathVariable String id, @RequestBody String targetEmail) {
        documentService.resendDocument(id, targetEmail);
    }
}
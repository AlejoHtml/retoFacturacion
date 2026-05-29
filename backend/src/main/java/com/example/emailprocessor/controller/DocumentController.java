package com.example.emailprocessor.controller;

import com.example.emailprocessor.model.ProcessedDocument;
import com.example.emailprocessor.service.DocumentService;
import com.example.emailprocessor.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final EmailService emailService;

    @GetMapping
    public List<ProcessedDocument> getDocuments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String status) {
        
        return documentService.searchDocuments(search, startDate, endDate, status);
    }

    @GetMapping("/senders")
    public List<String> getUniqueSenders() {
        return documentService.getUniqueSenders();
    }

    @DeleteMapping("/{id}")
    public void deleteDocument(@PathVariable String id) throws IOException {
        documentService.deleteDocument(id);
    }

    @PutMapping("/{id}/status")
    public void updateStatus(@PathVariable String id, @RequestParam String status) {
        documentService.updateStatus(id, status);
    }

    @PostMapping("/{id}/resend")
    public void resendDocument(@PathVariable String id, @RequestBody String targetEmail) {
        emailService.resendDocument(id, targetEmail);
    }
}
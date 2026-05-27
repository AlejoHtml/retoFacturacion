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
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        return documentService.searchDocuments(search, startDate, endDate);
    }

    @GetMapping("/senders")
    public List<String> getUniqueSenders() {
        return documentService.getUniqueSenders();
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
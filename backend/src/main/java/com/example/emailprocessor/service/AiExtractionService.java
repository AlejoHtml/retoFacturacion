package com.example.emailprocessor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiExtractionService {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model-name}")
    private String modelName;

    @Value("${ai.provider:openai}")
    private String provider;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiExtractionService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> extractData(String text) {
        if ("demo".equals(apiKey) || apiKey == null || apiKey.isEmpty()) {
            return createDemoData(text);
        }

        String url = provider.equalsIgnoreCase("groq") 
            ? "https://api.groq.com/openai/v1/chat/completions"
            : "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a document data extractor. Extract all relevant key-value pairs from the text. " +
                "Specifically look for 'invoice_number', 'date', and 'document_type'. " +
                "IMPORTANT: The 'invoice_number' must be the full alphanumeric code. Do NOT return partial words like 'do' or 'no'. " +
                "Return ONLY a valid JSON object with these keys and any others you find.");
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "Extract data from this text: " + text);
        messages.add(userMessage);

        requestBody.put("messages", messages);
        
        // Groq and OpenAI both support json_object response format
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");
                return objectMapper.readValue(content, Map.class);
            }
        } catch (Exception e) {
            return createDemoData(text); // Fallback to demo data on error
        }

        return createDemoData(text);
    }

    private Map<String, Object> createDemoData(String text) {
        Map<String, Object> demo = new HashMap<>();
        demo.put("info", "AI Extraction in Demo Mode (No API Key)");
        demo.put("document_type", "factura_demo");
        // Intentamos extraer algo básico del texto para que no esté vacío
        if (text.contains("Factura")) demo.put("invoice_number", "DEMO-123");
        return demo;
    }
}
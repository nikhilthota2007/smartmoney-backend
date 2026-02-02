package com.nikhil.finance_advisor.controller;

import com.nikhil.finance_advisor.model.ChatRequest;
import com.nikhil.finance_advisor.model.ChatResponse;
import com.nikhil.finance_advisor.service.GeminiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class FinancialAdvisorController {
    
    @Autowired
    private GeminiService geminiService;
    
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        try {
            String response = geminiService.getChatResponse(request);
            return new ChatResponse(response, true);
        } catch (Exception e) {
            ChatResponse errorResponse = new ChatResponse(null, false);
            errorResponse.setError(e.getMessage());
            return errorResponse;
        }
    }
    
    @GetMapping("/health")
    public String health() {
        return "Financial Advisor API is running!";
    }
}
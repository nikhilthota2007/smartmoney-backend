package com.nikhil.finance_advisor.controller;

import com.nikhil.finance_advisor.model.ChatRequest;
import com.nikhil.finance_advisor.model.ChatResponse;
import com.nikhil.finance_advisor.service.AdvisorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${cors.allowed.origins}")
public class FinancialAdvisorController {

    private static final Logger log = LoggerFactory.getLogger(FinancialAdvisorController.class);

    /** What the client is told when the upstream call fails. Details stay in the logs. */
    static final String GENERIC_ERROR =
            "The advisor is temporarily unavailable. Please try again in a moment.";

    private final AdvisorService advisorService;

    public FinancialAdvisorController(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        try {
            return new ChatResponse(advisorService.getChatResponse(request), true);
        } catch (Exception e) {
            // Exception messages can carry upstream URLs, keys and internal state,
            // so they are logged rather than returned to the browser.
            log.error("Chat request failed", e);
            ChatResponse errorResponse = new ChatResponse(null, false);
            errorResponse.setError(GENERIC_ERROR);
            return errorResponse;
        }
    }

    @GetMapping("/health")
    public String health() {
        return "Financial Advisor API is running!";
    }
}

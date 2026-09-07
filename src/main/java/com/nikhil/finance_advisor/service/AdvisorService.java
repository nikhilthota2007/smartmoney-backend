package com.nikhil.finance_advisor.service;

import com.nikhil.finance_advisor.model.ChatMessage;
import com.nikhil.finance_advisor.model.ChatRequest;
import com.nikhil.finance_advisor.prompt.AdvisorPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to Groq's OpenAI-compatible chat completions API.
 *
 * (This was called GeminiService; it has always called Groq, never Gemini.)
 */
@Service
public class AdvisorService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorService.class);

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final AdvisorPrompt advisorPrompt;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public AdvisorService(
            RestTemplate groqRestTemplate,
            AdvisorPrompt advisorPrompt,
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.api.model:llama-3.3-70b-versatile}") String model,
            @Value("${groq.api.temperature:0.7}") double temperature,
            @Value("${groq.api.max-tokens:1000}") int maxTokens) {
        this.restTemplate = groqRestTemplate;
        this.advisorPrompt = advisorPrompt;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public String getChatResponse(ChatRequest request) {
        List<Map<String, String>> messages = buildMessages(request);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        log.debug("Calling Groq: model={} promptVersion={} messages={} groundedContext={}",
                model, AdvisorPrompt.PROMPT_VERSION, messages.size(),
                request.getFinancialContext() != null);

        @SuppressWarnings("unchecked")
        Map<String, Object> response =
                restTemplate.postForObject(GROQ_API_URL, new HttpEntity<>(requestBody, headers), Map.class);

        return extractContent(response);
    }

    private List<Map<String, String>> buildMessages(ChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system",
                advisorPrompt.build(request.getFinancialData(), request.getFinancialContext())));

        if (request.getHistory() != null) {
            for (ChatMessage msg : request.getHistory()) {
                // The frontend has used both 'model' and 'assistant' over time.
                String role = "model".equals(msg.getRole()) ? "assistant" : msg.getRole();
                messages.add(message(role, msg.getContent()));
            }
        }

        messages.add(message("user", request.getMessage()));
        return messages;
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String extractContent(Map<String, Object> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("Groq response contained no choices");
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)
                || !(message.get("content") instanceof String content)) {
            throw new IllegalStateException("Groq response was not in the expected shape");
        }
        return content;
    }
}

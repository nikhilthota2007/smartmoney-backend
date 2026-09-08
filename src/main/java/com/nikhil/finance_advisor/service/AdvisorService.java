package com.nikhil.finance_advisor.service;

import com.nikhil.finance_advisor.model.ChatMessage;
import com.nikhil.finance_advisor.model.ChatRequest;
import com.nikhil.finance_advisor.model.ToolCall;
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
 *
 * The model may answer, or ask for a calculation. Tool calls are relayed to the
 * client rather than executed here — see AdvisorTools for why.
 */
@Service
public class AdvisorService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorService.class);

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final RestTemplate restTemplate;
    private final AdvisorPrompt advisorPrompt;
    private final AdvisorTools advisorTools;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    public AdvisorService(
            RestTemplate groqRestTemplate,
            AdvisorPrompt advisorPrompt,
            AdvisorTools advisorTools,
            @Value("${groq.api.key}") String apiKey,
            @Value("${groq.api.model:llama-3.3-70b-versatile}") String model,
            @Value("${groq.api.temperature:0.7}") double temperature,
            @Value("${groq.api.max-tokens:1000}") int maxTokens) {
        this.restTemplate = groqRestTemplate;
        this.advisorPrompt = advisorPrompt;
        this.advisorTools = advisorTools;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /** Either the assistant's text, or the calculations it wants run. */
    public record AdvisorReply(String content, List<ToolCall> toolCalls) {
        public boolean needsTools() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    public AdvisorReply getChatResponse(ChatRequest request) {
        List<Map<String, Object>> messages = buildMessages(request);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("tools", advisorTools.declarations());
        requestBody.put("tool_choice", "auto");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        log.debug("Calling Groq: model={} promptVersion={} toolsVersion={} messages={} groundedContext={}",
                model, AdvisorPrompt.PROMPT_VERSION, AdvisorTools.TOOLS_VERSION, messages.size(),
                request.getFinancialContext() != null);

        @SuppressWarnings("unchecked")
        Map<String, Object> response =
                restTemplate.postForObject(GROQ_API_URL, new HttpEntity<>(requestBody, headers), Map.class);

        return extractReply(response);
    }

    private List<Map<String, Object>> buildMessages(ChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(textMessage("system",
                advisorPrompt.build(request.getFinancialData(), request.getFinancialContext())));

        if (request.getHistory() != null) {
            for (ChatMessage message : request.getHistory()) {
                messages.add(fromHistory(message));
            }
        }

        // Absent on a continuation: the question is already in the history, and
        // what follows is the tool results the model is waiting on.
        if (request.getMessage() != null && !request.getMessage().isBlank()) {
            messages.add(textMessage("user", request.getMessage()));
        }
        return messages;
    }

    private Map<String, Object> fromHistory(ChatMessage message) {
        if ("tool".equals(message.getRole())) {
            Map<String, Object> toolResult = new LinkedHashMap<>();
            toolResult.put("role", "tool");
            toolResult.put("tool_call_id", message.getToolCallId());
            toolResult.put("content", message.getContent() != null ? message.getContent() : "");
            return toolResult;
        }

        // The frontend has used both 'model' and 'assistant' over time.
        String role = "model".equals(message.getRole()) ? "assistant" : message.getRole();
        Map<String, Object> turn = textMessage(role, message.getContent() != null ? message.getContent() : "");

        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            turn.put("tool_calls", message.getToolCalls().stream().map(AdvisorService::toWireFormat).toList());
        }
        return turn;
    }

    private static Map<String, Object> toWireFormat(ToolCall call) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", call.name());
        function.put("arguments", call.arguments() != null ? call.arguments() : "{}");

        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("id", call.id());
        wire.put("type", "function");
        wire.put("function", function);
        return wire;
    }

    private static Map<String, Object> textMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static AdvisorReply extractReply(Map<String, Object> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("Groq response contained no choices");
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("Groq response was not in the expected shape");
        }

        String content = message.get("content") instanceof String text ? text : null;
        List<ToolCall> toolCalls = parseToolCalls(message.get("tool_calls"));

        if (content == null && toolCalls.isEmpty()) {
            throw new IllegalStateException("Groq returned neither content nor tool calls");
        }
        return new AdvisorReply(content, toolCalls);
    }

    private static List<ToolCall> parseToolCalls(Object raw) {
        if (!(raw instanceof List<?> rawCalls)) return List.of();

        List<ToolCall> calls = new ArrayList<>();
        for (Object rawCall : rawCalls) {
            if (!(rawCall instanceof Map<?, ?> call)) continue;
            if (!(call.get("function") instanceof Map<?, ?> function)) continue;
            if (!(function.get("name") instanceof String name)) continue;

            String id = call.get("id") instanceof String callId ? callId : name;
            String arguments = function.get("arguments") instanceof String args ? args : "{}";
            calls.add(new ToolCall(id, name, arguments));
        }
        return calls;
    }
}

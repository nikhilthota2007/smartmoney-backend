package com.nikhil.finance_advisor.service;

import com.nikhil.finance_advisor.model.ChatMessage;
import com.nikhil.finance_advisor.model.ChatRequest;
import com.nikhil.finance_advisor.model.FinancialData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {
    
    @Value("${groq.api.key}")
    private String apiKey;
    
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    
    public String getChatResponse(ChatRequest request) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            String systemPrompt = buildSystemPrompt(request.getFinancialData());
            
            // Build messages array for Groq (OpenAI format)
            List<Map<String, String>> messages = new ArrayList<>();
            
            // Add system message
            Map<String, String> systemMessage = new HashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.add(systemMessage);
            
            // Add conversation history
            if (request.getHistory() != null) {
                for (ChatMessage msg : request.getHistory()) {
                    Map<String, String> historyMsg = new HashMap<>();
                    historyMsg.put("role", msg.getRole().equals("model") ? "assistant" : msg.getRole());
                    historyMsg.put("content", msg.getContent());
                    messages.add(historyMsg);
                }
            }
            
            // Add current message
            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            messages.add(userMessage);
            
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "llama-3.3-70b-versatile");
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // Make API call
            Map response = restTemplate.postForObject(GROQ_API_URL, entity, Map.class);
            
            // Extract response text
            if (response != null && response.containsKey("choices")) {
                List<Map> choices = (List<Map>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map choice = choices.get(0);
                    Map message = (Map) choice.get("message");
                    return (String) message.get("content");
                }
            }
            
            throw new RuntimeException("Failed to parse Groq API response");
            
        } catch (Exception e) {
            throw new RuntimeException("Error calling Groq API: " + e.getMessage(), e);
        }
    }
    
    private String buildSystemPrompt(FinancialData data) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert personal financial advisor whose core principle is helping clients build wealth through smart money management and avoiding consumer debt.\n\n");
        
        prompt.append("===== CORE PHILOSOPHY =====\n");
        prompt.append("Your mission is to guide people toward financial freedom by:\n");
        prompt.append("* Avoiding all consumer debt (except primary residence mortgages under strict conditions)\n");
        prompt.append("* Making purchases with earned capital, not borrowed money\n");
        prompt.append("* Building wealth through disciplined saving and investing\n");
        prompt.append("* Making informed financial decisions based on their actual financial capacity\n\n");
        
        prompt.append("===== YOUR ADVISORY APPROACH =====\n");
        prompt.append("PRINCIPLE 1: Pay cash for depreciating assets\n");
        prompt.append("PRINCIPLE 2: Delay consumption rather than borrow\n");
        prompt.append("PRINCIPLE 3: Invest first, buy later\n");
        prompt.append("PRINCIPLE 4: Income growth beats borrowing\n");
        prompt.append("PRINCIPLE 5: Freedom over convenience\n\n");
        
        prompt.append("===== HOW TO HANDLE PURCHASE REQUESTS =====\n\n");
        prompt.append("When someone asks about buying something (car, TV, appliance, etc.):\n\n");
        
        prompt.append("STEP 1 - ASSESS: Check if purchase fits budget, determine if they're considering debt\n\n");
        
        prompt.append("STEP 2 - ASK CLARIFYING QUESTIONS:\n");
        prompt.append("* What specific model or features are you looking for?\n");
        prompt.append("* What will you primarily use this for?\n");
        prompt.append("* What's your timeline?\n");
        prompt.append("* Have you considered alternatives in a lower price range?\n\n");
        
        prompt.append("STEP 3 - IF TOO EXPENSIVE OR REQUIRES DEBT:\n");
        prompt.append("* Firmly but kindly reject the debt option\n");
        prompt.append("* Explain the TRUE cost (principal + interest)\n");
        prompt.append("* Provide SPECIFIC, REALISTIC alternatives that fit their budget\n\n");
        
        prompt.append("STEP 4 - GIVE CONCRETE ALTERNATIVES:\n");
        prompt.append("Example - Car Purchase:\n");
        prompt.append("If they want new Toyota Corolla ($28,000), suggest:\n");
        prompt.append("- Used Toyota Corolla (2018-2020) for $15,000-$18,000\n");
        prompt.append("- Honda Civic (2017-2019) for $14,000-$17,000\n");
        prompt.append("- Mazda3 (2018-2020) for $13,000-$16,000\n");
        prompt.append("BE SPECIFIC with models, years, and approximate prices.\n\n");
        
        prompt.append("STEP 5 - CREATE A SAVINGS PLAN:\n");
        prompt.append("Show them how to save toward the purchase and where to invest while saving.\n\n");
        
        prompt.append("===== TONE & STYLE =====\n");
        prompt.append("* Professional but warm\n");
        prompt.append("* Call yourself a 'financial advisor' - not 'debt-averse advisor'\n");
        prompt.append("* Never shaming or judgmental\n");
        prompt.append("* Patient and encouraging\n");
        prompt.append("* Use simple math and real examples\n");
        prompt.append("* Long-term wealth building focused\n\n");
        
        prompt.append("===== MORTGAGE EXCEPTION =====\n");
        prompt.append("Only discuss mortgages for primary residences when:\n");
        prompt.append("* Down payment 10-20% minimum\n");
        prompt.append("* Payment ≤25% of take-home income\n");
        prompt.append("* Emergency fund in place\n");
        prompt.append("Even then: emphasize paying extra, 15-year term, early payoff.\n\n");
        
        prompt.append("===== USER'S FINANCIAL INFORMATION =====\n");
        prompt.append("Monthly Income: $").append(data.getMonthlyIncome() != null ? data.getMonthlyIncome() : "Not provided").append("\n");
        prompt.append("Monthly Expenses: $").append(data.getMonthlyExpenses() != null ? data.getMonthlyExpenses() : "Not provided").append("\n");
        prompt.append("Current Savings: $").append(data.getSavings() != null ? data.getSavings() : "Not provided").append("\n");
        prompt.append("Outstanding Debts: $").append(data.getDebts() != null ? data.getDebts() : "Not provided").append("\n");
        prompt.append("Financial Goals: ").append(data.getGoals() != null ? data.getGoals() : "Not provided").append("\n\n");
        
        prompt.append("Always consider their specific financial situation. If they can't afford something now, help them create a realistic plan to afford it later without debt. Ask follow-up questions to understand their needs better and provide specific, actionable alternatives.");
        
        return prompt.toString();
    }
}
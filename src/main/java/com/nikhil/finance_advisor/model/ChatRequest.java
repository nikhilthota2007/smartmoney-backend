package com.nikhil.finance_advisor.model;

import java.util.List;

public class ChatRequest {
    private FinancialData financialData;
    private String message;
    private List<ChatMessage> history;

    public ChatRequest() {}

    public FinancialData getFinancialData() { return financialData; }
    public void setFinancialData(FinancialData financialData) { 
        this.financialData = financialData; 
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<ChatMessage> getHistory() { return history; }
    public void setHistory(List<ChatMessage> history) { 
        this.history = history; 
    }
}
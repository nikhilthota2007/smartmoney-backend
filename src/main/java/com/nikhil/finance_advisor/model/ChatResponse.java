package com.nikhil.finance_advisor.model;

import java.util.List;

/**
 * Either an answer, or a request for calculations.
 *
 * When toolCalls is non-empty the model has asked for figures it will not
 * estimate. The client runs them and posts again with the results appended to
 * the history; `response` may then be null.
 */
public class ChatResponse {
    private String response;
    private boolean success;
    private String error;
    private List<ToolCall> toolCalls;

    public ChatResponse() {}

    public ChatResponse(String response, boolean success) {
        this.response = response;
        this.success = success;
    }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }
}
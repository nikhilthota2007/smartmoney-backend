package com.nikhil.finance_advisor.model;

import java.util.List;

/**
 * One turn of the conversation.
 *
 * Beyond plain user and assistant turns, two shapes carry a tool round trip:
 * an assistant turn with toolCalls (the model asking for a calculation), and a
 * turn with role "tool" carrying toolCallId and the result as its content.
 * The backend holds no state, so the client sends these back with every request.
 */
public class ChatMessage {
    private String role;
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
}
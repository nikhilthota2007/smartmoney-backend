package com.nikhil.finance_advisor.model;

/**
 * A calculation the model has asked for.
 *
 * Relayed to the client, which runs it and sends the result back as a message
 * with role "tool" carrying the matching id.
 *
 * @param arguments raw JSON as the model produced it, passed through untouched —
 *                  the client validates it, since the client is what executes.
 */
public record ToolCall(String id, String name, String arguments) {}

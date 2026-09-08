package com.nikhil.finance_advisor.controller;

import com.nikhil.finance_advisor.model.ChatRequest;
import com.nikhil.finance_advisor.model.ChatResponse;
import com.nikhil.finance_advisor.prompt.AdvisorPrompt;
import com.nikhil.finance_advisor.service.AdvisorService;
import com.nikhil.finance_advisor.service.AdvisorTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${cors.allowed.origins}")
public class FinancialAdvisorController {

    private static final Logger log = LoggerFactory.getLogger(FinancialAdvisorController.class);

    /** What the client is told when the upstream call fails. Details stay in the logs. */
    static final String GENERIC_ERROR =
            "The advisor is temporarily unavailable. Please try again in a moment.";

    private final AdvisorService advisorService;
    private final AdvisorTools advisorTools;

    public FinancialAdvisorController(AdvisorService advisorService, AdvisorTools advisorTools) {
        this.advisorService = advisorService;
        this.advisorTools = advisorTools;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        try {
            AdvisorService.AdvisorReply reply = advisorService.getChatResponse(request);
            ChatResponse response = new ChatResponse(reply.content(), true);
            if (reply.needsTools()) {
                // The client runs these and posts again with the results.
                response.setToolCalls(reply.toolCalls());
            }
            return response;
        } catch (RestClientResponseException e) {
            // Groq rejected the call. Its body says why — a decommissioned model,
            // a bad key, a rate limit — and that distinction is invisible from the
            // browser, which sees the same generic error for all of them.
            log.error("Groq rejected the request: status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            return failure();
        } catch (Exception e) {
            // Exception messages can carry upstream URLs, keys and internal state,
            // so they are logged rather than returned to the browser.
            log.error("Chat request failed", e);
            return failure();
        }
    }

    private static ChatResponse failure() {
        ChatResponse errorResponse = new ChatResponse(null, false);
        errorResponse.setError(GENERIC_ERROR);
        return errorResponse;
    }

    /**
     * Liveness, plus what this instance is actually running.
     *
     * The versions are here so a deploy can be verified in one request. Without
     * them an answer that quotes no computed figures is ambiguous: it could be
     * the model declining to call a tool, or an older build that has no tools to
     * call. The tool names make that difference visible.
     *
     * The model name is here for the same reason: Groq retires models, and the
     * only symptom of calling a retired one is the generic chat error.
     */
    @GetMapping("/health")
    public String health() {
        return "Financial Advisor API is running!"
                + " model=" + advisorService.modelName()
                + " prompt=" + AdvisorPrompt.PROMPT_VERSION
                + " tools=" + AdvisorTools.TOOLS_VERSION
                + " " + advisorTools.names();
    }
}

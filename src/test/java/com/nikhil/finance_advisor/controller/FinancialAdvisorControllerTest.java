package com.nikhil.finance_advisor.controller;

import com.nikhil.finance_advisor.model.ToolCall;
import com.nikhil.finance_advisor.service.AdvisorService;
import com.nikhil.finance_advisor.service.AdvisorTools;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialAdvisorController.class)
@org.springframework.test.context.TestPropertySource(properties = "cors.allowed.origins=http://localhost:3000")
class FinancialAdvisorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvisorService advisorService;

    @MockitoBean
    private AdvisorTools advisorTools;

    private static final String REQUEST_BODY = """
            {
              "message": "Should I buy a new car?",
              "financialData": {
                "monthlyIncome": "5000",
                "monthlyExpenses": "3500",
                "savings": "10000",
                "debts": "5000",
                "goals": "Buy a house"
              },
              "history": []
            }
            """;

    @Test
    void returnsTheAdvisorsReply() throws Exception {
        given(advisorService.getChatResponse(any()))
                .willReturn(new AdvisorService.AdvisorReply("Buy used, pay cash.", List.of()));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.response").value("Buy used, pay cash."));
    }

    @Test
    void relaysToolCallsForTheClientToRun() throws Exception {
        given(advisorService.getChatResponse(any())).willReturn(new AdvisorService.AdvisorReply(
                null, List.of(new ToolCall("call_1", "simulate_debt_payoff", "{\"extraPayment\":500}"))));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.toolCalls[0].id").value("call_1"))
                .andExpect(jsonPath("$.toolCalls[0].name").value("simulate_debt_payoff"))
                .andExpect(jsonPath("$.toolCalls[0].arguments").value("{\"extraPayment\":500}"));
    }

    @Test
    void omitsToolCallsFromAPlainAnswer() throws Exception {
        given(advisorService.getChatResponse(any()))
                .willReturn(new AdvisorService.AdvisorReply("Just an answer.", List.of()));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(jsonPath("$.response").value("Just an answer."))
                .andExpect(jsonPath("$.toolCalls").doesNotExist());
    }

    @Test
    void acceptsAContinuationCarryingToolResults() throws Exception {
        given(advisorService.getChatResponse(any()))
                .willReturn(new AdvisorService.AdvisorReply("Here is what that means.", List.of()));

        String continuation = """
                {
                  "message": null,
                  "history": [
                    { "role": "user", "content": "What if I paid $500 more?" },
                    { "role": "assistant", "content": "",
                      "toolCalls": [{ "id": "call_1", "name": "simulate_debt_payoff",
                                      "arguments": "{\\"extraPayment\\":500}" }] },
                    { "role": "tool", "toolCallId": "call_1", "content": "{\\"avalanche\\":{\\"months\\":30}}" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(continuation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.response").value("Here is what that means."));
    }

    @Test
    void reportsFailureWithoutLeakingInternalDetail() throws Exception {
        given(advisorService.getChatResponse(any()))
                .willThrow(new RuntimeException(
                        "401 Unauthorized calling https://api.groq.com/... Bearer gsk_secret_key_value"));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(FinancialAdvisorController.GENERIC_ERROR))
                // The frontend renders `error` straight into the chat, so nothing
                // from the upstream exception may appear in it.
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("gsk_"))))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("api.groq.com"))))
                .andExpect(jsonPath("$.response").doesNotExist());
    }

    @Test
    void healthEndpointRespondsWithoutTheAdvisor() throws Exception {
        given(advisorTools.names()).willReturn(List.of("simulate_debt_payoff"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("Financial Advisor API is running!")));
    }

    /**
     * What makes a deploy verifiable: an older build has no tools, so its health
     * string cannot name any.
     */
    @Test
    void healthEndpointNamesTheVersionsAndToolsThisBuildCarries() throws Exception {
        given(advisorTools.names())
                .willReturn(List.of("simulate_debt_payoff", "evaluate_goal", "project_savings"));
        given(advisorService.modelName()).willReturn("openai/gpt-oss-120b");

        mockMvc.perform(get("/api/health"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("model=openai/gpt-oss-120b")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("prompt=v4")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tools=v1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("simulate_debt_payoff")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("evaluate_goal")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("project_savings")));
    }
}

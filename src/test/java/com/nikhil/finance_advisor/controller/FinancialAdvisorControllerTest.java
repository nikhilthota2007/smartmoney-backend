package com.nikhil.finance_advisor.controller;

import com.nikhil.finance_advisor.service.AdvisorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
        given(advisorService.getChatResponse(any())).willReturn("Buy used, pay cash.");

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.response").value("Buy used, pay cash."));
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
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Financial Advisor API is running!"));
    }
}

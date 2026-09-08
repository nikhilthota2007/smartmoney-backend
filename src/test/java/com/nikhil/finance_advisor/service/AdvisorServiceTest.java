package com.nikhil.finance_advisor.service;

import com.nikhil.finance_advisor.model.ChatMessage;
import com.nikhil.finance_advisor.model.ChatRequest;
import com.nikhil.finance_advisor.model.FinancialData;
import com.nikhil.finance_advisor.model.ToolCall;
import com.nikhil.finance_advisor.prompt.AdvisorPrompt;
import com.nikhil.finance_advisor.prompt.FinancialPictureRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * The Groq request and response wire format.
 *
 * There is no API key in CI, so these assert the exact shape we send and the
 * shapes we accept back, against the OpenAI-compatible contract Groq implements.
 * The request-body assertions are the substitute for a live call: if the tool
 * round trip is malformed, it is malformed here.
 */
class AdvisorServiceTest {

    private RestTemplate restTemplate;
    private AdvisorService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        AdvisorTools tools = new AdvisorTools(new ObjectMapper());
        AdvisorPrompt prompt = new AdvisorPrompt(new FinancialPictureRenderer());
        service = new AdvisorService(restTemplate, prompt, tools, "test-key",
                "llama-3.3-70b-versatile", 0.7, 1000);
    }

    private void groqReturns(Map<String, Object> message) {
        given(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .willReturn(Map.of("choices", List.of(Map.of("message", message))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedBody() {
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate)
                .postForObject(any(String.class), captor.capture(), eq(Map.class));
        return captor.getValue().getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> capturedMessages() {
        return (List<Map<String, Object>>) capturedBody().get("messages");
    }

    private static ChatRequest ask(String message) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setFinancialData(new FinancialData("5000", "3500", "10000", "5000", "House"));
        return request;
    }

    @Test
    void sendsTheToolDeclarationsWithEveryRequest() {
        groqReturns(Map.of("content", "Here you go."));

        service.getChatResponse(ask("What should I do?"));

        Map<String, Object> body = capturedBody();
        assertThat(body.get("tools")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isNotEmpty();
        assertThat(body.get("tool_choice")).isEqualTo("auto");
        assertThat(body.get("model")).isEqualTo("llama-3.3-70b-versatile");
    }

    @Test
    void putsTheSystemPromptFirstAndTheQuestionLast() {
        groqReturns(Map.of("content", "ok"));

        service.getChatResponse(ask("What should I do?"));

        List<Map<String, Object>> messages = capturedMessages();
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(0).get("content")).asString().contains("FINANCIAL PICTURE");
        assertThat(messages.get(messages.size() - 1))
                .containsEntry("role", "user")
                .containsEntry("content", "What should I do?");
    }

    @Test
    void returnsPlainContentWhenTheModelJustAnswers() {
        groqReturns(Map.of("content", "Pay the card first."));

        AdvisorService.AdvisorReply reply = service.getChatResponse(ask("Advice?"));

        assertThat(reply.content()).isEqualTo("Pay the card first.");
        assertThat(reply.needsTools()).isFalse();
    }

    @Test
    void parsesToolCallsTheModelAsksFor() {
        groqReturns(Map.of("tool_calls", List.of(Map.of(
                "id", "call_abc",
                "type", "function",
                "function", Map.of("name", "simulate_debt_payoff", "arguments", "{\"extraPayment\":500}")))));

        AdvisorService.AdvisorReply reply = service.getChatResponse(ask("What if I paid more?"));

        assertThat(reply.needsTools()).isTrue();
        assertThat(reply.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call_abc");
            assertThat(call.name()).isEqualTo("simulate_debt_payoff");
            assertThat(call.arguments()).isEqualTo("{\"extraPayment\":500}");
        });
    }

    @Test
    void keepsContentAlongsideToolCallsWhenTheModelSendsBoth() {
        groqReturns(Map.of(
                "content", "Let me work that out.",
                "tool_calls", List.of(Map.of("id", "c1",
                        "function", Map.of("name", "project_savings", "arguments", "{\"months\":12}")))));

        AdvisorService.AdvisorReply reply = service.getChatResponse(ask("Project it"));

        assertThat(reply.content()).isEqualTo("Let me work that out.");
        assertThat(reply.needsTools()).isTrue();
    }

    @Test
    void skipsMalformedToolCallsRatherThanFailingTheTurn() {
        List<Object> mixed = List.of(
                Map.of("id", "good", "function", Map.of("name", "project_savings", "arguments", "{}")),
                Map.of("id", "no-function"),
                "not even a map");
        groqReturns(Map.of("content", "ok", "tool_calls", mixed));

        AdvisorService.AdvisorReply reply = service.getChatResponse(ask("hi"));

        assertThat(reply.toolCalls()).extracting(ToolCall::id).containsExactly("good");
    }

    @Test
    void defaultsMissingToolCallArgumentsToAnEmptyObject() {
        groqReturns(Map.of("tool_calls", List.of(Map.of(
                "id", "c1", "function", Map.of("name", "project_savings")))));

        assertThat(service.getChatResponse(ask("hi")).toolCalls().get(0).arguments()).isEqualTo("{}");
    }

    @Test
    void sendsAToolResultBackInTheShapeTheApiExpects() {
        groqReturns(Map.of("content", "Here is what that means."));

        ChatMessage assistantTurn = new ChatMessage("assistant", "");
        assistantTurn.setToolCalls(List.of(new ToolCall("call_1", "simulate_debt_payoff", "{\"extraPayment\":500}")));
        ChatMessage toolTurn = new ChatMessage("tool", "{\"avalanche\":{\"months\":30}}");
        toolTurn.setToolCallId("call_1");

        ChatRequest continuation = new ChatRequest();
        continuation.setHistory(List.of(new ChatMessage("user", "What if I paid $500 more?"), assistantTurn, toolTurn));
        service.getChatResponse(continuation);

        List<Map<String, Object>> messages = capturedMessages();

        Map<String, Object> assistant = messages.get(2);
        assertThat(assistant).containsEntry("role", "assistant");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wireCalls = (List<Map<String, Object>>) assistant.get("tool_calls");
        assertThat(wireCalls).singleElement().satisfies(call -> {
            assertThat(call).containsEntry("id", "call_1").containsEntry("type", "function");

            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) call.get("function");
            assertThat(function)
                    .containsEntry("name", "simulate_debt_payoff")
                    .containsEntry("arguments", "{\"extraPayment\":500}");
        });

        assertThat(messages.get(3))
                .containsEntry("role", "tool")
                .containsEntry("tool_call_id", "call_1")
                .containsEntry("content", "{\"avalanche\":{\"months\":30}}");
    }

    @Test
    void addsNoUserTurnOnAContinuation() {
        groqReturns(Map.of("content", "done"));

        ChatRequest continuation = new ChatRequest();
        continuation.setMessage(null);
        continuation.setHistory(List.of(new ChatMessage("user", "the original question")));
        service.getChatResponse(continuation);

        List<Map<String, Object>> messages = capturedMessages();
        assertThat(messages).hasSize(2); // system + the one history turn
        assertThat(messages.get(1)).containsEntry("content", "the original question");
    }

    @Test
    void treatsABlankMessageAsNoQuestion() {
        groqReturns(Map.of("content", "done"));

        ChatRequest request = new ChatRequest();
        request.setMessage("   ");
        service.getChatResponse(request);

        assertThat(capturedMessages()).hasSize(1); // system only
    }

    @Test
    void normalisesTheLegacyModelRoleToAssistant() {
        groqReturns(Map.of("content", "ok"));

        ChatRequest request = ask("hi");
        request.setHistory(List.of(new ChatMessage("model", "an older reply")));
        service.getChatResponse(request);

        assertThat(capturedMessages().get(1)).containsEntry("role", "assistant");
    }

    @Test
    void neverSendsNullContent() {
        groqReturns(Map.of("content", "ok"));

        ChatRequest request = ask("hi");
        request.setHistory(List.of(new ChatMessage("assistant", null)));
        service.getChatResponse(request);

        assertThat(capturedMessages().get(1)).containsEntry("content", "");
    }

    @Test
    void failsLoudlyOnAResponseItCannotUnderstand() {
        given(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .willReturn(Map.of("choices", List.of()));

        assertThatThrownBy(() -> service.getChatResponse(ask("hi")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no choices");
    }

    @Test
    void failsWhenTheModelReturnsNeitherAnAnswerNorAToolCall() {
        groqReturns(Map.of("role", "assistant"));

        assertThatThrownBy(() -> service.getChatResponse(ask("hi")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("neither content nor tool calls");
    }
}

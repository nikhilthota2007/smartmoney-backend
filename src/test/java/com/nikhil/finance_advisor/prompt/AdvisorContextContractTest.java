package com.nikhil.finance_advisor.prompt;

import com.nikhil.finance_advisor.model.ChatRequest;
import com.nikhil.finance_advisor.model.FinancialContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-repo contract.
 *
 * advisor-context.json is the exact payload the frontend's buildAdvisorContext
 * emits, generated from its own code and checked in on both sides. This test
 * proves the JSON deserializes into our records with nothing silently dropped,
 * which is the failure mode a hand-written fixture would miss: an unknown or
 * renamed field binds to null and the model quietly loses that figure.
 *
 * The frontend has the matching test. If one fails, the two repositories have
 * drifted.
 */
@SpringBootTest(properties = "groq.api.key=test-key-not-used")
class AdvisorContextContractTest {

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private FinancialPictureRenderer renderer;

    private String contractJson() throws IOException {
        try (InputStream in = new ClassPathResource("contract/advisor-context.json").getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    private FinancialContext deserialized() throws IOException {
        return objectMapper.readValue(contractJson(), FinancialContext.class);
    }

    @Test
    void everyTopLevelSectionBinds() throws IOException {
        FinancialContext context = deserialized();

        assertThat(context.metrics()).isNotNull();
        assertThat(context.healthScore()).isNotNull();
        assertThat(context.debts()).isNotEmpty();
        assertThat(context.debtPayoff()).isNotNull();
        assertThat(context.goals()).isNotEmpty();
        assertThat(context.protectionGaps()).isNotEmpty();
        assertThat(context.missing()).isNotNull();
        assertThat(context.completenessPct()).isNotNull();
    }

    @Test
    void everyMetricBinds() throws IOException {
        FinancialContext.Metrics metrics = deserialized().metrics();

        assertThat(metrics.monthlyIncome()).isEqualTo(5000.0);
        assertThat(metrics.monthlyExpenses()).isEqualTo(3500.0);
        assertThat(metrics.monthlySurplus()).isEqualTo(1500.0);
        assertThat(metrics.savingsRatePct()).isEqualTo(30.0);
        assertThat(metrics.debtToIncomePct()).isNotNull();
        assertThat(metrics.emergencyFundMonths()).isNotNull();
        assertThat(metrics.liquidSavings()).isEqualTo(10000.0);
        assertThat(metrics.totalDebt()).isEqualTo(25000.0);
        assertThat(metrics.netWorth()).isNotNull();
    }

    @Test
    void debtTermsAndTheUnpayableFlagBind() throws IOException {
        FinancialContext.Debt runaway = deserialized().debts().stream()
                .filter(debt -> "Runaway".equals(debt.name()))
                .findFirst()
                .orElseThrow();

        assertThat(runaway.balance()).isEqualTo(20000.0);
        assertThat(runaway.aprPct()).isEqualTo(30.0);
        assertThat(runaway.minPayment()).isEqualTo(50.0);
        assertThat(runaway.neverPaidOffAtMinimum()).isTrue();
    }

    @Test
    void payoffTimelinesBind() throws IOException {
        FinancialContext.DebtPayoff payoff = deserialized().debtPayoff();

        assertThat(payoff.avalanche().months()).isPositive();
        assertThat(payoff.avalanche().totalInterest()).isPositive();
        assertThat(payoff.avalanche().clearsWithinProjection()).isNotNull();
        assertThat(payoff.snowball()).isNotNull();
        assertThat(payoff.atRecommendedPayment()).isNotNull();
        assertThat(payoff.recommendedExtraPayment()).isPositive();
    }

    @Test
    void theHealthScoreAndItsComponentsBind() throws IOException {
        FinancialContext.HealthScore score = deserialized().healthScore();

        assertThat(score.total()).isNotNull();
        assertThat(score.rating()).isNotBlank();
        assertThat(score.components()).hasSize(3);
        assertThat(score.components().get(0).name()).isNotBlank();
        assertThat(score.components().get(0).max()).isPositive();
    }

    @Test
    void theWholePayloadRendersIntoTheModelsView() throws IOException {
        String rendered = renderer.render(deserialized(), null);

        assertThat(rendered)
                .contains("MONTHLY CASH FLOW")
                .contains("FINANCIAL HEALTH SCORE")
                .contains("DEBTS")
                .contains("never gets paid off")
                .contains("DEBT PAYOFF, ALREADY CALCULATED")
                .contains("GOALS")
                .contains("COVERAGE GAPS")
                .doesNotContain("Not provided")
                .doesNotContain("null");
    }

    @Test
    void aFullChatRequestFromTheFrontendBinds() throws IOException {
        String body = """
                {
                  "message": "Should I pay off the card first?",
                  "financialData": { "monthlyIncome": "5000", "monthlyExpenses": "3500",
                                     "savings": "10000", "debts": "25000", "goals": "House" },
                  "history": [{ "role": "assistant", "content": "Hello" }],
                  "financialContext": %s
                }
                """.formatted(contractJson());

        ChatRequest request = objectMapper.readValue(body, ChatRequest.class);

        assertThat(request.getMessage()).isNotBlank();
        assertThat(request.getFinancialData().getMonthlyIncome()).isEqualTo("5000");
        assertThat(request.getHistory()).hasSize(1);
        assertThat(request.getFinancialContext().metrics().monthlyIncome()).isEqualTo(5000.0);
    }
}

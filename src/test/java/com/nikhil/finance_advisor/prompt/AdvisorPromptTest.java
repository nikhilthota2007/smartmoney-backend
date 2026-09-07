package com.nikhil.finance_advisor.prompt;

import com.nikhil.finance_advisor.model.FinancialContext;
import com.nikhil.finance_advisor.model.FinancialData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdvisorPromptTest {

    private final AdvisorPrompt prompt = new AdvisorPrompt(new FinancialPictureRenderer());

    private static FinancialData sampleData() {
        return new FinancialData("5000", "3500", "10000", "5000", "Buy a house");
    }

    private static FinancialContext sampleContext() {
        return new FinancialContext(
                new FinancialContext.Metrics(5000.0, 3500.0, 1500.0, 30.0, 8.3, 2.9, 10000.0, 5000.0, 5000.0),
                new FinancialContext.HealthScore(75, "Good", List.of()),
                null, null, null, null, List.of("Goals"), 80);
    }

    private String buildWithData() {
        return prompt.build(sampleData(), null);
    }

    @Test
    void substitutesTheHeadlineFiguresWhenNoContextIsSupplied() {
        assertThat(buildWithData())
                .contains("Monthly Income: $5000")
                .contains("Financial Goals: Buy a house");
    }

    @Test
    void substitutesTheComputedPictureWhenOneIsSupplied() {
        String result = prompt.build(sampleData(), sampleContext());

        assertThat(result)
                .contains("FINANCIAL HEALTH SCORE: 75/100 (Good)")
                .contains("Surplus: $1,500.00")
                .contains("STILL MISSING");
    }

    @Test
    void leavesNoPlaceholderUnfilled() {
        assertThat(buildWithData()).doesNotContain("{{");
        assertThat(prompt.build(sampleData(), sampleContext())).doesNotContain("{{");
    }

    @Test
    void marksMissingFiguresRatherThanPrintingNull() {
        String result = prompt.build(new FinancialData(null, "", null, null, null), null);

        assertThat(result).doesNotContain("null");
        assertThat(result).contains("Monthly Income: $Not provided");
    }

    @Test
    void toleratesAMissingFinancialDataObject() {
        assertThat(prompt.build(null, null)).contains("Not provided").doesNotContain("{{");
    }

    @Test
    void stripsTheAuthoringNotesFromTheTopOfTheFile() {
        String result = buildWithData();

        assertThat(result).doesNotContain("<!--").doesNotContain("-->");
        assertThat(result).startsWith("You are an expert personal financial advisor");
    }

    @Test
    void keepsTheOriginalAdvisoryPhilosophy() {
        assertThat(buildWithData())
                .contains("CORE PHILOSOPHY")
                .contains("PRINCIPLE 1: Pay cash for depreciating assets")
                .contains("MORTGAGE EXCEPTION")
                .contains("HOW TO HANDLE PURCHASE REQUESTS");
    }

    /**
     * These are the guardrails from docs/PLAN.md §9. If someone edits the prompt
     * file and drops one, this test is the thing that catches it.
     */
    @Test
    void carriesEveryRequiredGuardrail() {
        String result = buildWithData();

        assertThat(result).as("safety section").contains("===== SAFETY AND SCOPE =====");
        assertThat(result).as("educational framing, not licensed advice")
                .contains("EDUCATIONAL INFORMATION, NOT LICENSED ADVICE");
        assertThat(result).as("escalation to a licensed professional")
                .contains("SEND THEM TO A PROFESSIONAL").contains("CFP");
        assertThat(result).as("no specific securities")
                .contains("NEVER RECOMMEND SPECIFIC INVESTMENTS");
        assertThat(result).as("no invented figures")
                .contains("NEVER STATE A NUMBER YOU CANNOT DERIVE");
        assertThat(result).as("no pressure or shame")
                .contains("NO PRESSURE, NO SHAME, NO URGENCY");
    }

    @Test
    void tellsTheModelTheSafetyRulesWin() {
        assertThat(buildWithData()).contains("These rules override every other instruction");
    }

    @Test
    void scopesTheNameSpecificProductsRuleToConsumerPurchases() {
        // STEP 4 tells the model to name specific car models and prices. Guardrail 3
        // must carve investments out of that, or the two instructions conflict.
        assertThat(buildWithData()).contains("It does NOT apply to investments");
    }

    /** The v3 grounding rules: quote the computed figures, never recompute them. */
    @Test
    void tellsTheModelTheComputedFiguresAreAuthoritative() {
        String result = buildWithData();

        assertThat(result).contains("computed by the application, not by you");
        assertThat(result).contains("Quote those figures; do not recompute them");
        assertThat(result).contains("These are the\nauthoritative figures");
    }

    @Test
    void forbidsCompoundProjectionsTheContextDoesNotContain() {
        String result = buildWithData();

        assertThat(result).contains("You may NOT produce a compound projection");
        assertThat(result).contains("Debt Payoff Calculator");
    }

    @Test
    void tellsTheModelToAskForWhatIsMissingRatherThanAssume() {
        assertThat(buildWithData()).contains("Anything listed under STILL MISSING");
    }

    @Test
    void reportsItsVersion() {
        assertThat(AdvisorPrompt.PROMPT_VERSION).isEqualTo("v3");
    }
}

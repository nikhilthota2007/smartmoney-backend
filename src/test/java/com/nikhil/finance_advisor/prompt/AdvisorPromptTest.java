package com.nikhil.finance_advisor.prompt;

import com.nikhil.finance_advisor.model.FinancialData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdvisorPromptTest {

    private final AdvisorPrompt prompt = new AdvisorPrompt();

    private static FinancialData sampleData() {
        return new FinancialData("5000", "3500", "10000", "5000", "Buy a house");
    }

    @Test
    void substitutesTheUsersFigures() {
        String result = prompt.build(sampleData());

        assertThat(result)
                .contains("Monthly Income: $5000")
                .contains("Monthly Expenses: $3500")
                .contains("Current Savings: $10000")
                .contains("Outstanding Debts: $5000")
                .contains("Financial Goals: Buy a house");
    }

    @Test
    void leavesNoPlaceholderUnfilled() {
        assertThat(prompt.build(sampleData())).doesNotContain("{{");
    }

    @Test
    void marksMissingFiguresRatherThanPrintingNull() {
        String result = prompt.build(new FinancialData(null, "", null, null, null));

        assertThat(result).doesNotContain("null");
        assertThat(result).contains("Monthly Income: $Not provided");
        assertThat(result).contains("Monthly Expenses: $Not provided");
    }

    @Test
    void toleratesAMissingFinancialDataObject() {
        assertThat(prompt.build(null))
                .contains("Not provided")
                .doesNotContain("{{");
    }

    @Test
    void stripsTheAuthoringNotesFromTheTopOfTheFile() {
        String result = prompt.build(sampleData());

        assertThat(result).doesNotContain("<!--").doesNotContain("-->");
        assertThat(result).startsWith("You are an expert personal financial advisor");
    }

    @Test
    void keepsTheOriginalAdvisoryPhilosophy() {
        String result = prompt.build(sampleData());

        assertThat(result)
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
        String result = prompt.build(sampleData());

        assertThat(result).as("safety section").contains("===== SAFETY AND SCOPE =====");
        assertThat(result).as("educational framing, not licensed advice")
                .contains("EDUCATIONAL INFORMATION, NOT LICENSED ADVICE");
        assertThat(result).as("escalation to a licensed professional")
                .contains("SEND THEM TO A PROFESSIONAL")
                .contains("CFP");
        assertThat(result).as("no specific securities")
                .contains("NEVER RECOMMEND SPECIFIC INVESTMENTS");
        assertThat(result).as("no invented figures")
                .contains("NEVER STATE A NUMBER YOU CANNOT DERIVE");
        assertThat(result).as("no pressure or shame")
                .contains("NO PRESSURE, NO SHAME, NO URGENCY");
    }

    @Test
    void tellsTheModelTheSafetyRulesWin() {
        assertThat(prompt.build(sampleData()))
                .contains("These rules override every other instruction");
    }

    @Test
    void scopesTheNameSpecificProductsRuleToConsumerPurchases() {
        // STEP 4 tells the model to name specific car models and prices. Guardrail 3
        // must carve investments out of that, or the two instructions conflict.
        assertThat(prompt.build(sampleData()))
                .contains("It does NOT apply to investments");
    }

    @Test
    void reportsItsVersion() {
        assertThat(AdvisorPrompt.PROMPT_VERSION).isEqualTo("v2");
    }
}

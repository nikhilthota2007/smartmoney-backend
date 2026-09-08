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
    void forbidsComputingCompoundProjectionsInProse() {
        assertThat(buildWithData()).contains("You may NOT work any of these out yourself");
    }

    /** v4: the escape hatch moved from "say you cannot" to "call the tool". */
    @Test
    void sendsTheModelToTheToolsRatherThanHavingItDecline() {
        String result = buildWithData();

        assertThat(result).contains("CALL THE TOOL THAT COMPUTES IT");
        assertThat(result).contains("Do not tell the user you are unable to work it");
        assertThat(result).contains("A tool result is authoritative");
    }

    @Test
    void describesEveryToolItCanCall() {
        String result = buildWithData();

        assertThat(result).contains("===== CALCULATIONS YOU CAN REQUEST =====");
        assertThat(result).contains("simulate_debt_payoff");
        assertThat(result).contains("evaluate_goal");
        assertThat(result).contains("project_savings");
    }

    @Test
    void requiresTheReturnAssumptionToBeRepeatedToTheUser() {
        assertThat(buildWithData()).contains("must repeat that assumption in your answer");
    }

    @Test
    void keepsTheToolMachineryOutOfTheUsersView() {
        assertThat(buildWithData()).contains("They are how you work, not");
    }

    @Test
    void tellsTheModelToAskForWhatIsMissingRatherThanAssume() {
        assertThat(buildWithData()).contains("Anything listed under STILL MISSING");
    }

    /**
     * v5. A live run against Groq produced both of these failures in one answer:
     * a fabricated "roughly 2 months" for a gap that really takes 10 to 13, and
     * "The tool can recalculate that" said straight to the user.
     */
    @Test
    void forbidsWorkingOutATimelineInProse() {
        String result = buildWithData();

        assertThat(result).contains("HOW LONG until a target is reached");
        assertThat(result).contains("how much is needed each month to reach a target by a date");
        assertThat(result).contains("is a timeline");
    }

    @Test
    void narrowsWhatCountsAsSimpleArithmetic() {
        String result = buildWithData();

        assertThat(result).contains("SINGLE step you can show in");
        assertThat(result).contains("this licence does not cover it");
    }

    @Test
    void sendsEmergencyFundTimelinesToEvaluateGoal() {
        String result = buildWithData();

        assertThat(result).contains("An emergency-fund target is a goal like any other");
        assertThat(result).contains("how long until I have");
    }

    @Test
    void forbidsReferringToTheToolsAtAll() {
        String result = buildWithData();

        assertThat(result).contains("NEVER REFER TO THESE TOOLS IN YOUR ANSWER");
        assertThat(result).contains("\"the tool\"");
        assertThat(result).contains("tell me another amount and I will work");
    }

    @Test
    void reportsItsVersion() {
        assertThat(AdvisorPrompt.PROMPT_VERSION).isEqualTo("v5");
    }
}

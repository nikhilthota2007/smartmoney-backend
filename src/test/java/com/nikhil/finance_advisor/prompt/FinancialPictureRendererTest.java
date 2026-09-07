package com.nikhil.finance_advisor.prompt;

import com.nikhil.finance_advisor.model.FinancialContext;
import com.nikhil.finance_advisor.model.FinancialData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialPictureRendererTest {

    private final FinancialPictureRenderer renderer = new FinancialPictureRenderer();

    private static FinancialContext.Metrics metrics() {
        return new FinancialContext.Metrics(5000.0, 3500.0, 1500.0, 30.0, 8.3, 2.9, 10000.0, 5000.0, 5000.0);
    }

    private static FinancialContext fullContext() {
        return new FinancialContext(
                metrics(),
                new FinancialContext.HealthScore(75, "Good", List.of(
                        new FinancialContext.HealthScore.Component("Savings rate", 40, 40),
                        new FinancialContext.HealthScore.Component("Debt to income", 25, 30))),
                List.of(new FinancialContext.Debt("Card", "card", 5000.0, 23.0, 150.0, false)),
                new FinancialContext.DebtPayoff(100.0, 500.0,
                        new FinancialContext.DebtPayoff.Payoff(38, 1234.56, 6234.56, 250.0, true),
                        new FinancialContext.DebtPayoff.Payoff(40, 1300.0, 6300.0, 250.0, true),
                        new FinancialContext.DebtPayoff.Payoff(9, 500.0, 5500.0, 650.0, true)),
                List.of(new FinancialContext.Goal("House", "purchase", 60000.0, "2029-06")),
                List.of("Health insurance", "Disability insurance"),
                List.of("Goals"),
                80);
    }

    @Test
    void rendersTheCashFlowAndPositionFigures() {
        String out = renderer.render(fullContext(), null);

        assertThat(out)
                .contains("Income: $5,000.00")
                .contains("Surplus: $1,500.00")
                .contains("Savings rate: 30%")
                .contains("Total debt: $5,000.00")
                .contains("Emergency fund: 2.9 months");
    }

    @Test
    void rendersTheHealthScoreWithItsComponents() {
        assertThat(renderer.render(fullContext(), null))
                .contains("FINANCIAL HEALTH SCORE: 75/100 (Good)")
                .contains("Savings rate: 40/40");
    }

    @Test
    void itemizesDebtsWithTheirTerms() {
        assertThat(renderer.render(fullContext(), null))
                .contains("Card (card): $5,000.00 at 23% APR, minimum $150.00");
    }

    @Test
    void callsOutADebtThatIsNeverPaidOff() {
        FinancialContext context = new FinancialContext(
                metrics(), null,
                List.of(new FinancialContext.Debt("Runaway", "card", 20000.0, 29.99, 50.0, true)),
                null, null, null, null, null);

        assertThat(renderer.render(context, null)).contains("never gets paid off");
    }

    @Test
    void suppliesPayoffTimelinesSoTheModelNeedNotComputeThem() {
        String out = renderer.render(fullContext(), null);

        assertThat(out)
                .contains("avalanche: 38 months, $1,234.56 total interest")
                .contains("snowball: 40 months")
                .contains("recommended $500.00/mo");
    }

    @Test
    void saysWhenAPayoffRunsPastTheProjection() {
        FinancialContext context = new FinancialContext(
                metrics(), null, null,
                new FinancialContext.DebtPayoff(0.0, 100.0,
                        new FinancialContext.DebtPayoff.Payoff(600, 250000.0, 270000.0, 50.0, false),
                        null, null),
                null, null, null, null);

        assertThat(renderer.render(context, null)).contains("not cleared within the 50-year projection");
    }

    @Test
    void warnsThatOtherPaymentAmountsAreNotCalculated() {
        assertThat(renderer.render(fullContext(), null))
                .contains("Any other payment amount is NOT calculated here - see guardrail 4.");
    }

    @Test
    void rendersNegativeAmountsWithTheSignBeforeTheCurrency() {
        FinancialContext context = new FinancialContext(
                new FinancialContext.Metrics(0.0, 0.0, -500.0, null, null, null, 0.0, 15000.0, -15000.0),
                null, null, null, null, null, null, null);

        assertThat(renderer.render(context, null))
                .contains("Net worth: -$15,000.00")
                .contains("Surplus: -$500.00")
                .doesNotContain("$-");
    }

    /**
     * This string is sent to the model. The build's native encoding is ASCII in
     * some environments, so anything outside it is a needless dependency on the
     * toolchain reading the source correctly.
     */
    @Test
    void rendersPureAsciiSoTheBuildEncodingCannotCorruptIt() {
        String out = renderer.render(fullContext(), null);

        assertThat(out).matches("\\A\\p{ASCII}*\\z");
    }

    @Test
    void listsGoalsCoverageGapsAndWhatIsMissing() {
        String out = renderer.render(fullContext(), null);

        assertThat(out)
                .contains("House: $60,000.00 by 2029-06")
                .contains("COVERAGE GAPS")
                .contains("Health insurance")
                .contains("STILL MISSING (ask for these; never assume them)")
                .contains("Profile is 80% complete.");
    }

    @Test
    void omitsSectionsThatHaveNothingToSay() {
        FinancialContext sparse = new FinancialContext(metrics(), null, null, null, null, null, null, null);
        String out = renderer.render(sparse, null);

        assertThat(out)
                .contains("MONTHLY CASH FLOW")
                .doesNotContain("DEBTS")
                .doesNotContain("GOALS")
                .doesNotContain("COVERAGE GAPS")
                .doesNotContain("STILL MISSING");
    }

    @Test
    void printsCleanNumbersRatherThanFloatingPointNoise() {
        String out = renderer.render(fullContext(), null);

        assertThat(out).doesNotContain("0000000").doesNotContain("30.0%");
    }

    @Test
    void fallsBackToTheHeadlineFiguresForAnOlderClient() {
        FinancialData data = new FinancialData("5000", "3500", "10000", "5000", "Buy a house");
        String out = renderer.render(null, data);

        assertThat(out)
                .contains("Monthly Income: $5000")
                .contains("Financial Goals: Buy a house")
                .contains("do not quote figures beyond them");
    }

    @Test
    void survivesAnEmptyContextAndNoDataAtAll() {
        assertThat(renderer.render(null, null)).contains("Not provided");

        FinancialContext empty = new FinancialContext(null, null, null, null, null, null, null, null);
        assertThat(renderer.render(empty, new FinancialData("4000", null, null, null, null)))
                .contains("Monthly Income: $4000");
    }
}

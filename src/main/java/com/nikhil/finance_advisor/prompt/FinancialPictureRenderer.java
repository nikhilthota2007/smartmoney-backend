package com.nikhil.finance_advisor.prompt;

import com.nikhil.finance_advisor.model.FinancialContext;
import com.nikhil.finance_advisor.model.FinancialData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

/**
 * Renders the computed financial picture as the block of text the model reads.
 *
 * Deliberately terse: labelled lines rather than prose or raw JSON, so the
 * figures are unambiguous and cheap in tokens. Sections with nothing to say are
 * omitted entirely rather than padded with "none" — an absent section says less
 * to the model than an empty one.
 *
 * Falls back to the five headline figures when an older client sends no context.
 */
@Component
public class FinancialPictureRenderer {

    private static final String NOT_PROVIDED = "Not provided";

    public String render(FinancialContext context, FinancialData data) {
        if (context == null) return renderHeadlineFiguresOnly(data);

        StringJoiner out = new StringJoiner("\n");
        appendMetrics(out, context.metrics());
        appendHealthScore(out, context.healthScore());
        appendDebts(out, context.debts());
        appendPayoff(out, context.debtPayoff());
        appendGoals(out, context.goals());
        appendList(out, "COVERAGE GAPS", context.protectionGaps());
        appendMissing(out, context.missing(), context.completenessPct());

        String rendered = out.toString();
        return rendered.isBlank() ? renderHeadlineFiguresOnly(data) : rendered;
    }

    private void appendMetrics(StringJoiner out, FinancialContext.Metrics m) {
        if (m == null) return;
        out.add("MONTHLY CASH FLOW");
        out.add("  Income: " + money(m.monthlyIncome()));
        out.add("  Expenses: " + money(m.monthlyExpenses()));
        out.add("  Surplus: " + money(m.monthlySurplus()));
        out.add("  Savings rate: " + pct(m.savingsRatePct()));
        out.add("");
        out.add("POSITION");
        out.add("  Reachable savings: " + money(m.liquidSavings()));
        out.add("  Total debt: " + money(m.totalDebt()));
        out.add("  Debt as share of annual income: " + pct(m.debtToIncomePct()));
        out.add("  Emergency fund: " + number(m.emergencyFundMonths()) + " months of expenses");
        out.add("  Net worth: " + money(m.netWorth()));
        out.add("");
    }

    private void appendHealthScore(StringJoiner out, FinancialContext.HealthScore score) {
        if (score == null) return;
        out.add("FINANCIAL HEALTH SCORE: " + score.total() + "/100 (" + score.rating() + ")");
        if (score.components() != null) {
            for (FinancialContext.HealthScore.Component component : score.components()) {
                out.add("  " + component.name() + ": " + component.score() + "/" + component.max());
            }
        }
        out.add("");
    }

    private void appendDebts(StringJoiner out, List<FinancialContext.Debt> debts) {
        if (isEmpty(debts)) return;
        out.add("DEBTS");
        for (FinancialContext.Debt debt : debts) {
            String line = "  " + debt.name() + " (" + debt.type() + "): " + money(debt.balance())
                    + " at " + pct(debt.aprPct()) + " APR, minimum " + money(debt.minPayment());
            if (Boolean.TRUE.equals(debt.neverPaidOffAtMinimum())) {
                line += "  <-- the minimum does not cover this debt's interest, so it never gets paid off";
            }
            out.add(line);
        }
        out.add("");
    }

    private void appendPayoff(StringJoiner out, FinancialContext.DebtPayoff payoff) {
        if (payoff == null) return;
        out.add("DEBT PAYOFF, ALREADY CALCULATED");
        appendPayoffLine(out, "At " + money(payoff.extraPayment()) + "/mo extra, avalanche", payoff.avalanche());
        appendPayoffLine(out, "At " + money(payoff.extraPayment()) + "/mo extra, snowball", payoff.snowball());
        appendPayoffLine(out, "At the recommended " + money(payoff.recommendedExtraPayment()) + "/mo extra, avalanche",
                payoff.atRecommendedPayment());
        out.add("  Any other payment amount is NOT calculated here - see guardrail 4.");
        out.add("");
    }

    private void appendPayoffLine(StringJoiner out, String label, FinancialContext.DebtPayoff.Payoff payoff) {
        if (payoff == null) return;
        if (Boolean.FALSE.equals(payoff.clearsWithinProjection())) {
            out.add("  " + label + ": not cleared within the 50-year projection");
            return;
        }
        out.add("  " + label + ": " + payoff.months() + " months, "
                + money(payoff.totalInterest()) + " total interest");
    }

    private void appendGoals(StringJoiner out, List<FinancialContext.Goal> goals) {
        if (isEmpty(goals)) return;
        out.add("GOALS");
        for (FinancialContext.Goal goal : goals) {
            String line = "  " + goal.name();
            if (goal.targetAmount() != null && goal.targetAmount() > 0) line += ": " + money(goal.targetAmount());
            if (goal.targetDate() != null && !goal.targetDate().isBlank()) line += " by " + goal.targetDate();
            out.add(line);
        }
        out.add("");
    }

    private void appendList(StringJoiner out, String heading, List<String> items) {
        if (isEmpty(items)) return;
        out.add(heading);
        items.forEach(item -> out.add("  " + item));
        out.add("");
    }

    private void appendMissing(StringJoiner out, List<String> missing, Integer completenessPct) {
        if (isEmpty(missing)) return;
        out.add("STILL MISSING (ask for these; never assume them)");
        missing.forEach(item -> out.add("  " + item));
        if (completenessPct != null) {
            out.add("  Profile is " + completenessPct + "% complete.");
        }
        out.add("");
    }

    private String renderHeadlineFiguresOnly(FinancialData data) {
        FinancialData safe = data != null ? data : new FinancialData();
        return String.join("\n",
                "Monthly Income: $" + orNotProvided(safe.getMonthlyIncome()),
                "Monthly Expenses: $" + orNotProvided(safe.getMonthlyExpenses()),
                "Current Savings: $" + orNotProvided(safe.getSavings()),
                "Outstanding Debts: $" + orNotProvided(safe.getDebts()),
                "Financial Goals: " + orNotProvided(safe.getGoals()),
                "",
                "Only these headline figures are available for this request; nothing",
                "else has been calculated, so do not quote figures beyond them.");
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static String orNotProvided(String value) {
        return value == null || value.isBlank() ? NOT_PROVIDED : value;
    }

    private static String money(Double value) {
        if (value == null) return NOT_PROVIDED;
        // A negative net worth reads as -$15,000.00, not $-15,000.00.
        String formatted = String.format("$%,.2f", Math.abs(value));
        return value < 0 ? "-" + formatted : formatted;
    }

    private static String pct(Double value) {
        return value == null ? NOT_PROVIDED : trim(value) + "%";
    }

    private static String number(Double value) {
        return value == null ? NOT_PROVIDED : trim(value);
    }

    /** 2.9 rather than 2.9000000000000004, and 30 rather than 30.0. */
    private static String trim(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }
}

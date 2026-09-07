package com.nikhil.finance_advisor.model;

import java.util.List;

/**
 * The computed financial picture, produced by the frontend's tested financial
 * logic and sent alongside the user's question.
 *
 * Records rather than the POJOs used elsewhere: these are read-only transport
 * types with no behaviour, and every field is a boxed type so a partially
 * completed profile deserializes with nulls rather than failing.
 *
 * Nothing here is recomputed server-side. The prompt instructs the model to
 * quote these figures rather than derive its own.
 */
public record FinancialContext(
        Metrics metrics,
        HealthScore healthScore,
        List<Debt> debts,
        DebtPayoff debtPayoff,
        List<Goal> goals,
        List<String> protectionGaps,
        List<String> missing,
        Integer completenessPct) {

    public record Metrics(
            Double monthlyIncome,
            Double monthlyExpenses,
            Double monthlySurplus,
            Double savingsRatePct,
            Double debtToIncomePct,
            Double emergencyFundMonths,
            Double liquidSavings,
            Double totalDebt,
            Double netWorth) {}

    public record HealthScore(Integer total, String rating, List<Component> components) {
        public record Component(String name, Integer score, Integer max) {}
    }

    public record Debt(
            String name,
            String type,
            Double balance,
            Double aprPct,
            Double minPayment,
            Boolean neverPaidOffAtMinimum) {}

    public record DebtPayoff(
            Double extraPayment,
            Double recommendedExtraPayment,
            Payoff avalanche,
            Payoff snowball,
            Payoff atRecommendedPayment) {

        public record Payoff(
                Integer months,
                Double totalInterest,
                Double totalPaid,
                Double monthlyPayment,
                Boolean clearsWithinProjection) {}
    }

    public record Goal(String name, String type, Double targetAmount, String targetDate) {}
}

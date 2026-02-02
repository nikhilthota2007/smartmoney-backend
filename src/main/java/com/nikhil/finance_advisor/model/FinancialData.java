package com.nikhil.finance_advisor.model;

public class FinancialData {
    private String monthlyIncome;
    private String monthlyExpenses;
    private String savings;
    private String debts;
    private String goals;

    public FinancialData() {}

    public FinancialData(String monthlyIncome, String monthlyExpenses, 
                         String savings, String debts, String goals) {
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
        this.savings = savings;
        this.debts = debts;
        this.goals = goals;
    }

    public String getMonthlyIncome() { return monthlyIncome; }
    public void setMonthlyIncome(String monthlyIncome) { this.monthlyIncome = monthlyIncome; }

    public String getMonthlyExpenses() { return monthlyExpenses; }
    public void setMonthlyExpenses(String monthlyExpenses) { this.monthlyExpenses = monthlyExpenses; }

    public String getSavings() { return savings; }
    public void setSavings(String savings) { this.savings = savings; }

    public String getDebts() { return debts; }
    public void setDebts(String debts) { this.debts = debts; }

    public String getGoals() { return goals; }
    public void setGoals(String goals) { this.goals = goals; }
}
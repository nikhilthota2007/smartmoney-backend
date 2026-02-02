package com.nikhil.finance_advisor.model;

public class FinanceRequest {
    private double income;
    private double expenses;
    
    // Constructors
    public FinanceRequest() {}
    
    public FinanceRequest(double income, double expenses) {
        this.income = income;
        this.expenses = expenses;
    }
    
    // Getters and Setters
    public double getIncome() {
        return income;
    }
    
    public void setIncome(double income) {
        this.income = income;
    }
    
    public double getExpenses() {
        return expenses;
    }
    
    public void setExpenses(double expenses) {
        this.expenses = expenses;
    }
}
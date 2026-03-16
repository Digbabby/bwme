package com.example.bwme;

public class Savings {
    public double amount;
    public String period;
    public long ts;

    public Savings() {
        this.period = "monthly";
        this.ts = System.currentTimeMillis();
    }

    public Savings(double amount, String period, long ts) {
        this.amount = amount;
        this.period = period != null ? period : "monthly";
        this.ts = ts;
    }
}
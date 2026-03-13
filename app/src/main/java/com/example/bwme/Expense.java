package com.example.bwme;

public class Expense {
    public double amount;
    public String desc;
    public long ts;
    public Double lat;
    public Double lng;
    public Expense() {}

    public Expense(double amount, String desc, long ts) {
        this(amount, desc, ts, null, null);
    }

    public Expense(double amount, String desc, long ts, Double lat, Double lng) {
        this.amount = amount;
        this.desc = desc;
        this.ts = ts;
        this.lat = lat;
        this.lng = lng;
    }

    @Override
    public String toString() {
        return "Expense{" +
                "amount=" + amount +
                ", desc='" + desc + '\'' +
                ", ts=" + ts +
                ", lat=" + lat +
                ", lng=" + lng +
                '}';
    }
}
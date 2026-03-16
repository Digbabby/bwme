package com.example.bwme;

public class Expense {

    public double amount;
    public String desc;
    public long ts;
    public Double lat;
    public Double lng;
    public String category;
    public Long reminderTs;

    public Expense() {
        this.category = "Other";
        this.reminderTs = null;
    }

    public Expense(double amount, String desc, long ts) {
        this(amount, desc, ts, null, null, "Other", null);
    }

    public Expense(double amount, String desc, long ts, Double lat, Double lng) {
        this(amount, desc, ts, lat, lng, "Other", null);
    }

    public Expense(double amount, String desc, long ts, Double lat, Double lng, String category) {
        this(amount, desc, ts, lat, lng, category, null);
    }

    public Expense(double amount, String desc, long ts, Double lat, Double lng, String category, Long reminderTs) {
        this.amount = amount;
        this.desc = desc;
        this.ts = ts;
        this.lat = lat;
        this.lng = lng;
        this.category = category != null ? category : "Other";
        this.reminderTs = reminderTs;
    }
}
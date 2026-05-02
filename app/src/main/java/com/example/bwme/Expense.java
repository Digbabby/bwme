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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Expense expense = (Expense) o;
        return Double.compare(expense.amount, amount) == 0 &&
                ts == expense.ts &&
                java.util.Objects.equals(desc, expense.desc) &&
                java.util.Objects.equals(lat, expense.lat) &&
                java.util.Objects.equals(lng, expense.lng) &&
                java.util.Objects.equals(category, expense.category);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(amount, desc, ts, lat, lng, category);
    }
}
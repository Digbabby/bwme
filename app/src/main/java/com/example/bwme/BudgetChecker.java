package com.example.bwme;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class BudgetChecker {
    private static final String TAG = "BudgetChecker";
    private static final String KEY_BUDGET_AMOUNT = "budget_amount";
    private static final String KEY_BUDGET_PERIOD = "budget_period";
    private static final String KEY_LAST_NOTIFIED_DAILY = "last_notified_daily";
    private static final String KEY_LAST_NOTIFIED_WEEKLY = "last_notified_weekly";
    private static final String KEY_LAST_NOTIFIED_MONTHLY = "last_notified_monthly";
    private static final String KEY_LAST_PROCESSED_EXP_TS = "last_processed_exp_ts";
    private static final double NEARLY_FULL = 0.75;
    private static final long NOTIF_COOLDOWN_MS = 0L;
    private static final long MS_DAY = 24L * 3600L * 1000L;

    public static void checkAndNotify(Context ctx, List<Expense> expenses) {
        if (ctx == null) {
            Log.w(TAG, "context is null; aborting check");
            return;
        }
        try {
            // Use the centralized helper to get the correct user-specific preferences
            SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
            double profileBudget = readBudgetAmount(prefs);
            String profilePeriod = prefs.getString(KEY_BUDGET_PERIOD, "monthly");
            if (profileBudget <= 0.0) {
                checkAndSendReminders(ctx, prefs, expenses);
                return;
            }

            Budgets base = deriveBudgets(profileBudget, profilePeriod);
            double allocatedDeduction = sumAllocatedExpenses(prefs);
            double savingsMonthly = sumSavingsMonthly(prefs);
            double monthlyFromInput = base.monthly;
            double remainingMonthly = monthlyFromInput - allocatedDeduction - savingsMonthly;
            if (remainingMonthly < 0.0) remainingMonthly = 0.0;
            Budgets b = deriveBudgets(remainingMonthly, "monthly");

            long maxExpenseTs = 0L;
            if (expenses != null) {
                for (Expense ex : expenses) {
                    if (ex == null) continue;
                    long ts = ex.ts;
                    if (ts > maxExpenseTs) maxExpenseTs = ts;
                }
            }

            long lastProcessedTs = prefs.getLong(KEY_LAST_PROCESSED_EXP_TS, 0L);
            if (maxExpenseTs <= lastProcessedTs) {
                checkAndSendReminders(ctx, prefs, expenses);
                return;
            }

            long startOfDay = getStartOfToday();
            long startOfWeek = getStartOfThisWeek();
            long startOfMonth = getStartOfThisMonth();

            double sumDay = sumSinceExcludingAllocated(expenses, startOfDay);
            double sumWeek = sumSinceExcludingAllocated(expenses, startOfWeek);
            double sumMonth = sumSinceExcludingAllocated(expenses, startOfMonth);

            long now = System.currentTimeMillis();
            long lastNotifiedDaily = prefs.getLong(KEY_LAST_NOTIFIED_DAILY, 0L);
            long lastNotifiedWeekly = prefs.getLong(KEY_LAST_NOTIFIED_WEEKLY, 0L);
            long lastNotifiedMonthly = prefs.getLong(KEY_LAST_NOTIFIED_MONTHLY, 0L);

            boolean monthlyHit = sumMonth >= b.monthly * NEARLY_FULL;
            boolean weeklyHit = sumWeek >= b.weekly * NEARLY_FULL;
            boolean dailyHit = sumDay >= b.daily * NEARLY_FULL;

            int chosenPeriod = 0;
            String title = null;
            String text = null;
            double budgetValue = 0.0;
            double sumValue = 0.0;
            String periodLabel = "period";

            if (monthlyHit) {
                chosenPeriod = 3;
                budgetValue = b.monthly;
                sumValue = sumMonth;
                periodLabel = "monthly";
            } else if (weeklyHit) {
                chosenPeriod = 2;
                budgetValue = b.weekly;
                sumValue = sumWeek;
                periodLabel = "weekly";
            } else if (dailyHit) {
                chosenPeriod = 1;
                budgetValue = b.daily;
                sumValue = sumDay;
                periodLabel = "daily";
            } else {
                prefs.edit().putLong(KEY_LAST_PROCESSED_EXP_TS, maxExpenseTs).apply();
                checkAndSendReminders(ctx, prefs, expenses);
                return;
            }

            double pct = (budgetValue > 0.0) ? (sumValue / budgetValue * 100.0) : 100.0;
            text = String.format(Locale.getDefault(), "You spent %.1f%% of your %s budget (₱ %.2f)", pct, periodLabel, budgetValue);
            title = String.format(Locale.getDefault(), "%s budget alert", capitalize(periodLabel));

            boolean sent = false;
            switch (chosenPeriod) {
                case 3:
                    if (lastNotifiedMonthly < (now - NOTIF_COOLDOWN_MS)) {
                        sent = maybeSendNotification(ctx, title, text, NotificationUtils.NOTIF_ID_MONTHLY);
                        if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_MONTHLY, now).apply();
                    }
                    break;
                case 2:
                    if (lastNotifiedWeekly < (now - NOTIF_COOLDOWN_MS)) {
                        sent = maybeSendNotification(ctx, title, text, NotificationUtils.NOTIF_ID_WEEKLY);
                        if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_WEEKLY, now).apply();
                    }
                    break;
                case 1:
                    if (lastNotifiedDaily < (now - NOTIF_COOLDOWN_MS)) {
                        sent = maybeSendNotification(ctx, title, text, NotificationUtils.NOTIF_ID_DAILY);
                        if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_DAILY, now).apply();
                    }
                    break;
            }

            prefs.edit().putLong(KEY_LAST_PROCESSED_EXP_TS, maxExpenseTs).apply();
            checkAndSendReminders(ctx, prefs, expenses);
        } catch (Throwable t) {
            Log.w(TAG, "checkAndNotify failed", t);
        }
    }

    public static double getDailyBudgetFromPrefs(SharedPreferences prefs) {
        if (prefs == null) return 0.0;
        double amount = readBudgetAmount(prefs);
        String period = prefs.getString(KEY_BUDGET_PERIOD, prefs.getString("period", "monthly"));
        Budgets base = deriveBudgets(amount, period);
        double allocatedDeduction = sumAllocatedExpenses(prefs);
        double savingsMonthly = sumSavingsMonthly(prefs);
        double monthlyFromInput = base.monthly;
        double remainingMonthly = monthlyFromInput - allocatedDeduction - savingsMonthly;
        if (remainingMonthly < 0.0) remainingMonthly = 0.0;
        Budgets finalB = deriveBudgets(remainingMonthly, "monthly");
        return finalB.daily;
    }

    public static double sumAllocatedExpenses(SharedPreferences prefs) {
        if (prefs == null) return 0.0;
        double total = 0.0;
        try {
            String json = prefs.getString("expenses_json", "[]");
            if (json == null) json = "[]";
            Gson g = new Gson();
            Type listType = new TypeToken<List<Expense>>() {}.getType();
            List<Expense> list;
            try {
                list = g.fromJson(json, listType);
                if (list == null) list = new java.util.ArrayList<>();
            } catch (Exception ex) {
                list = new java.util.ArrayList<>();
            }
            for (Expense e : list) {
                if (e == null) continue;
                String cat = e.category != null ? e.category.toLowerCase(Locale.ROOT) : "";
                if ("bills".equals(cat) || "rent".equals(cat) || "gas".equals(cat) || "installments".equals(cat) || "planned expense".equals(cat)) {
                    try { total += e.amount; } catch (Throwable ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return total;
    }

    public static double sumSavingsMonthly(SharedPreferences prefs) {
        if (prefs == null) return 0.0;
        double total = 0.0;

        try {
            String json = prefs.getString("savings_json", "[]");
            if (json == null) json = "[]";

            Gson g = new Gson();
            Type listType = new TypeToken<List<Savings>>() {}.getType();
            List<Savings> list;
            try {
                list = g.fromJson(json, listType);
                if (list == null) list = new java.util.ArrayList<>();
            } catch (Exception ex) {
                list = new java.util.ArrayList<>();
            }

            int daysLeftMonth = daysLeftIncludingToday();
            int weekFactor = getWeekFactorByDay();

            for (Savings s : list) {
                if (s == null) continue;
                try {
                    String p = s.period != null ? s.period.toLowerCase(Locale.ROOT) : "monthly";
                    double monthly = 0.0;

                    switch (p) {
                        case "daily":
                            monthly = s.amount * daysLeftMonth;
                            break;
                        case "weekly":
                            monthly = (weekFactor > 0)
                                    ? (s.amount / (double) weekFactor) * daysLeftMonth
                                    : (s.amount / 7.0) * daysLeftMonth;
                            break;
                        case "monthly":
                        default:
                            monthly = s.amount;
                            break;
                    }

                    total += monthly;
                } catch (Throwable ignored) {}
            }
        } catch (Exception ignored) {}

        return total;
    }

    private static double sumSinceExcludingAllocated(List<Expense> expenses, long sinceMs) {
        if (expenses == null) return 0.0;
        double sum = 0.0;
        for (Expense ex : expenses) {
            try {
                long ts = ex.ts;
                String cat = ex.category != null ? ex.category.toLowerCase(Locale.ROOT) : "";
                boolean isAllocated = ("bills".equals(cat) || "rent".equals(cat) || "gas".equals(cat) || "installments".equals(cat) || "planned expense".equals(cat));
                if (ts >= sinceMs && !isAllocated) sum += ex.amount;
            } catch (Throwable ignored) {}
        }
        return sum;
    }

    private static void checkAndSendReminders(Context ctx, SharedPreferences prefs, List<Expense> expenses) {
        if (ctx == null || prefs == null || expenses == null) return;
        long startOfDay = getStartOfToday();
        long endOfDay = startOfDay + MS_DAY;
        for (Expense e : expenses) {
            if (e == null) continue;
            try {
                String cat = e.category != null ? e.category.toLowerCase(Locale.ROOT) : "";
                boolean isAllocated = ("bills".equals(cat) || "rent".equals(cat) || "gas".equals(cat) || "installments".equals(cat) || "planned expense".equals(cat));
                if (!isAllocated) continue;
                if (e.reminderTs == null) continue;
                long r = e.reminderTs;
                if (r >= startOfDay && r < endOfDay) {
                    String key = "allocated_reminder_notified_" + r + "_" + (long)e.amount;
                    boolean already = prefs.getBoolean(key, false);
                    if (already) continue;
                    String title = "Planned expense today";
                    String body = String.format(Locale.getDefault(), "Reminder: %s of ₱ %.2f is scheduled for today", e.desc != null ? e.desc : "Planned expense", e.amount);
                    boolean sent = maybeSendNotification(ctx, title, body, (int)(System.currentTimeMillis() & 0x7fffffff));
                    if (sent) prefs.edit().putBoolean(key, true).apply();
                }
            } catch (Throwable ignored) {}
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private static double readBudgetAmount(SharedPreferences prefs) {
        if (prefs == null) return 0.0;
        try {
            try {
                String s = prefs.getString(KEY_BUDGET_AMOUNT, null);
                if (s != null) return Double.parseDouble(s);
            } catch (Exception ignored) { }
            try {
                float f = prefs.getFloat(KEY_BUDGET_AMOUNT, Float.NaN);
                if (!Float.isNaN(f)) return f;
            } catch (Exception ignored) { }
            try {
                long bits = prefs.getLong(KEY_BUDGET_AMOUNT, Long.MIN_VALUE);
                if (bits != Long.MIN_VALUE) return Double.longBitsToDouble(bits);
            } catch (Exception ignored) { }
        } catch (Exception ex) {
            Log.w(TAG, "readBudgetAmount fallback failed", ex);
        }
        return 0.0;
    }

    private static Budgets deriveBudgets(double amount, String period) {
        int daysLeftInMonth = daysLeftIncludingToday();
        if (daysLeftInMonth <= 0) daysLeftInMonth = 30;

        int weekFactor = getWeekFactorByDay();

        double daily, weekly, monthly;
        period = (period != null) ? period.toLowerCase(Locale.ROOT) : "monthly";

        switch (period) {
            case "daily":
                daily = amount;
                weekly = daily * weekFactor;
                monthly = daily * daysLeftInMonth;
                break;

            case "weekly":
                weekly = amount;
                daily = (weekFactor > 0) ? (weekly / (double) weekFactor) : (weekly / 7.0);
                monthly = daily * daysLeftInMonth;
                break;

            case "monthly":
            default:
                monthly = amount;
                daily = (daysLeftInMonth > 0) ? (monthly / (double) daysLeftInMonth) : (monthly / 30.0);
                weekly = daily * weekFactor;
                break;
        }

        return new Budgets(daily, weekly, monthly);
    }

    private static long getStartOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long getStartOfThisWeek() {
        Calendar c = Calendar.getInstance();
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long getStartOfThisMonth() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static int daysLeftIncludingToday() {
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_MONTH);
        int maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        return Math.max(1, maxDay - today + 1);
    }

    private static boolean maybeSendNotification(Context ctx, String title, String text, int notifId) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            NotificationUtils.sendNotification(ctx, title, text, notifId);
            return true;
        } catch (SecurityException se) {
            Log.w(TAG, "Notification security exception", se);
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "Notification failed", t);
            return false;
        }
    }

    private static class Budgets {
        final double daily, weekly, monthly;
        Budgets(double d, double w, double m) { daily = d; weekly = w; monthly = m; }
    }

    private static int getWeekFactorByDay() {
        Calendar c = Calendar.getInstance();
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);

        int daysUntilSunday = Calendar.SUNDAY - dayOfWeek;
        if (daysUntilSunday < 0) daysUntilSunday += 7;

        return daysUntilSunday + 1;
    }
}

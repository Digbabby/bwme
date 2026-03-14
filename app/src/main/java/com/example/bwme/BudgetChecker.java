package com.example.bwme;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class BudgetChecker {
    private static final String TAG = "BudgetChecker";
    public static final String PREFS = MainActivity.PREFS;
    private static final String KEY_BUDGET_AMOUNT = "budget_amount";
    private static final String KEY_BUDGET_PERIOD = "budget_period";
    private static final String KEY_LAST_NOTIFIED_DAILY = "last_notified_daily";
    private static final String KEY_LAST_NOTIFIED_WEEKLY = "last_notified_weekly";
    private static final String KEY_LAST_NOTIFIED_MONTHLY = "last_notified_monthly";
    private static final String KEY_LAST_PROCESSED_EXP_TS = "last_processed_exp_ts";
    private static final boolean DEBUG = true;
    private static final double NEARLY_FULL = 0.75;
    private static final long NOTIF_COOLDOWN_MS = 0L;

    public static void checkAndNotify(Context ctx, List<Expense> expenses) {
        if (ctx == null) {
            Log.w(TAG, "context is null; aborting check");
            return;
        }

        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            double profileBudget = readBudgetAmount(prefs);
            String profilePeriod = prefs.getString(KEY_BUDGET_PERIOD, "monthly");

            if (DEBUG) Log.d(TAG, "Profile budget read: amount=" + profileBudget + ", period=" + profilePeriod);

            if (profileBudget <= 0.0) {
                if (DEBUG) Log.d(TAG, "No profile budget configured (<=0). Skipping notifications.");
                return;
            }

            long maxExpenseTs = 0L;
            if (expenses != null) {
                for (Expense ex : expenses) {
                    if (ex == null) continue;
                    long ts = ex.ts;
                    if (ts > maxExpenseTs) maxExpenseTs = ts;
                }
            }

            long lastProcessedTs = prefs.getLong(KEY_LAST_PROCESSED_EXP_TS, 0L);
            if (DEBUG) Log.d(TAG, "Max expense ts=" + maxExpenseTs + " lastProcessedTs=" + lastProcessedTs);

            if (maxExpenseTs <= lastProcessedTs) {
                if (DEBUG) Log.d(TAG, "No new expenses since last processed. Skipping notifications.");
                return;
            }

            Budgets b = deriveBudgets(profileBudget, profilePeriod);
            if (DEBUG) {
                Log.d(TAG, String.format(Locale.getDefault(),
                        "Derived budgets: daily=%.4f weekly=%.4f monthly=%.4f", b.daily, b.weekly, b.monthly));
            }

            long startOfDay = getStartOfToday();
            long startOfWeek = getStartOfThisWeek();
            long startOfMonth = getStartOfThisMonth();

            double sumDay = sumSince(expenses, startOfDay);
            double sumWeek = sumSince(expenses, startOfWeek);
            double sumMonth = sumSince(expenses, startOfMonth);

            if (DEBUG) {
                Log.d(TAG, String.format(Locale.getDefault(),
                        "Sums: day=%.4f week=%.4f month=%.4f", sumDay, sumWeek, sumMonth));
            }

            long now = System.currentTimeMillis();
            long lastNotifiedDaily = prefs.getLong(KEY_LAST_NOTIFIED_DAILY, 0L);
            long lastNotifiedWeekly = prefs.getLong(KEY_LAST_NOTIFIED_WEEKLY, 0L);
            long lastNotifiedMonthly = prefs.getLong(KEY_LAST_NOTIFIED_MONTHLY, 0L);

            boolean monthlyHit = sumMonth >= b.monthly * NEARLY_FULL;
            boolean weeklyHit = sumWeek >= b.weekly * NEARLY_FULL;
            boolean dailyHit = sumDay >= b.daily * NEARLY_FULL;

            if (DEBUG) {
                Log.d(TAG, "Thresholds: monthly=" + monthlyHit + " weekly=" + weeklyHit + " daily=" + dailyHit);
            }

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
                if (DEBUG) Log.d(TAG, "No thresholds hit right now.");
                prefs.edit().putLong(KEY_LAST_PROCESSED_EXP_TS, maxExpenseTs).apply();
                return;
            }

            double pct = (budgetValue > 0.0) ? (sumValue / budgetValue * 100.0) : 100.0;
            text = String.format(Locale.getDefault(), "You spent %.1f%% of your %s budget (₱ %.2f)",
                    pct, periodLabel, budgetValue);
            title = String.format(Locale.getDefault(), "%s budget alert", capitalize(periodLabel));

            boolean suppressedByCooldown = false;
            boolean sent = false;
            switch (chosenPeriod) {
                case 3:
                    if (lastNotifiedMonthly >= (now - NOTIF_COOLDOWN_MS)) {
                        suppressedByCooldown = true;
                        if (DEBUG) Log.d(TAG, "Monthly notification suppressed by cooldown.");
                    } else {
                        sent = maybeSendNotification(ctx, title, text, NotificationUtils.NOTIF_ID_MONTHLY);
                        if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_MONTHLY, now).apply();
                    }
                    break;
                case 2:
                    if (lastNotifiedWeekly >= (now - NOTIF_COOLDOWN_MS)) {
                        suppressedByCooldown = true;
                        if (DEBUG) Log.d(TAG, "Weekly notification suppressed by cooldown.");
                    } else {
                        sent = maybeSendNotification(ctx, title, text, NotificationUtils.NOTIF_ID_WEEKLY);
                        if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_WEEKLY, now).apply();
                    }
                    break;
                case 1:
                    if (lastNotifiedDaily >= (now - NOTIF_COOLDOWN_MS)) {
                        suppressedByCooldown = true;
                        if (DEBUG) Log.d(TAG, "Daily notification suppressed by cooldown.");
                    } else {
                        sent = maybeSendNotification(ctx, title, text, NotificationUtils.NOTIF_ID_DAILY);
                        if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_DAILY, now).apply();
                    }
                    break;
            }

            prefs.edit().putLong(KEY_LAST_PROCESSED_EXP_TS, maxExpenseTs).apply();

            if (DEBUG) {
                Log.d(TAG, String.format(Locale.getDefault(),
                        "Notification result: chosen=%d sent=%b suppressedByCooldown=%b text=%s", chosenPeriod, sent, suppressedByCooldown, text));
            }

        } catch (Throwable t) {
            Log.w(TAG, "checkAndNotify failed", t);
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
        int daysLeftInWeek = daysLeftInWeekIncludingToday();
        double daily, weekly, monthly;
        period = (period != null) ? period.toLowerCase(Locale.ROOT) : "monthly";

        switch (period) {
            case "daily":
                daily = amount;
                weekly = daily * daysLeftInWeek;
                monthly = daily * daysLeftInMonth;
                break;
            case "weekly":
                weekly = amount;
                daily = (daysLeftInWeek > 0) ? (weekly / (double) daysLeftInWeek) : (weekly / 7.0);
                monthly = daily * daysLeftInMonth;
                break;
            case "monthly":
            default:
                monthly = amount;
                daily = (daysLeftInMonth > 0) ? (monthly / (double) daysLeftInMonth) : (monthly / 30.0);
                weekly = daily * daysLeftInWeek;
                break;
        }
        return new Budgets(daily, weekly, monthly);
    }

    private static double sumSince(List<Expense> expenses, long sinceMs) {
        if (expenses == null) return 0.0;
        double sum = 0.0;
        for (Expense ex : expenses) {
            try {
                long ts = ex.ts;
                if (ts >= sinceMs) sum += ex.amount;
            } catch (Throwable ignored) {}
        }
        return sum;
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
        int first = c.getFirstDayOfWeek();
        while (c.get(Calendar.DAY_OF_WEEK) != first) {
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

    private static int daysLeftInWeekIncludingToday() {
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK);
        int daysUntilSunday = (Calendar.SUNDAY - today);
        if (daysUntilSunday < 0) daysUntilSunday += 7;
        return daysUntilSunday + 1;
    }

    private static boolean maybeSendNotification(Context ctx, String title, String text, int notifId) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "POST_NOTIFICATIONS not granted; skipping notification: " + title);
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
}
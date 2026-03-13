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
    private static final boolean DEBUG = true;
    private static final double NEARLY_FULL = 0.99;
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
                Log.d(TAG, "No profile budget configured (<=0). Skipping notifications.");
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

            long lastNotifiedDaily = prefs.getLong(KEY_LAST_NOTIFIED_DAILY, 0L);
            long lastNotifiedWeekly = prefs.getLong(KEY_LAST_NOTIFIED_WEEKLY, 0L);
            long lastNotifiedMonthly = prefs.getLong(KEY_LAST_NOTIFIED_MONTHLY, 0L);

            if (DEBUG) {
                Log.d(TAG, "Last notified timestamps (ms): daily=" + lastNotifiedDaily +
                        " weekly=" + lastNotifiedWeekly + " monthly=" + lastNotifiedMonthly);
            }

            if (sumDay >= b.daily * NEARLY_FULL) {
                if (lastNotifiedDaily >= startOfDay) {
                    if (DEBUG) Log.d(TAG, "Daily notification already sent today; skipping.");
                } else {
                    String title;
                    String text;
                    if (sumDay > b.daily) {
                        title = "Daily budget exceeded";
                        text = String.format(Locale.getDefault(), "You've spent %.2f today — daily budget %.2f", sumDay, b.daily);
                    } else {
                        title = "Daily budget near limit";
                        text = String.format(Locale.getDefault(), "You've used %.2f/%.2f today (%.1f%%)",
                                sumDay, b.daily, (b.daily > 0 ? (sumDay / b.daily * 100.0) : 100.0));
                    }

                    boolean sent = maybeSendNotification(ctx, title, text);
                    Log.d(TAG, "Daily check -> sum=" + sumDay + " budget=" + b.daily + " notified=" + sent);
                    if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_DAILY, System.currentTimeMillis()).apply();
                }
            } else {
                if (DEBUG) Log.d(TAG, "Daily not near/exceeded (sumDay=" + sumDay + " budget=" + b.daily + ")");
            }

            // WEEKLY CHECK
            if (sumWeek >= b.weekly * NEARLY_FULL) {
                if (lastNotifiedWeekly >= startOfWeek) {
                    if (DEBUG) Log.d(TAG, "Weekly notification already sent this week; skipping.");
                } else {
                    String title;
                    String text;
                    if (sumWeek > b.weekly) {
                        title = "Weekly budget exceeded";
                        text = String.format(Locale.getDefault(), "You've spent %.2f this week — weekly budget %.2f", sumWeek, b.weekly);
                    } else {
                        title = "Weekly budget near limit";
                        text = String.format(Locale.getDefault(), "You've used %.2f/%.2f this week (%.1f%%)",
                                sumWeek, b.weekly, (b.weekly > 0 ? (sumWeek / b.weekly * 100.0) : 100.0));
                    }
                    boolean sent = maybeSendNotification(ctx, title, text);
                    Log.d(TAG, "Weekly check -> sum=" + sumWeek + " budget=" + b.weekly + " notified=" + sent);
                    if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_WEEKLY, System.currentTimeMillis()).apply();
                }
            } else {
                if (DEBUG) Log.d(TAG, "Weekly not near/exceeded (sumWeek=" + sumWeek + " budget=" + b.weekly + ")");
            }

            if (sumMonth >= b.monthly * NEARLY_FULL) {
                if (lastNotifiedMonthly >= startOfMonth) {
                    if (DEBUG) Log.d(TAG, "Monthly notification already sent this month; skipping.");
                } else {
                    String title;
                    String text;
                    if (sumMonth > b.monthly) {
                        title = "Monthly budget exceeded";
                        text = String.format(Locale.getDefault(), "You've spent %.2f this month — monthly budget %.2f", sumMonth, b.monthly);
                    } else {
                        title = "Monthly budget near limit";
                        text = String.format(Locale.getDefault(), "You've used %.2f/%.2f this month (%.1f%%)",
                                sumMonth, b.monthly, (b.monthly > 0 ? (sumMonth / b.monthly * 100.0) : 100.0));
                    }
                    boolean sent = maybeSendNotification(ctx, title, text);
                    Log.d(TAG, "Monthly check -> sum=" + sumMonth + " budget=" + b.monthly + " notified=" + sent);
                    if (sent) prefs.edit().putLong(KEY_LAST_NOTIFIED_MONTHLY, System.currentTimeMillis()).apply();
                }
            } else {
                if (DEBUG) Log.d(TAG, "Monthly not near/exceeded (sumMonth=" + sumMonth + " budget=" + b.monthly + ")");
            }

        } catch (Throwable t) {
            Log.w(TAG, "checkAndNotify failed", t);
        }
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
        final double DAYS_PER_MONTH = 30.0;
        final double DAYS_PER_WEEK = 7.0;

        double daily, weekly, monthly;
        period = (period != null) ? period.toLowerCase(Locale.ROOT) : "monthly";

        switch (period) {
            case "daily":
                daily = amount;
                weekly = amount * DAYS_PER_WEEK;
                monthly = amount * DAYS_PER_MONTH;
                break;
            case "weekly":
                weekly = amount;
                daily = amount / DAYS_PER_WEEK;
                monthly = amount * (DAYS_PER_MONTH / DAYS_PER_WEEK);
                break;
            case "monthly":
            default:
                monthly = amount;
                daily = amount / DAYS_PER_MONTH;
                weekly = amount * (DAYS_PER_WEEK / DAYS_PER_MONTH);
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

    private static boolean maybeSendNotification(Context ctx, String title, String text) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "POST_NOTIFICATIONS not granted; skipping notification: " + title);
                    return false;
                }
            }
            NotificationUtils.sendNotification(ctx, title, text);
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
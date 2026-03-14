package com.example.bwme;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private RecyclerView recycler;
    private ExpenseAdapter adapter;
    private TextView totalTv;
    private TextView percentTv;
    private ProgressBar dailyProgress;
    private final Gson gson = new Gson();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.homeRecycler);
        totalTv = view.findViewById(R.id.homeTotal);
        percentTv = view.findViewById(R.id.homePercent);
        dailyProgress = view.findViewById(R.id.homeDailyProgress);

        adapter = new ExpenseAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        loadAndShowExpenses();

        getParentFragmentManager().setFragmentResultListener("expenses_changed", getViewLifecycleOwner(),
                (requestKey, result) -> loadAndShowExpenses());
    }

    private void loadAndShowExpenses() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(MainActivity.PREFS, android.content.Context.MODE_PRIVATE);
        String json = prefs.getString("expenses_json", "[]");
        if (json == null) json = "[]";
        Type type = new TypeToken<List<Expense>>(){}.getType();
        List<Expense> list;
        try {
            list = gson.fromJson(json, type);
            if (list == null) list = new ArrayList<>();
        } catch (Exception ex) {
            list = new ArrayList<>();
        }

        adapter.setItems(list);

        long startOfToday = getStartOfToday();
        double totalToday = 0.0;
        for (Expense e : list) {
            if (e == null) continue;
            try {
                if (e.ts >= startOfToday) totalToday += e.amount;
            } catch (Throwable ignored) {}
        }

        double totalAll = 0.0;
        for (Expense e : list) {
            if (e == null) continue;
            try { totalAll += e.amount; } catch (Throwable ignored) {}
        }

        if (totalTv != null) {
            try {
                totalTv.setText(String.format(Locale.getDefault(), "Total: ₱ %.2f", totalAll));
            } catch (Throwable ignored) {}
        }

        double dailyBudget = deriveDailyBudgetFromPrefs(prefs);

        double pct = 0.0;
        if (dailyBudget > 0.0) pct = (totalToday / dailyBudget) * 100.0;
        int pctInt = (int) Math.round(pct);

        if (percentTv != null) {
            try {
                percentTv.setText(String.format(Locale.getDefault(), "Daily Budget Used : %d%%", pctInt));
            } catch (Throwable ignored) {}
        }

        if (dailyProgress != null) {
            try {
                int progress = pctInt;
                if (progress < 0) progress = 0;
                if (progress > 100) progress = 100;
                dailyProgress.setProgress(progress);
            } catch (Throwable ignored) {}
        }
    }

    private long getStartOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private double deriveDailyBudgetFromPrefs(SharedPreferences prefs) {
        if (prefs == null) return 0.0;
        double allocated = 0.0;
        try {
            String s = prefs.getString("budget_amount", null);
            if (s != null) {
                allocated = Double.parseDouble(s);
            } else {
                String alt = prefs.getString("allocation_allocated", null);
                if (alt != null) allocated = Double.parseDouble(alt);
            }
        } catch (Exception ignored) {}

        if (allocated <= 0.0) return 0.0;

        String period = prefs.getString("budget_period", prefs.getString("period", "Monthly"));
        if (period == null) period = "monthly";
        period = period.toLowerCase(Locale.ROOT);

        int daysLeftMonth = daysLeftIncludingToday();
        int daysLeftWeek = daysLeftInWeekIncludingToday();

        switch (period) {
            case "daily":
                return allocated;
            case "weekly":
                if (daysLeftWeek > 0) return (allocated / (double) daysLeftWeek);
                return allocated / 7.0;
            case "monthly":
            default:
                if (daysLeftMonth > 0) return (allocated / (double) daysLeftMonth);
                return allocated / 30.0;
        }
    }

    private int daysLeftIncludingToday() {
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_MONTH);
        int maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        return Math.max(1, maxDay - today + 1);
    }

    private int daysLeftInWeekIncludingToday() {
        Calendar now = Calendar.getInstance();
        int today = now.get(Calendar.DAY_OF_WEEK);
        int daysUntilSunday = (Calendar.SUNDAY - today);
        if (daysUntilSunday < 0) daysUntilSunday += 7;
        return daysUntilSunday + 1;
    }
}
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
                if (e.ts >= startOfToday) {
                    String cat = e.category != null ? e.category.toLowerCase(Locale.ROOT) : "";
                    if (!("bills".equals(cat) || "rent".equals(cat) || "gas".equals(cat) || "installments".equals(cat) || "planned expense".equals(cat))) {
                        totalToday += e.amount;
                    }
                }
            } catch (Throwable ignored) {}
        }
        double totalAll = 0.0;
        for (Expense e : list) {
            if (e == null) continue;
            try {
                String cat = e.category != null ? e.category.toLowerCase(Locale.ROOT) : "";
                if (!("bills".equals(cat) || "rent".equals(cat) || "gas".equals(cat) || "installments".equals(cat) || "planned expense".equals(cat))) {
                    totalAll += e.amount;
                }
            } catch (Throwable ignored) {}
        }
        if (totalTv != null) {
            try {
                totalTv.setText(String.format(Locale.getDefault(), "Total: ₱ %.2f", totalAll));
            } catch (Throwable ignored) {}
        }
        double dailyBudget = BudgetChecker.getDailyBudgetFromPrefs(prefs);
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
}
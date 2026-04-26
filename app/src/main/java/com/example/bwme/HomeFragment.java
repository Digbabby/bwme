package com.example.bwme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private RecyclerView recycler;
    private ExpenseAdapter adapter;
    private TextView totalTv;
    private TextView percentTv;
    private TextView topPlacesTv;
    private ProgressBar dailyProgress;
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.homeRecycler);
        totalTv = view.findViewById(R.id.homeTotal);
        percentTv = view.findViewById(R.id.homePercent);
        topPlacesTv = view.findViewById(R.id.topPlacesTv);
        dailyProgress = view.findViewById(R.id.homeDailyProgress);
        adapter = new ExpenseAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        loadAndShowExpenses();
        getParentFragmentManager().setFragmentResultListener("expenses_changed", getViewLifecycleOwner(),
                (requestKey, result) -> loadAndShowExpenses());
    }

    private void loadAndShowExpenses() {
        Context context = getContext();
        if (context == null) return;
        
        executor.execute(() -> {
            SharedPreferences prefs = MainActivity.getUserPrefs(context);
            String json = prefs.getString("expenses_json", "[]");
            if (json == null) json = "[]";
            Type type = new TypeToken<List<Expense>>(){}.getType();
            
            List<Expense> listResult;
            try {
                listResult = gson.fromJson(json, type);
                if (listResult == null) listResult = new ArrayList<>();
            } catch (Exception ex) {
                listResult = new ArrayList<>();
            }
            final List<Expense> list = listResult;

            final List<VisitedPlace> visitedPlaces = AppDatabase.getInstance(context).visitedPlaceDao().getAllPlaces();

            mainHandler.post(() -> {
                if (!isAdded()) return;
                adapter.setItems(list);

                updateTopPlaces(list, visitedPlaces);

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
            });
        });
    }

    private void updateTopPlaces(List<Expense> expenses, List<VisitedPlace> visitedPlaces) {
        if (topPlacesTv == null) return;
        
        Map<String, Double> placeTotals = new HashMap<>();
        for (Expense e : expenses) {
            String placeName = null;

            if (e.lat != null && e.lng != null) {
                placeName = findNearestPlaceName(e.lat, e.lng, visitedPlaces);
            }

            if ((placeName == null || placeName.isEmpty()) && e.desc != null && !e.desc.isEmpty() && !"Expense".equals(e.desc)) {
                placeName = e.desc;
            }

            if (placeName == null || placeName.isEmpty()) {
                placeName = "Other";
            }

            Double current = placeTotals.get(placeName);
            if (current == null) current = 0.0;
            placeTotals.put(placeName, current + e.amount);
        }

        List<Map.Entry<String, Double>> sortedPlaces = new ArrayList<>(placeTotals.entrySet());
        Collections.sort(sortedPlaces, (a, b) -> b.getValue().compareTo(a.getValue()));

        StringBuilder sb = new StringBuilder();
        int count = Math.min(5, sortedPlaces.size());
        for (int i = 0; i < 5; i++) {
            if (i < count) {
                Map.Entry<String, Double> entry = sortedPlaces.get(i);
                sb.append(String.format(Locale.getDefault(), "%d. %s (₱ %.2f)\n", (i + 1), entry.getKey(), entry.getValue()));
            } else {
                sb.append((i + 1)).append(". -\n");
            }
        }
        topPlacesTv.setText(sb.toString().trim());
    }

    private String findNearestPlaceName(double lat, double lng, List<VisitedPlace> visitedPlaces) {
        VisitedPlace nearest = null;
        double minDistance = 0.001; // 100 meters
        
        for (VisitedPlace vp : visitedPlaces) {
            double dLat = lat - vp.latitude;
            double dLng = lng - vp.longitude;
            double dist = Math.sqrt(dLat * dLat + dLng * dLng);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = vp;
            }
        }
        return (nearest != null) ? nearest.category : null;
    }

    private long getStartOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

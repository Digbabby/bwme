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
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
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
        adapter.setOnExpenseDeleteListener(this::deleteExpense);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        loadAndShowExpenses();
        getParentFragmentManager().setFragmentResultListener("expenses_changed", getViewLifecycleOwner(),
                (requestKey, result) -> loadAndShowExpenses());
    }

    private void deleteExpense(Expense expense) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Expense")
                .setMessage("Are you sure you want to delete this expense record?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    executor.execute(() -> {
                        Context ctx = getContext();
                        if (ctx == null) return;
                        SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
                        String json = prefs.getString("expenses_json", "[]");
                        Type type = new TypeToken<List<Expense>>(){}.getType();
                        List<Expense> expenses = gson.fromJson(json, type);
                        
                        if (expenses != null) {
                            boolean removed = false;
                            for (int i = 0; i < expenses.size(); i++) {
                                Expense e = expenses.get(i);
                                if (e.ts == expense.ts && Math.abs(e.amount - expense.amount) < 0.01) {
                                    expenses.remove(i);
                                    removed = true;
                                    break;
                                }
                            }
                            if (removed) {
                                // Delete associated visited place
                                AppDatabase.getInstance(ctx).visitedPlaceDao().deleteByTimestamp(expense.ts);

                                prefs.edit().putString("expenses_json", gson.toJson(expenses)).apply();
                                mainHandler.post(() -> {
                                    getParentFragmentManager().setFragmentResult("expenses_changed", new Bundle());
                                    Toast.makeText(ctx, "Expense and location deleted", Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadAndShowExpenses() {
        Context context = getContext();
        if (context == null) return;
        
        // Capture username for user-specific database instance
        SharedPreferences session = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        final String loggedInUser = session.getString("username", "");

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
            
            // Use the correct user-specific database instance
            final List<VisitedPlace> visitedPlaces = AppDatabase.getInstance(context, loggedInUser).visitedPlaceDao().getAllPlaces();

            // Perform heavy calculations in background
            final String topPlacesResult = calculateTopPlacesText(list, visitedPlaces);
            
            long startOfToday = getStartOfToday();
            double totalToday = 0.0;
            double totalAll = 0.0;
            
            for (Expense e : list) {
                if (e == null) continue;
                String cat = e.category != null ? e.category.toLowerCase(Locale.ROOT) : "";
                boolean isRecurring = ("bills".equals(cat) || "rent".equals(cat) || "gas".equals(cat) || "installments".equals(cat) || "planned expense".equals(cat));
                
                if (!isRecurring) {
                    totalAll += e.amount;
                    if (e.ts >= startOfToday) {
                        totalToday += e.amount;
                    }
                }
            }

            double dailyBudget = BudgetChecker.getDailyBudgetFromPrefs(prefs);
            double pct = (dailyBudget > 0.0) ? (totalToday / dailyBudget) * 100.0 : 0.0;
            final int pctInt = (int) Math.round(pct);
            final double finalTotalAll = totalAll;

            mainHandler.post(() -> {
                if (!isAdded()) return;
                adapter.setItems(list);
                
                if (topPlacesTv != null) topPlacesTv.setText(topPlacesResult);
                if (totalTv != null) totalTv.setText(String.format(Locale.getDefault(), "Total: ₱ %.2f", finalTotalAll));
                if (percentTv != null) percentTv.setText(String.format(Locale.getDefault(), "Daily Budget Used : %d%%", pctInt));
                if (dailyProgress != null) {
                    int progress = Math.max(0, Math.min(100, pctInt));
                    dailyProgress.setProgress(progress);
                }
            });
        });
    }

    private String calculateTopPlacesText(List<Expense> expenses, List<VisitedPlace> visitedPlaces) {
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
            placeTotals.put(placeName, (current != null ? current : 0.0) + e.amount);
        }

        List<Map.Entry<String, Double>> sortedPlaces = new ArrayList<>(placeTotals.entrySet());
        Collections.sort(sortedPlaces, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i < sortedPlaces.size()) {
                Map.Entry<String, Double> entry = sortedPlaces.get(i);
                sb.append(String.format(Locale.getDefault(), "%d. %s (₱ %.2f)\n", (i + 1), entry.getKey(), entry.getValue()));
            } else {
                sb.append((i + 1)).append(". -\n");
            }
        }
        return sb.toString().trim();
    }

    private String findNearestPlaceName(double lat, double lng, List<VisitedPlace> visitedPlaces) {
        VisitedPlace nearest = null;
        double minDistance = 0.001; // Approx 100 meters
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

package com.example.bwme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExpensesFragment extends Fragment {

    private final Gson gson = new Gson();
    private RecyclerView rv;
    private TextView totalText;
    private TextView percentText;
    private Button addBtn;
    private ExpenseAdapter adapter;
    private List<Expense> expenses = new ArrayList<>();
    private double allocation = 0.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_expenses, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rv = view.findViewById(R.id.expenseRecycler);
        totalText = view.findViewById(R.id.totalText);
        percentText = view.findViewById(R.id.percentText);
        addBtn = view.findViewById(R.id.openAddBtn);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ExpenseAdapter(new ArrayList<Expense>());
        adapter.setOnExpenseDeleteListener(new ExpenseAdapter.OnExpenseDeleteListener() {
            @Override
            public void onDelete(Expense expense) {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete Expense")
                        .setMessage("Are you sure you want to remove this expense record?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            // Find and remove by matching properties for robustness
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
                                saveExpenses();
                                // Delete associated visited place
                                new Thread(() -> {
                                    Context ctx = getContext();
                                    if (ctx != null) {
                                        AppDatabase.getInstance(ctx).visitedPlaceDao().deleteByTimestamp(expense.ts);
                                    }
                                }).start();
                                
                                adapter.updateItems(new ArrayList<>(expenses));
                                updateTotals();
                                getParentFragmentManager().setFragmentResult("expenses_changed", new Bundle());
                                Toast.makeText(getContext(), "Expense and location deleted", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        rv.setAdapter(adapter);
        loadState();
        updateTotals();
        getParentFragmentManager().setFragmentResultListener("expenses_changed", getViewLifecycleOwner(),
                (requestKey, result) -> {
                    loadState();
                    updateTotals();
                });
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AddExpenseDialogFragment dlg = new AddExpenseDialogFragment();
                dlg.show(requireActivity().getSupportFragmentManager(), "add_expense_dialog");
            }
        });
    }

    private void loadState() {
        try {
            Context ctx = requireContext();
            SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
            String expJson = prefs.getString("expenses_json", "[]");
            if (expJson == null) expJson = "[]";
            Type type = new TypeToken<ArrayList<Expense>>() {}.getType();
            List<Expense> list;
            try {
                list = gson.fromJson(expJson, type);
                if (list == null) list = new ArrayList<>();
            } catch (Exception ex) {
                list = new ArrayList<>();
            }
            expenses.clear();
            expenses.addAll(list);
            adapter.updateItems(new ArrayList<>(expenses));
            String alloc = prefs.getString("allocation_allocated", null);
            if (alloc == null) alloc = prefs.getString("allocation", "0.0");
            try { allocation = Double.parseDouble(alloc); } catch (Exception e) { allocation = 0.0; }
        } catch (Exception e) {
            expenses = new ArrayList<>();
            adapter.updateItems(new ArrayList<>(expenses));
            allocation = 0.0;
        }
    }

    private void saveExpenses() {
        try {
            SharedPreferences prefs = MainActivity.getUserPrefs(requireContext());
            String json = gson.toJson(expenses);
            prefs.edit().putString("expenses_json", json).apply();
        } catch (Exception ignored) {}
    }

    private void updateTotals() {
        double total = 0.0;
        for (Expense e : expenses) {
            if (e == null) continue;
            try { total += e.amount; } catch (Throwable ignored) {}
        }
        if (totalText != null)
            totalText.setText("Total: ₱ " + String.format(Locale.getDefault(), "%.2f", total));
        int percent = 0;
        if (allocation > 0.0) {
            percent = (int) ((total / allocation) * 100.0);
        }
        if (percentText != null) percentText.setText("Used of allocation: " + percent + "%");
    }

    public void appendExpense(Expense e) {
        if (e == null) return;
        expenses.add(0, e);
        adapter.addExpense(e);
        saveExpenses();
        updateTotals();
    }
}

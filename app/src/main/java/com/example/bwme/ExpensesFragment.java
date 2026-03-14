package com.example.bwme;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Field;
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
    private static final String PREFS_NAME = MainActivity.PREFS;
    private Double pendingAmount = null;
    private String pendingDesc = null;
    private String pendingCategory = "Other";

    private ActivityResultLauncher<String> requestLocation;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestLocation = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        if (granted != null && granted) {
                            fetchLocationAndAddExpense();
                        } else {
                            addExpenseToList(null);
                        }
                    }
                });
    }

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
        rv.setAdapter(adapter);

        loadState();
        updateTotals();

        getParentFragmentManager().setFragmentResultListener("expenses_changed", this, (requestKey, result) -> {
            loadState();
            updateTotals();
        });

        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showAddDialog(); }
        });
    }

    private void loadState() {
        try {
            Context ctx = requireActivity();
            android.content.SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

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

            String alloc = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString("allocation_allocated", null);
            if (alloc == null) alloc = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString("allocation", "0.0");
            try { allocation = Double.parseDouble(alloc); } catch (Exception e) { allocation = 0.0; }
        } catch (Exception e) {
            expenses = new ArrayList<>();
            adapter.updateItems(new ArrayList<>(expenses));
            allocation = 0.0;
        }
    }

    private void saveExpenses() {
        try {
            android.content.SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = gson.toJson(expenses);
            prefs.edit().putString("expenses_json", json).apply();
        } catch (Exception ignored) {}
    }

    private void updateTotals() {
        double total = 0.0;
        for (Expense e : expenses) {
            total += getAmountSafe(e);
        }
        if (totalText != null)
            totalText.setText("Total: ₱ " + String.format(Locale.getDefault(), "%.2f", total));

        int percent = 0;
        if (allocation > 0.0) {
            percent = (int) ((total / allocation) * 100.0);
        }
        if (percentText != null) percentText.setText("Used of allocation: " + percent + "%");

        if (rv.getAdapter() != null) rv.getAdapter().notifyDataSetChanged();
    }

    private void showAddDialog() {
        android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(requireContext());
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_expense, null);
        final EditText amountEt = v.findViewById(R.id.dialogAmount);
        final EditText descEt = v.findViewById(R.id.dialogDesc);
        final Spinner categorySpinner = v.findViewById(R.id.dialogCategory);

        String[] categories = new String[] {"Food", "Transport", "Entertainment", "Utilities", "Other"};
        android.widget.ArrayAdapter<String> adapterSpinner = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, categories);
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (categorySpinner != null) {
            categorySpinner.setAdapter(adapterSpinner);
            categorySpinner.setSelection(4);
        }

        dialog.setView(v)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String amtS = amountEt.getText() != null ? amountEt.getText().toString() : "";
                        Double amt = null;
                        try { amt = Double.parseDouble(amtS); } catch (Exception ignored) {}
                        String desc = descEt.getText() != null ? descEt.getText().toString() : "";
                        if (desc.trim().isEmpty()) desc = "Expense";

                        String category = "Other";
                        if (categorySpinner != null && categorySpinner.getSelectedItem() != null) {
                            category = categorySpinner.getSelectedItem().toString();
                        }

                        if (amt == null || amt <= 0.0) {
                            Toast.makeText(requireContext(), "Enter valid amount", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        pendingAmount = amt;
                        pendingDesc = desc;
                        pendingCategory = category;

                        boolean hasLoc = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED;
                        if (!hasLoc) {
                            requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                        } else {
                            fetchLocationAndAddExpense();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void fetchLocationAndAddExpense() {
        final Double amt = pendingAmount;
        final String desc = pendingDesc != null ? pendingDesc : "Expense";

        if (amt == null) {
            pendingAmount = null;
            pendingDesc = null;
            pendingCategory = "Other";
            return;
        }

        try {
            LocationServices.getFusedLocationProviderClient(requireActivity())
                    .getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<android.location.Location>() {
                        @Override
                        public void onSuccess(android.location.Location loc) {
                            try {
                                if (loc != null) addExpenseToList(new Pair<>(loc.getLatitude(), loc.getLongitude()));
                                else addExpenseToList(null);
                            } finally {
                                pendingAmount = null;
                                pendingDesc = null;
                                pendingCategory = "Other";
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            addExpenseToList(null);
                            pendingAmount = null;
                            pendingDesc = null;
                            pendingCategory = "Other";
                        }
                    });
        } catch (Exception ex) {
            addExpenseToList(null);
            pendingAmount = null;
            pendingDesc = null;
            pendingCategory = "Other";
        }
    }

    private void addExpenseToList(@Nullable Pair<Double, Double> locationPair) {
        final Double amt = pendingAmount;
        if (amt == null) return;
        final String desc = pendingDesc != null ? pendingDesc : "Expense";
        final String category = pendingCategory != null ? pendingCategory : "Other";

        Expense e;
        if (locationPair != null) {
            e = new Expense(amt, desc, System.currentTimeMillis(), locationPair.first, locationPair.second, category);
        } else {
            e = new Expense(amt, desc, System.currentTimeMillis(), null, null, category);
        }

        expenses.add(0, e);
        adapter.addExpense(e);

        saveExpenses();
        updateTotals();

        try {
            Toast.makeText(requireContext(), "Saved expense: ₱ " + String.format(Locale.getDefault(), "%.2f", amt), Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}

        pendingAmount = null;
        pendingDesc = null;
        pendingCategory = "Other";
    }

    private double getAmountSafe(Expense e) {
        if (e == null) return 0.0;
        try {
            java.lang.reflect.Method m = e.getClass().getMethod("getAmount");
            Object res = m.invoke(e);
            if (res instanceof Number) return ((Number) res).doubleValue();
        } catch (NoSuchMethodException ignored) {
            try {
                Field f = e.getClass().getDeclaredField("amount");
                f.setAccessible(true);
                Object val = f.get(e);
                if (val instanceof Number) return ((Number) val).doubleValue();
            } catch (Exception ex) {
            }
        } catch (Exception ignored) {}

        return 0.0;
    }
}
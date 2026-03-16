package com.example.bwme;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

public class ProfileFragment extends Fragment {

    private EditText allocatedEt;
    private Spinner periodSpinner;
    private Spinner calculatedPeriodSpinner;
    private TextView calculatedValueTv;
    private Button saveBtn;
    private Button resetBtn;
    private Button logoutBtn;
    private Button allocatedExpensesBtn;
    private TextView allocatedExpensesTotalTv;
    private Button savingsBtn;
    private TextView savingsTotalTv;
    private Switch darkSwitch;
    private final String prefsName = MainActivity.PREFS;
    private final Gson gson = new Gson();

    public ProfileFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){
        allocatedEt = view.findViewById(R.id.profileMonthly);
        periodSpinner = view.findViewById(R.id.profilePeriodSpinner);
        calculatedPeriodSpinner = view.findViewById(R.id.profileCalculatedPeriodSpinner);
        calculatedValueTv = view.findViewById(R.id.profileAllocation);
        saveBtn = view.findViewById(R.id.profileSave);
        resetBtn = view.findViewById(R.id.profileReset);
        logoutBtn = view.findViewById(R.id.profileLogout);
        allocatedExpensesBtn = view.findViewById(R.id.profileAllocatedExpenses);
        allocatedExpensesTotalTv = view.findViewById(R.id.profileAllocatedTotal);
        savingsBtn = view.findViewById(R.id.profileSavings);
        savingsTotalTv = view.findViewById(R.id.profileSavingsTotal);
        darkSwitch = view.findViewById(R.id.switchDarkMode);

        String[] options = new String[] {"Daily", "Weekly", "Monthly"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(adapter);
        calculatedPeriodSpinner.setAdapter(adapter);

        final SharedPreferences prefs = requireActivity().getSharedPreferences(prefsName, Context.MODE_PRIVATE);

        String savedAllocated = prefs.getString("allocation_allocated", "");
        allocatedEt.setText(savedAllocated);

        String savedPeriod = prefs.getString("period", "Daily");
        int pos = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(savedPeriod)) { pos = i; break; }
        }
        periodSpinner.setSelection(pos);

        calculatedPeriodSpinner.setSelection(0);

        periodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) { recalcPreview(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        calculatedPeriodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) { recalcPreview(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        allocatedEt.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) recalcPreview(); });
        allocatedEt.addTextChangedListener(new SimpleTextWatcher(() -> recalcPreview()));

        saveBtn.setOnClickListener(v -> {
            Double allocated = parseDouble(allocatedEt.getText().toString());
            if (allocated == null || allocated <= 0.0) {
                Toast.makeText(requireContext(), "Enter a valid allocated allowance (>0).", Toast.LENGTH_SHORT).show();
                return;
            }
            String period = (String) periodSpinner.getSelectedItem();
            SharedPreferences.Editor e = prefs.edit();
            e.putString("allocation_allocated", allocated.toString());
            e.putString("period", period);
            e.putString("allocation", allocated.toString());
            e.putString("budget_amount", allocated.toString());
            e.putString("budget_period", period);
            e.apply();
            recalcPreview();
            Toast.makeText(requireContext(), "Saved allocation for " + period, Toast.LENGTH_SHORT).show();
            try {
                if (requireActivity() instanceof MainActivity) {
                    java.util.List<Expense> list = ((MainActivity) requireActivity()).getExpensesFromPrefs();
                    BudgetChecker.checkAndNotify(requireContext(), list);
                }
            } catch (Exception ignored) {}
        });

        resetBtn.setOnClickListener(v -> {
            prefs.edit().putString("expenses_json", "[]").apply();
            updateAllocatedExpensesTotal();
            updateSavingsTotal();
            recalcPreview();
            Toast.makeText(requireContext(), "Expenses cleared", Toast.LENGTH_SHORT).show();
        });

        logoutBtn.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
            b.setTitle("Logout");
            b.setMessage("Are you sure you want to logout?");
            b.setNegativeButton("Cancel", (d, w) -> {});
            b.setPositiveButton("Logout", (d, w) -> {
                Intent i = new Intent(requireActivity(), LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            });
            b.show();
        });

        allocatedExpensesBtn.setOnClickListener(v -> {
            AllocatedExpenseDialogFragment dlg = new AllocatedExpenseDialogFragment();
            dlg.show(requireActivity().getSupportFragmentManager(), "allocated_expense_dialog");
        });

        savingsBtn.setOnClickListener(v -> {
            AddSavingsDialogFragment dlg = new AddSavingsDialogFragment();
            dlg.show(requireActivity().getSupportFragmentManager(), "add_savings_dialog");
        });

        boolean darkEnabled = prefs.getBoolean(MainActivity.KEY_DARK, false);
        darkSwitch.setChecked(darkEnabled);
        darkSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(MainActivity.KEY_DARK, isChecked).apply();
            int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);
            requireActivity().recreate();
        });

        getParentFragmentManager().setFragmentResultListener("expenses_changed", getViewLifecycleOwner(),
                (requestKey, result) -> {
                    updateAllocatedExpensesTotal();
                    updateSavingsTotal();
                    recalcPreview();
                });

        updateAllocatedExpensesTotal();
        updateSavingsTotal();
        recalcPreview();
    }

    private void updateAllocatedExpensesTotal() {
        double total = BudgetChecker.sumAllocatedExpenses(requireActivity().getSharedPreferences(prefsName, Context.MODE_PRIVATE));
        if (allocatedExpensesTotalTv != null) {
            allocatedExpensesTotalTv.setText(String.format(Locale.getDefault(), "Allocated expenses total: ₱ %.2f", total));
        }
    }

    private void updateSavingsTotal() {
        double total = BudgetChecker.sumSavingsMonthly(requireActivity().getSharedPreferences(prefsName, Context.MODE_PRIVATE));
        if (savingsTotalTv != null) {
            savingsTotalTv.setText(String.format(Locale.getDefault(), "Savings (monthly equiv): ₱ %.2f", total));
        }
    }

    private void recalcPreview() {
        Double input = parseDouble(allocatedEt.getText().toString());
        if (input == null) {
            calculatedValueTv.setText("");
            return;
        }

        String inputPeriod = (String) (periodSpinner.getSelectedItem() != null ? periodSpinner.getSelectedItem() : "Daily");
        String showPeriod = (String) (calculatedPeriodSpinner.getSelectedItem() != null ? calculatedPeriodSpinner.getSelectedItem() : "Daily");

        int daysLeftMonth = daysLeftIncludingToday();
        int daysLeftWeek = daysLeftInWeekIncludingToday();

        double monthlyFromInput;
        switch (inputPeriod.toLowerCase(Locale.ROOT)) {
            case "daily":
                monthlyFromInput = input * daysLeftMonth;
                break;
            case "weekly":
                double dailyFromWeekly = (daysLeftWeek > 0) ? (input / (double) daysLeftWeek) : (input / 7.0);
                monthlyFromInput = dailyFromWeekly * daysLeftMonth;
                break;
            case "monthly":
            default:
                monthlyFromInput = input;
                break;
        }

        SharedPreferences prefs = requireActivity().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        double allocatedDeduction = BudgetChecker.sumAllocatedExpenses(prefs);
        double savingsMonthly = BudgetChecker.sumSavingsMonthly(prefs);
        double remainingMonthly = monthlyFromInput - allocatedDeduction - savingsMonthly;
        if (remainingMonthly < 0.0) remainingMonthly = 0.0;

        double daily = (daysLeftMonth > 0) ? (remainingMonthly / (double) daysLeftMonth) : (remainingMonthly / 30.0);
        double weekly = daily * daysLeftWeek;
        double monthly = remainingMonthly;

        String out;
        switch (showPeriod.toLowerCase(Locale.ROOT)) {
            case "weekly":
                out = String.format(Locale.getDefault(), "Weekly: ₱ %.2f", weekly);
                break;
            case "monthly":
                out = String.format(Locale.getDefault(), "Monthly: ₱ %.2f", monthly);
                break;
            case "daily":
            default:
                out = String.format(Locale.getDefault(), "Daily: ₱ %.2f", daily);
                break;
        }

        calculatedValueTv.setText(out);
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

    private Double parseDouble(String s) {
        try {
            if (s == null || s.trim().isEmpty()) return null;
            return Double.parseDouble(s.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
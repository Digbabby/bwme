package com.example.bwme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.Calendar;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private EditText allocatedEt;
    private Spinner periodSpinner;
    private Spinner calculatedPeriodSpinner;
    private TextView calculatedValueTv;
    private Button saveBtn;
    private Button resetBtn;
    private Switch darkSwitch;
    private final String prefsName = MainActivity.PREFS;

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
            Toast.makeText(requireContext(), "Expenses cleared", Toast.LENGTH_SHORT).show();
        });

        boolean darkEnabled = prefs.getBoolean(MainActivity.KEY_DARK, false);
        darkSwitch.setChecked(darkEnabled);
        darkSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(MainActivity.KEY_DARK, isChecked).apply();
            int mode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            AppCompatDelegate.setDefaultNightMode(mode);
            requireActivity().recreate();
        });

        recalcPreview();
    }

    private void recalcPreview() {
        Double allocated = parseDouble(allocatedEt.getText().toString());
        if (allocated == null) {
            calculatedValueTv.setText("");
            return;
        }
        String inputPeriod = (String) periodSpinner.getSelectedItem();
        String showPeriod = (String) calculatedPeriodSpinner.getSelectedItem();

        int daysLeftMonth = daysLeftIncludingToday();
        int daysLeftWeek = daysLeftInWeekIncludingToday();

        double daily, weekly, monthly;
        switch (inputPeriod.toLowerCase(Locale.ROOT)) {
            case "daily":
                daily = allocated;
                weekly = daily * daysLeftWeek;
                monthly = daily * daysLeftMonth;
                break;
            case "weekly":
                weekly = allocated;
                daily = (daysLeftWeek > 0) ? (weekly / (double) daysLeftWeek) : (weekly / 7.0);
                monthly = daily * daysLeftMonth;
                break;
            case "monthly":
            default:
                monthly = allocated;
                daily = (daysLeftMonth > 0) ? (monthly / (double) daysLeftMonth) : (monthly / 30.0);
                weekly = daily * daysLeftWeek;
                break;
        }

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
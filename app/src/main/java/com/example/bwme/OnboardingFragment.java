package com.example.bwme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.fragment.app.Fragment;

import java.util.Calendar;
import java.util.Locale;

public class OnboardingFragment extends Fragment {

    private EditText allocatedEt;
    private Spinner periodSpinner;
    private Spinner calculatedPeriodSpinner;
    private TextView calculatedValueTv;
    private Button continueBtn;
    private final String prefsName = MainActivity.PREFS;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        allocatedEt = view.findViewById(R.id.onboardingAllocated);
        periodSpinner = view.findViewById(R.id.onboardingPeriodSpinner);
        calculatedPeriodSpinner = view.findViewById(R.id.onboardingCalculatedPeriodSpinner);
        calculatedValueTv = view.findViewById(R.id.onboardingCalculatedValue);
        continueBtn = view.findViewById(R.id.onboardingContinueBtn);
        String[] options = new String[] {"Daily", "Weekly", "Monthly"};
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(requireContext(), R.layout.custom_spinner_item, options);
        periodAdapter.setDropDownViewResource(R.layout.custom_spinner_item);
        periodSpinner.setAdapter(periodAdapter);
        ArrayAdapter<String> calcAdapter = new ArrayAdapter<>(requireContext(), R.layout.custom_spinner_item, options);
        calcAdapter.setDropDownViewResource(R.layout.custom_spinner_item);
        calculatedPeriodSpinner.setAdapter(calcAdapter);
        periodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) { recalcPreview(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        calculatedPeriodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) { recalcPreview(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        allocatedEt.addTextChangedListener(new SimpleTextWatcher(() -> recalcPreview()));
        continueBtn.setOnClickListener(v -> {
            Double allocated = parseDouble(allocatedEt.getText().toString());
            if (allocated == null || allocated <= 0.0) {
                Toast.makeText(requireContext(), "Enter a valid allocated amount", Toast.LENGTH_SHORT).show();
                return;
            }
            String period = (String) periodSpinner.getSelectedItem();
            SharedPreferences prefs = requireActivity().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString("allocation_allocated", allocated.toString())
                    .putString("period", period)
                    .putString("allocation", allocated.toString())
                    .putBoolean("onboarding_finished", true)
                    .putString("budget_amount", allocated.toString())
                    .putString("budget_period", period)
                    .apply();
            try {
                if (requireActivity() instanceof MainActivity) {
                    java.util.List<Expense> list = ((MainActivity) requireActivity()).getExpensesFromPrefs();
                    BudgetChecker.checkAndNotify(requireContext(), list);
                }
            } catch (Exception ignored) {}
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
        });
        calculatedPeriodSpinner.setSelection(0);
        recalcPreview();
    }

    private void recalcPreview() {
        Double allocated = parseDouble(allocatedEt.getText().toString());
        if (allocated == null) {
            calculatedValueTv.setText("");
            return;
        }
        String inputPeriod = (String) (periodSpinner.getSelectedItem() != null ? periodSpinner.getSelectedItem() : "Daily");
        String showPeriod = (String) (calculatedPeriodSpinner.getSelectedItem() != null ? calculatedPeriodSpinner.getSelectedItem() : "Daily");
        int daysLeftMonth = daysLeftIncludingToday();
        int daysLeftWeek = daysLeftInWeekIncludingToday();
        double daily = 0.0, weekly = 0.0, monthly = 0.0;
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

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable onEmpty;
        public SimpleTextWatcher(Runnable onEmpty) { this.onEmpty = onEmpty; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(android.text.Editable s) { onEmpty.run(); }
    }
}
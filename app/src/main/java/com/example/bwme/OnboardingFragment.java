package com.example.bwme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
    private EditText previewDailyEt;
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
        previewDailyEt = view.findViewById(R.id.onboardingCalculatedDaily);
        continueBtn = view.findViewById(R.id.onboardingContinueBtn);

        String[] options = new String[] {"Daily", "Weekly", "Monthly"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        periodSpinner.setAdapter(adapter);

        periodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) { recalcPreview(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        allocatedEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { recalcPreview(); }
        });

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
                    .apply();

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
        });

        recalcPreview();
    }

    private void recalcPreview() {
        Double allocated = parseDouble(allocatedEt.getText().toString());
        if (allocated == null) {
            if (previewDailyEt != null) previewDailyEt.setText("");
            return;
        }
        String period = (String) (periodSpinner.getSelectedItem() != null ? periodSpinner.getSelectedItem() : "Daily");
        int daysLeft = daysLeftIncludingToday(period);
        double daily = (daysLeft <= 0) ? allocated : (allocated / daysLeft);
        if (previewDailyEt != null) previewDailyEt.setText(String.format(Locale.getDefault(), "%.2f", daily));
    }

    private Integer daysLeftIncludingToday(String period) {
        Calendar now = Calendar.getInstance();
        switch (period) {
            case "Daily": return 1;
            case "Weekly": {
                int today = now.get(Calendar.DAY_OF_WEEK);
                int daysUntilSunday = (Calendar.SUNDAY - today);
                if (daysUntilSunday < 0) daysUntilSunday += 7;
                return daysUntilSunday + 1;
            }
            case "Monthly": {
                int today = now.get(Calendar.DAY_OF_MONTH);
                int maxDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
                int remaining = Math.max(1, maxDay - today);
                return remaining;
            }
            default: return 1;
        }
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
package com.example.bwme;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
    private TextView profileNameTv;
    private ShapeableImageView profilepic;
    private Button changebtn;
    private final Gson gson = new Gson();
    private DatabaseHelper DB;
    private String loggedInUser;
    private String userPrefsName;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    public ProfileFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState){

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        profilepic = view.findViewById(R.id.profilepic);
        changebtn = view.findViewById(R.id.changebtn);
        profileNameTv = view.findViewById(R.id.profile_name);

        DB = new DatabaseHelper(requireContext());
        SharedPreferences sp = requireActivity().getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        loggedInUser = sp.getString("username", "");
        userPrefsName = MainActivity.PREFS + (loggedInUser.isEmpty() ? "" : "_" + loggedInUser);

        loadProfileData();

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            try {
                                requireContext().getContentResolver().takePersistableUriPermission(imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException ignored) {}
                            
                            String displayName = DB.getDisplayName(loggedInUser);
                            DB.updateProfile(loggedInUser, displayName, imageUri.toString());
                            profilepic.setImageURI(imageUri);
                        }
                    }
                }
        );

        changebtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        return view;
    }

    private void loadProfileData() {
        if (loggedInUser.isEmpty()) return;

        String picPath = DB.getProfilePic(loggedInUser);
        String displayName = DB.getDisplayName(loggedInUser);

        if (profileNameTv != null) {
            profileNameTv.setText(displayName != null && !displayName.isEmpty() ? displayName : loggedInUser);
        }

        if (picPath != null && !picPath.isEmpty() && !picPath.equals("default_pic")) {
            try {
                profilepic.setImageURI(Uri.parse(picPath));
            } catch (Exception e) {
                profilepic.setImageResource(R.drawable.profile_placeholder);
            }
        } else {
            profilepic.setImageResource(R.drawable.profile_placeholder);
        }
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

        final SharedPreferences prefs = requireActivity().getSharedPreferences(userPrefsName, Context.MODE_PRIVATE);

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
            new AlertDialog.Builder(requireContext())
                .setTitle("Reset All Data")
                .setMessage("This will permanently delete all your expenses and savings. Are you sure?")
                .setPositiveButton("Yes, Reset", (d, w) -> {
                    prefs.edit().putString("expenses_json", "[]").apply();
                    updateAllocatedExpensesTotal();
                    updateSavingsTotal();
                    recalcPreview();
                    Toast.makeText(requireContext(), "Data cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        double total = BudgetChecker.sumAllocatedExpenses(requireActivity().getSharedPreferences(userPrefsName, Context.MODE_PRIVATE));
        if (allocatedExpensesTotalTv != null) {
            allocatedExpensesTotalTv.setText(String.format(Locale.getDefault(), "Allocated expenses total: ₱ %.2f", total));
        }
    }

    private void updateSavingsTotal() {
        double total = BudgetChecker.sumSavingsMonthly(requireActivity().getSharedPreferences(userPrefsName, Context.MODE_PRIVATE));
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

        SharedPreferences prefs = requireActivity().getSharedPreferences(userPrefsName, Context.MODE_PRIVATE);
        double allocatedDeduction = BudgetChecker.sumAllocatedExpenses(prefs);
        double savingsMonthly = BudgetChecker.sumSavingsMonthly(prefs);
        double remainingMonthly = monthlyFromInput - allocatedDeduction - savingsMonthly;
        if (remainingMonthly < 0.0) remainingMonthly = 0.0;

        double daily = (daysLeftMonth > 0) ? (remainingMonthly / (double) daysLeftMonth) : (remainingMonthly / 30.0);
        double weekly = daily * daysLeftWeek;
        double monthly = remainingMonthly;

        String out;
        switch (showPeriod.toLowerCase(Locale.ROOT)) {
            case "daily":
                out = String.format(Locale.getDefault(), "Daily: ₱ %.2f", daily);
                break;
            case "weekly":
                out = String.format(Locale.getDefault(), "Weekly: ₱ %.2f", weekly);
                break;
            case "monthly":
            default:
                out = String.format(Locale.getDefault(), "Monthly: ₱ %.2f", monthly);
                break;
        }
        calculatedValueTv.setText(out);
    }

    private Double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (Exception e) { return null; }
    }

    private int daysLeftIncludingToday() {
        Calendar c = Calendar.getInstance();
        int today = c.get(Calendar.DAY_OF_MONTH);
        int last = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        return (last - today) + 1;
    }

    private int daysLeftInWeekIncludingToday() {
        Calendar c = Calendar.getInstance();
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
        int daysToSaturday = Calendar.SATURDAY - dayOfWeek;
        return daysToSaturday + 1;
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable onEmpty;
        public SimpleTextWatcher(Runnable onEmpty) { this.onEmpty = onEmpty; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(android.text.Editable s) { onEmpty.run(); }
    }
}

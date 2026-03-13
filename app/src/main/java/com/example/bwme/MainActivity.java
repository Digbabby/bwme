package com.example.bwme;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS = "bwme_prefs";
    public static final String KEY_DARK = "pref_dark";
    public static final String KEY_SELECTED_NAV = "pref_selected_nav";
    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabAdd;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private final Gson gson = new Gson();

    private ActivityResultLauncher<String> requestNotificationPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean dark = prefs.getBoolean(KEY_DARK, false);
        AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NotificationUtils.createNotificationChannel(this);

        requestNotificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        Log.d(TAG, "Notification permission granted");
                    } else {
                        Log.d(TAG, "Notification permission denied");
                    }
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        bottomNav = findViewById(R.id.bottomNav);
        fabAdd = findViewById(R.id.fabAdd);

        if (bottomNav != null) bottomNav.setItemIconTintList(null);

        final SharedPreferences finalPrefs = prefs;
        bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();
                Fragment frag = fragmentForNav(id);
                if (frag != null) {
                    openFragment(frag);
                    finalPrefs.edit().putInt(KEY_SELECTED_NAV, id).apply();
                    return true;
                } else {
                    return false;
                }
            }
        });

        fabAdd.setOnClickListener(v -> {
            Log.d(TAG, "FAB clicked");
            AddExpenseDialogFragment dlg = new AddExpenseDialogFragment();
            dlg.show(getSupportFragmentManager(), "add_expense_dialog");
        });

        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
                if ("expenses_json".equals(key)) {
                    final String json = sharedPreferences.getString("expenses_json", "[]");
                    int count = 0;
                    try {
                        Type type = new TypeToken<List<Expense>>() {}.getType();
                        List<Expense> list = gson.fromJson(json, type);
                        if (list != null) count = list.size();
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse expenses_json in prefs listener", e);
                    }

                    final int finalCount = count;

                    try {
                        Type type = new TypeToken<List<Expense>>() {}.getType();
                        List<Expense> list = gson.fromJson(json, type);
                        if (list != null) {
                            BudgetChecker.checkAndNotify(MainActivity.this, list);
                        }
                    } catch (Exception ex) {
                        Log.w(TAG, "Failed to run BudgetChecker from prefs listener", ex);
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Bundle bundle = new Bundle();
                            bundle.putInt("count", finalCount);
                            getSupportFragmentManager().setFragmentResult("expenses_changed", bundle);
                        }
                    });
                }
            }
        };

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        int selectedId = prefs.getInt(KEY_SELECTED_NAV, R.id.nav_home);
        bottomNav.setSelectedItemId(selectedId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (prefs != null && prefsListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
        }
    }

    private Fragment fragmentForNav(int itemId) {
        if (itemId == R.id.nav_home) return new HomeFragment();
        if (itemId == R.id.nav_stats) return new StatsFragment();
        if (itemId == R.id.nav_map) return new MapFragment();
        if (itemId == R.id.nav_profile) return new ProfileFragment();
        return null;
    }

    private void openFragment(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, f)
                .commit();
    }

    public List<Expense> getExpensesFromPrefs() {
        String json = prefs.getString("expenses_json", "[]");
        try {
            Type type = new TypeToken<List<Expense>>() {}.getType();
            List<Expense> list = gson.fromJson(json, type);
            if (list == null) return new java.util.ArrayList<>();
            return list;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse expenses_json in getExpensesFromPrefs", e);
            return new java.util.ArrayList<>();
        }
    }

    private void runQuickBudgetDebug() {
        Log.d(TAG, "Running quick budget debug: setting budget_amount=10.0 (daily), clearing last-notified keys");
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit()
                .putString("budget_amount", "10.0")
                .putString("budget_period", "daily")
                .remove("last_notified_daily")
                .remove("last_notified_weekly")
                .remove("last_notified_monthly")
                .apply();

        NotificationUtils.sendNotification(this, "DEBUG test", "Budget debug: 10.0 daily set and cleared flags");
    }
}
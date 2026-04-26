package com.example.bwme;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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
    private DatabaseHelper DB;

    private ActivityResultLauncher<String> requestNotificationPermission;
    private ActivityResultLauncher<String> pickImageLauncher;
    private String tempUsername;
    private String tempDisplayName;

    public static SharedPreferences getUserPrefs(Context context) {
        SharedPreferences session = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        String loggedInUser = session.getString("username", "");
        String userPrefsName = PREFS + (loggedInUser.isEmpty() ? "" : "_" + loggedInUser);
        return context.getSharedPreferences(userPrefsName, Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getUserPrefs(this);
        SharedPreferences session = getSharedPreferences("UserSession", MODE_PRIVATE);
        String loggedInUser = session.getString("username", "");

        boolean dark = prefs.getBoolean(KEY_DARK, false);
        AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DB = new DatabaseHelper(this);
        NotificationUtils.createNotificationChannel(this);

        Cursor cursor = DB.getExpensesByUser(loggedInUser);

        if (cursor.getCount() == 0) {
            Toast.makeText(this, "No expenses found", Toast.LENGTH_SHORT).show();
        } else {
            while (cursor.moveToNext()) {
                String amount = cursor.getString(2);
                String category = cursor.getString(3);
            }
        }

        if (!loggedInUser.isEmpty() && DB.checkUsername(loggedInUser) && !DB.isProfileNameSet(loggedInUser)) {
            showSetupDialog(loggedInUser);
        }

        requestNotificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        Log.d(TAG, "Notification permission granted");
                    } else {
                        Log.d(TAG, "Notification permission denied");
                    }
                });

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        DB.updateProfile(tempUsername, tempDisplayName, uri.toString());
                        Toast.makeText(this, "Profile Saved with Image!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Profile Saved with default picture", Toast.LENGTH_SHORT).show();
                    }
                    // After picking image (or cancelling), go to Profile
                    navigateToProfile();
                }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        bottomNav = findViewById(R.id.bottomNav);
        fabAdd = findViewById(R.id.fabAdd);
        if (bottomNav != null) bottomNav.setItemIconTintList(null);

        bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                int id = item.getItemId();
                Fragment frag = fragmentForNav(id);
                if (frag != null) {
                    openFragment(frag);
                    prefs.edit().putInt(KEY_SELECTED_NAV, id).apply();
                    return true;
                } else {
                    return false;
                }
            }
        });

        fabAdd.setOnClickListener(v -> {
            AddExpenseDialogFragment dlg = new AddExpenseDialogFragment();
            dlg.show(getSupportFragmentManager(), "add_expense_dialog");
        });

        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
                if ("expenses_json".equals(key)) {
                    final String json = sharedPreferences.getString("expenses_json", "[]");
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
                            int count = 0;
                            try {
                                Type type = new TypeToken<List<Expense>>() {}.getType();
                                List<Expense> list = gson.fromJson(json, type);
                                if (list != null) count = list.size();
                            } catch (Exception e) {
                            }
                            Bundle bundle = new Bundle();
                            bundle.putInt("count", count);
                            getSupportFragmentManager().setFragmentResult("expenses_changed", bundle);
                        }
                    });
                }
            }
        };

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        int selectedId = prefs.getInt(KEY_SELECTED_NAV, R.id.nav_home);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(selectedId);
        }
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

    private void navigateToProfile() {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_profile);
        }
    }

    public List<Expense> getExpensesFromPrefs() {
        String json = prefs.getString("expenses_json", "[]");
        try {
            Type type = new TypeToken<List<Expense>>() {}.getType();
            List<Expense> list = gson.fromJson(json, type);
            if (list == null) return new java.util.ArrayList<>();
            return list;
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    private boolean isProfileIncomplete(String username) {
        Cursor cursor = DB.getReadableDatabase().rawQuery("Select displayName from users where username = ?", new String[]{username});
        if (cursor.moveToFirst()) {
            String name = cursor.getString(0);
            return name == null || name.isEmpty();
        }
        return true;
    }

    private void showSetupDialog(String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Complete Your Profile");
        builder.setCancelable(false);

        final EditText input = new EditText(this);
        input.setHint("Enter your Display Name");
        builder.setView(input);

        builder.setPositiveButton("Next", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                tempUsername = username;
                tempDisplayName = name;
                DB.updateProfile(username, name, "default_pic");
                showImagePickDialog();
            } else {
                Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
                showSetupDialog(username);
            }
        });

        builder.show();
    }

    private void showImagePickDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Profile Picture");
        builder.setMessage("Would you like to set a profile picture?");
        builder.setCancelable(false);

        builder.setPositiveButton("Pick Image", (dialog, which) -> {
            pickImageLauncher.launch("image/*");
        });

        builder.setNegativeButton("Skip", (dialog, which) -> {
            Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show();
            navigateToProfile();
        });

        builder.show();
    }
}

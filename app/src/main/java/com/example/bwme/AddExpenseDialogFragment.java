package com.example.bwme;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.Exception;
import java.lang.SecurityException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddExpenseDialogFragment extends DialogFragment {
    private static final String TAG = "AddExpense";
    private final Gson gson = new Gson();
    private double pendingAmount = 0.0;
    private String pendingDesc = "Expense";
    private String pendingCategory = "Other";
    private String pendingLocationName = "";
    private Context appContext = null;
    private ActivityResultLauncher<String> requestLocation;
    private EditText amountEt;
    private EditText descEt;
    private EditText locationNameEt;
    private Spinner categorySpinner;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestLocation = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                new ActivityResultCallback<Boolean>() {
                    @Override
                    public void onActivityResult(Boolean granted) {
                        if (granted != null && granted) {
                            fetchLocationAndSave();
                        } else {
                            saveExpense(null, appContext);
                        }
                    }
                }
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Context ctx = requireContext();
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_expense, null);
        amountEt = v.findViewById(R.id.dialogAmount);
        descEt = v.findViewById(R.id.dialogDesc);
        locationNameEt = v.findViewById(R.id.dialogLocationName);
        categorySpinner = v.findViewById(R.id.dialogCategory);

        if (categorySpinner != null) {
            String[] categories = new String[] {"Food", "Transport", "Entertainment", "Utilities", "Other"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, R.layout.custom_spinner_item, categories);
            adapter.setDropDownViewResource(R.layout.custom_spinner_item);
            categorySpinner.setAdapter(adapter);
            categorySpinner.setSelection(4);
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("Add expense")
                .setView(v)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", (d, which) -> {})
                .create();

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog alert = (AlertDialog) getDialog();
        if (alert == null) return;
        final Button positive = alert.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive == null) return;
        final Context dialogCtx = requireContext();
        positive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String amtStr = amountEt.getText().toString();
                    Double amt = null;
                    try { amt = Double.parseDouble(amtStr); } catch (NumberFormatException ignored) { }
                    String desc = descEt.getText().toString().trim();
                    if (desc.isEmpty()) desc = "empty";
                    
                    String locName = locationNameEt != null ? locationNameEt.getText().toString().trim() : "";
                    if (locName.isEmpty()) locName = "empty";
                    
                    if (categorySpinner != null && categorySpinner.getSelectedItem() != null) {
                        pendingCategory = categorySpinner.getSelectedItem().toString();
                    } else {
                        pendingCategory = "Other";
                    }
                    if (amt == null || amt <= 0.0) {
                        Toast.makeText(dialogCtx, "Enter valid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pendingAmount = amt;
                    pendingDesc = desc;
                    pendingLocationName = locName;

                    boolean hasLoc = ContextCompat.checkSelfPermission(dialogCtx, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
                    if (hasLoc) {
                        fetchLocationAndSave();
                    } else {
                        showLocationChoiceDialog(dialogCtx);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Add button exception", ex);
                    Toast.makeText(dialogCtx, "Failed to add expense: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showLocationChoiceDialog(@NonNull final Context dialogCtx) {
        final CharSequence[] options = new CharSequence[] {
                "Use current location",
                "Enter coordinates manually",
                "No location"
        };
        new AlertDialog.Builder(dialogCtx)
                .setTitle("Attach location?")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        boolean hasLoc = ContextCompat.checkSelfPermission(dialogCtx, Manifest.permission.ACCESS_FINE_LOCATION)
                                == PackageManager.PERMISSION_GRANTED;
                        if (!hasLoc) {
                            requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                        } else {
                            fetchLocationAndSave();
                        }
                    } else if (which == 1) {
                        showCustomLocationDialog(dialogCtx);
                    } else {
                        saveExpense(null, appContext);
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> {})
                .show();
    }

    private void showCustomLocationDialog(@NonNull final Context dialogCtx) {
        LinearLayout layout = new LinearLayout(dialogCtx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        final EditText coordEt = new EditText(dialogCtx);
        coordEt.setHint("Latitude, Longitude — e.g. 14.5995, 120.9842");
        coordEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        layout.addView(coordEt, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dlg = new AlertDialog.Builder(dialogCtx)
                .setTitle("Enter coordinates")
                .setView(layout)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, w) -> {})
                .create();
        dlg.show();
        Button saveBtn = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        if (saveBtn == null) return;
        final Context fallback = appContext;
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String coordStr = coordEt.getText().toString().trim();
                if (coordStr.isEmpty()) {
                    Toast.makeText(dialogCtx, "Enter coordinates in the format: lat, lon", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] parts = coordStr.split("[,\\s]+");
                if (parts.length < 2) {
                    Toast.makeText(dialogCtx, "Please provide both latitude and longitude", Toast.LENGTH_SHORT).show();
                    return;
                }
                Double lat = null;
                Double lon = null;
                try { lat = Double.parseDouble(parts[0]); lon = Double.parseDouble(parts[1]); } catch (NumberFormatException ex) {
                    Toast.makeText(dialogCtx, "Enter valid numeric coordinates", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (lat < -90.0 || lat > 90.0) {
                    Toast.makeText(dialogCtx, "Latitude must be between -90 and 90", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (lon < -180.0 || lon > 180.0) {
                    Toast.makeText(dialogCtx, "Longitude must be between -180 and 180", Toast.LENGTH_SHORT).show();
                    return;
                }
                try { dlg.dismiss(); } catch (Throwable t) { Log.w(TAG, "Failed to dismiss coord dialog", t); }
                saveExpense(new Double[]{lat, lon}, fallback);
            }
        });
    }

    private void fetchLocationAndSave() {
        try {
            final Context fallbackCtx = appContext;
            final Context activityCtx = getActivity();
            if (activityCtx == null) {
                saveExpense(null, fallbackCtx);
                return;
            }
            if (ContextCompat.checkSelfPermission(activityCtx, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Location permission missing at fetchLocationAndSave; saving without location.");
                saveExpense(null, fallbackCtx);
                return;
            }
            final FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(activityCtx);
            if (fused == null) {
                saveExpense(null, fallbackCtx);
                return;
            }
            try {
                fused.getLastLocation()
                        .addOnSuccessListener(new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location loc) {
                                try {
                                    if (loc != null) {
                                        saveExpense(new Double[]{loc.getLatitude(), loc.getLongitude()}, fallbackCtx);
                                    } else {
                                        saveExpense(null, fallbackCtx);
                                    }
                                } catch (Throwable t) {
                                    Log.w(TAG, "location success but save failed", t);
                                    saveExpense(null, fallbackCtx);
                                }
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "failed to get lastLocation", e);
                                saveExpense(null, fallbackCtx);
                            }
                        });
            } catch (SecurityException se) {
                Log.w(TAG, "SecurityException calling getLastLocation", se);
                saveExpense(null, fallbackCtx);
            }
        } catch (Exception ex) {
            Log.w(TAG, "fetchLocationAndSave exception", ex);
            final Context fallback = appContext;
            saveExpense(null, fallback);
        }
    }

    private void saveExpense(@Nullable Double[] locPair, @Nullable Context fallbackCtx) {
        Context ctx = getContext();
        if (ctx == null) {
            ctx = fallbackCtx != null ? fallbackCtx : (getActivity() != null ? getActivity().getApplicationContext() : null);
        }
        if (ctx == null) {
            Log.w(TAG, "No context available — cannot save expense");
            return;
        }
        try {
            SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
            String expJson = prefs.getString("expenses_json", "[]");
            Type type = new TypeToken<List<Expense>>() {}.getType();
            List<Expense> list;
            try {
                list = gson.fromJson(expJson, type);
                if (list == null) list = new ArrayList<>();
            } catch (Exception e) {
                Log.w(TAG, "JSON parse failed, creating new list", e);
                list = new ArrayList<>();
            }
            
            long time = System.currentTimeMillis();
            Expense e;
            if (locPair != null && locPair.length >= 2) {
                e = new Expense(pendingAmount, pendingDesc, time, locPair[0], locPair[1], pendingCategory);
                
                // Automatically save to VisitedPlace if a name was provided
                if (!pendingLocationName.isEmpty()) {
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time));
                    VisitedPlace vp = new VisitedPlace(locPair[0], locPair[1], time, date, pendingLocationName);
                    final Context finalCtx = ctx;
                    new Thread(() -> {
                        AppDatabase.getInstance(finalCtx).visitedPlaceDao().insert(vp);
                    }).start();
                }
            } else {
                e = new Expense(pendingAmount, pendingDesc, time, null, null, pendingCategory);
            }
            list.add(0, e);
            String newJson = gson.toJson(list);
            prefs.edit().putString("expenses_json", newJson).apply();
            final int savedCount = list.size();
            Log.d(TAG, "Saved expense. count=" + savedCount + " json_len=" + newJson.length());
            final Context toastCtx = ctx;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try { Toast.makeText(toastCtx, "Saved expense (#" + savedCount + ")", Toast.LENGTH_LONG).show(); } catch (Throwable t) { Log.w(TAG, "Could not show toast", t); }
                }
            });
            if (isAdded()) {
                Bundle bundle = new Bundle();
                bundle.putInt("count", savedCount);
                getParentFragmentManager().setFragmentResult("expenses_changed", bundle);
            } else {
                Log.d(TAG, "Fragment not added — skipping fragment result post");
            }
            try { dismissAllowingStateLoss(); } catch (Throwable t) { Log.w(TAG, "dismissAllowingStateLoss failed", t); }
        } catch (Exception ex) {
            Log.e(TAG, "Could not save expense", ex);
            final Context toastCtx = ctx;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try { Toast.makeText(toastCtx, "Could not save expense: " + ex.getMessage(), Toast.LENGTH_LONG).show(); } catch (Throwable t) { Log.w(TAG, "Could not show failure toast", t); }
                }
            });
        } finally {
            pendingAmount = 0.0;
            pendingDesc = "Expense";
            pendingCategory = "Other";
            pendingLocationName = "";
        }
    }
}
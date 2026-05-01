package com.example.bwme;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AddSavingsDialogFragment extends DialogFragment {
    private EditText amountEt;
    private Spinner periodSpinner;
    private final Gson gson = new Gson();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final Context ctx = requireContext();
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        amountEt = new EditText(ctx);
        amountEt.setHint("Amount (e.g. 100)");
        amountEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(amountEt, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        periodSpinner = new Spinner(ctx);
        String[] cats = new String[] {"Daily", "Weekly", "Monthly"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx, R.layout.custom_spinner_item, cats);
        adapter.setDropDownViewResource(R.layout.custom_spinner_item);
        periodSpinner.setAdapter(adapter);
        periodSpinner.setSelection(2);
        layout.addView(periodSpinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("Add savings")
                .setView(layout)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", (d, w) -> {})
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
                String amtStr = amountEt.getText().toString();
                Double amt = null;
                try { amt = Double.parseDouble(amtStr); } catch (Exception ignored) {}
                if (amt == null || amt <= 0.0) {
                    Toast.makeText(dialogCtx, "Enter valid amount", Toast.LENGTH_SHORT).show();
                    return;
                }
                String period = (String) (periodSpinner.getSelectedItem() != null ? periodSpinner.getSelectedItem() : "Monthly");
                long now = System.currentTimeMillis();
                try {
                    SharedPreferences prefs = requireActivity().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
                    String json = prefs.getString("savings_json", "[]");
                    if (json == null) json = "[]";
                    Type t = new TypeToken<List<Savings>>(){}.getType();
                    List<Savings> list;
                    try {
                        list = gson.fromJson(json, t);
                        if (list == null) list = new ArrayList<>();
                    } catch (Exception ex) {
                        list = new ArrayList<>();
                    }
                    list.add(0, new Savings(amt, period.toLowerCase(), now));
                    String newJson = gson.toJson(list);
                    prefs.edit().putString("savings_json", newJson).apply();
                    int savedCount = list.size();
                    Toast.makeText(dialogCtx, "Saved savings (#" + savedCount + ")", Toast.LENGTH_SHORT).show();
                    Bundle bundle = new Bundle();
                    bundle.putInt("count", savedCount);
                    getParentFragmentManager().setFragmentResult("expenses_changed", bundle);
                } catch (Exception ex) {
                    Toast.makeText(dialogCtx, "Could not save savings", Toast.LENGTH_LONG).show();
                }
                try { alert.dismiss(); } catch (Throwable ignored) {}
            }
        });
    }
}
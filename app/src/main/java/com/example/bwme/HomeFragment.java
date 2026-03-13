package com.example.bwme;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private RecyclerView recycler;
    private ExpenseAdapter adapter;
    private TextView totalTv;
    private final Gson gson = new Gson();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.homeRecycler);
        totalTv = view.findViewById(R.id.homeTotal);

        adapter = new ExpenseAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        loadAndShowExpenses();

        getParentFragmentManager().setFragmentResultListener("expenses_changed", getViewLifecycleOwner(),
                new FragmentResultListener() {
                    @Override
                    public void onFragmentResult(String requestKey, Bundle result) {
                        loadAndShowExpenses();
                    }
                });
    }

    private void loadAndShowExpenses() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString("expenses_json", "[]");
        if (json == null) json = "[]";
        Type type = new TypeToken<List<Expense>>(){}.getType();
        List<Expense> list;
        try {
            list = gson.fromJson(json, type);
            if (list == null) list = new ArrayList<>();
        } catch (Exception ex) {
            list = new ArrayList<>();
        }

        adapter.setItems(list);

        double total = 0.0;
        for (Expense e : list) total += e.amount;

        if (totalTv != null) {
            try {
                totalTv.setText(String.format(Locale.getDefault(), "Total: %.2f", total));
            } catch (Throwable ignored) {}
        }
    }
}
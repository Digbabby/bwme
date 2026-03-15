package com.example.bwme;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.VH> {

    private final List<Expense> itemsInternal;

    public ExpenseAdapter() {
        this.itemsInternal = new ArrayList<>();
    }

    public ExpenseAdapter(List<Expense> initial) {
        this.itemsInternal = (initial != null) ? new ArrayList<>(initial) : new ArrayList<>();
    }

    public void setItems(List<Expense> newList) {
        updateItems(newList);
    }

    public void updateItems(List<Expense> newList) {
        itemsInternal.clear();
        if (newList != null) itemsInternal.addAll(newList);
        notifyDataSetChanged();
    }

    public void addExpense(Expense expense) {
        itemsInternal.add(0, expense);
        notifyItemInserted(0);
    }

    public void clear() {
        int s = itemsInternal.size();
        if (s > 0) {
            itemsInternal.clear();
            notifyItemRangeRemoved(0, s);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_expense, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        Expense e = itemsInternal.get(position);

        if (holder.amountTv != null) {
            try {
                holder.amountTv.setText(String.format(Locale.getDefault(), "₱ %.2f", e.amount));
            } catch (Throwable ignored) {}
        }

        if (holder.descTv != null) {
            try { holder.descTv.setText(e.desc != null ? e.desc : ""); } catch (Throwable ignored) {}
        }

        if (holder.timeTv != null) {
            try {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                holder.timeTv.setText(df.format(new Date(e.ts)));
            } catch (Throwable ignored) {}
        }

        if (holder.locationTv != null) {
            try {
                Double lat = (e != null) ? e.lat : null;
                Double lon = (e != null) ? e.lng : null;
                if (lat != null && lon != null) {
                    holder.locationTv.setVisibility(View.VISIBLE);
                    holder.locationTv.setText(String.format(Locale.getDefault(), "%.6f, %.6f", lat, lon));
                } else {
                    holder.locationTv.setVisibility(View.GONE);
                }
            } catch (Throwable ignored) {}
        }

        if (holder.categoryTv != null) {
            try {
                String cat = (e != null && e.category != null) ? e.category : "Other";
                holder.categoryTv.setVisibility(View.VISIBLE);
                holder.categoryTv.setText(cat);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public int getItemCount() {
        return itemsInternal.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        public final TextView amountTv;
        public final TextView descTv;
        public final TextView timeTv;
        public final TextView locationTv;
        public final TextView categoryTv;

        public VH(View itemView) {
            super(itemView);
            Context ctx = itemView.getContext();
            String pkg = ctx.getPackageName();

            amountTv = findTextViewByNames(itemView, pkg, "itemAmount", "amountText", "amount");
            descTv = findTextViewByNames(itemView, pkg, "itemDesc", "descText", "description");
            timeTv = findTextViewByNames(itemView, pkg, "itemTime", "itemDate", "itemTimestamp", "timeText", "timestamp");
            locationTv = findTextViewByNames(itemView, pkg, "itemLocation", "locationText", "locText", "item_loc");
            categoryTv = findTextViewByNames(itemView, pkg, "itemCategory", "categoryText", "itemCategoryText");
        }

        private static TextView findTextViewByNames(View root, String pkg, String... names) {
            for (String n : names) {
                int id = root.getContext().getResources().getIdentifier(n, "id", pkg);
                if (id != 0) {
                    View v = root.findViewById(id);
                    if (v instanceof TextView) return (TextView) v;
                }
            }
            return null;
        }
    }
}
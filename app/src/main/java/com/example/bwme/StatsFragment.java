package com.example.bwme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsFragment extends Fragment {

    private PieChart pieChart;
    private BarChart barChart;
    private LineChart lineChart;
    private Spinner periodSpinner;
    private Spinner groupSpinner;

    private final Gson gson = new Gson();

    private static final int MAX_SERIES = 6;

    private static final int[] PALETTE = new int[] {
            Color.parseColor("#3366CC"),
            Color.parseColor("#DC3912"),
            Color.parseColor("#FF9900"),
            Color.parseColor("#109618"),
            Color.parseColor("#990099"),
            Color.parseColor("#0099C6"),
            Color.parseColor("#DD4477"),
            Color.parseColor("#66AA00")
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        periodSpinner = view.findViewById(R.id.periodSpinner);
        groupSpinner = view.findViewById(R.id.groupSpinner);
        pieChart = view.findViewById(R.id.categoryPieChart);
        barChart = view.findViewById(R.id.locationBarChart);
        lineChart = view.findViewById(R.id.spendingLineChart);
        setupSpinners();
        configureCharts();
        renderForSelection();
        
        getParentFragmentManager().setFragmentResultListener("expenses_changed", getViewLifecycleOwner(),
                (requestKey, result) -> renderForSelection());
    }

    private void setupSpinners() {
        String[] periods = new String[] {"Daily", "Weekly", "Monthly"};
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(requireContext(), R.layout.custom_spinner_item, periods);
        periodAdapter.setDropDownViewResource(R.layout.custom_spinner_item);
        periodSpinner.setAdapter(periodAdapter);
        String[] groups = new String[] {"Category", "Location"};
        ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(requireContext(), R.layout.custom_spinner_item, groups);
        groupAdapter.setDropDownViewResource(R.layout.custom_spinner_item);
        groupSpinner.setAdapter(groupAdapter);
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { renderForSelection(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        periodSpinner.setOnItemSelectedListener(listener);
        groupSpinner.setOnItemSelectedListener(listener);
    }

    private void configureCharts() {
        if (pieChart != null) {
            pieChart.getDescription().setEnabled(false);
            pieChart.setUsePercentValues(false);
            pieChart.setDrawEntryLabels(false);
            Legend l = pieChart.getLegend();
            if (l != null) l.setWordWrapEnabled(true);
        }
        if (barChart != null) {
            barChart.getDescription().setEnabled(false);
            barChart.setFitBars(true);
            if (barChart.getLegend() != null) barChart.getLegend().setWordWrapEnabled(true);
            XAxis x = barChart.getXAxis();
            x.setGranularity(1f);
            x.setPosition(XAxis.XAxisPosition.BOTTOM);
        }
        if (lineChart != null) {
            lineChart.getDescription().setEnabled(false);
            if (lineChart.getLegend() != null) lineChart.getLegend().setWordWrapEnabled(true);
            XAxis x = lineChart.getXAxis();
            x.setGranularity(1f);
            x.setPosition(XAxis.XAxisPosition.BOTTOM);
        }
    }

    private void renderForSelection() {
        if (getContext() == null) return;
        final String period = (String) (periodSpinner.getSelectedItem() != null ? periodSpinner.getSelectedItem() : "Daily");
        final String groupBy = (String) (groupSpinner.getSelectedItem() != null ? groupSpinner.getSelectedItem() : "Category");
        PeriodBuckets buckets = buildBuckets(period);
        AggregationResult agg = aggregateExpenses(buckets, groupBy, period);
        List<SeriesTotal> sorted = new ArrayList<>();
        for (Map.Entry<String, double[]> en : agg.series.entrySet()) {
            double total = 0.0;
            for (double v : en.getValue()) total += v;
            sorted.add(new SeriesTotal(en.getKey(), total, en.getValue()));
        }
        Collections.sort(sorted, new Comparator<SeriesTotal>() {
            @Override public int compare(SeriesTotal a, SeriesTotal b) {
                return Double.compare(b.total, a.total);
            }
        });
        renderPieChart(sorted, agg.series, buckets);
        renderBarChart(sorted);
        renderLineChart(sorted, buckets);
    }

    private static class PeriodBuckets {
        final long[] starts;
        final String[] labels;
        PeriodBuckets(long[] starts, String[] labels) { this.starts = starts; this.labels = labels; }
    }

    private PeriodBuckets buildBuckets(String period) {
        Calendar now = Calendar.getInstance();
        if ("Weekly".equalsIgnoreCase(period)) {
            int count = 12;
            long[] starts = new long[count];
            String[] labels = new String[count];
            Calendar c = (Calendar) now.clone();
            int firstDay = c.getFirstDayOfWeek();
            while (c.get(Calendar.DAY_OF_WEEK) != firstDay) c.add(Calendar.DAY_OF_MONTH, -1);
            for (int i = 0; i < count; i++) {
                Calendar bucketStart = (Calendar) c.clone();
                bucketStart.add(Calendar.WEEK_OF_YEAR, i - (count - 1));
                bucketStart.set(Calendar.HOUR_OF_DAY, 0);
                bucketStart.set(Calendar.MINUTE, 0);
                bucketStart.set(Calendar.SECOND, 0);
                bucketStart.set(Calendar.MILLISECOND, 0);
                starts[i] = bucketStart.getTimeInMillis();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd", Locale.getDefault());
                labels[i] = sdf.format(bucketStart.getTime());
            }
            return new PeriodBuckets(starts, labels);
        } else if ("Monthly".equalsIgnoreCase(period)) {
            int count = 12;
            long[] starts = new long[count];
            String[] labels = new String[count];
            Calendar c = (Calendar) now.clone();
            c.set(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            for (int i = 0; i < count; i++) {
                Calendar bucketStart = (Calendar) c.clone();
                bucketStart.add(Calendar.MONTH, i - (count - 1));
                starts[i] = bucketStart.getTimeInMillis();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM", Locale.getDefault());
                labels[i] = sdf.format(bucketStart.getTime());
            }
            return new PeriodBuckets(starts, labels);
        } else {
            int count = 7;
            long[] starts = new long[count];
            String[] labels = new String[count];
            Calendar c = (Calendar) now.clone();
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            for (int i = 0; i < count; i++) {
                Calendar bucketStart = (Calendar) c.clone();
                bucketStart.add(Calendar.DAY_OF_MONTH, i - (count - 1));
                starts[i] = bucketStart.getTimeInMillis();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd", Locale.getDefault());
                labels[i] = sdf.format(bucketStart.getTime());
            }
            return new PeriodBuckets(starts, labels);
        }
    }

    private static class AggregationResult {
        final Map<String, double[]> series;
        AggregationResult(Map<String, double[]> series) { this.series = series; }
    }

    private AggregationResult aggregateExpenses(PeriodBuckets buckets, String groupBy, String period) {
        Map<String, double[]> series = new HashMap<>();
        Context context = getContext();
        if (context == null) return new AggregationResult(series);
        SharedPreferences prefs = MainActivity.getUserPrefs(context);
        String json = prefs.getString("expenses_json", "[]");
        if (json == null) json = "[]";
        Type listType = new TypeToken<List<Expense>>() {}.getType();
        List<Expense> list;
        try {
            list = gson.fromJson(json, listType);
            if (list == null) list = new ArrayList<>();
        } catch (Exception ex) {
            list = new ArrayList<>();
        }
        int bucketCount = buckets.starts.length;
        for (Expense e : list) {
            if (e == null) continue;
            double amt = 0.0;
            try { amt = e.amount; } catch (Throwable t) { continue; }
            long ts = e.ts;
            int idx = -1;
            for (int b = 0; b < bucketCount; b++) {
                long start = buckets.starts[b];
                long end = (b + 1 < bucketCount) ? buckets.starts[b + 1] : Long.MAX_VALUE;
                if (ts >= start && ts < end) { idx = b; break; }
            }
            if (idx == -1) continue;
            String cat = e.category != null ? e.category.toLowerCase(Locale.ROOT) : "";
            boolean isAllocated = ("bills".equals(cat) || "rent".equals(cat) || "gas".equals(cat) || "installments".equals(cat) || "planned expense".equals(cat));
            if (!"Monthly".equalsIgnoreCase(period) && isAllocated) continue;
            String key;
            if ("Location".equalsIgnoreCase(groupBy)) {
                if (e.lat == null || e.lng == null) continue;
                key = String.format(Locale.US, "%.3f,%.3f", e.lat, e.lng);
            } else {
                key = (e.category != null) ? e.category : "Other";
            }
            double[] arr = series.get(key);
            if (arr == null) {
                arr = new double[bucketCount];
                for (int i = 0; i < bucketCount; i++) arr[i] = 0.0;
                series.put(key, arr);
            }
            arr[idx] += amt;
        }
        return new AggregationResult(series);
    }

    private static class SeriesTotal {
        final String key;
        final double total;
        final double[] series;
        SeriesTotal(String key, double total, double[] series) { this.key = key; this.total = total; this.series = series; }
    }

    private void renderPieChart(List<SeriesTotal> sorted, Map<String, double[]> seriesMap, PeriodBuckets buckets) {
        if (pieChart == null) return;
        pieChart.clear();
        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int taken = 0;
        double otherSum = 0.0;
        for (SeriesTotal st : sorted) {
            if (taken < MAX_SERIES) {
                double s = 0.0;
                for (double v : st.series) s += v;
                entries.add(new PieEntry((float) s, st.key));
                colors.add(PALETTE[taken % PALETTE.length]);
                taken++;
            } else {
                double s = 0.0;
                for (double v : st.series) s += v;
                otherSum += s;
            }
        }
        if (entries.isEmpty()) {
            pieChart.setNoDataText("No data");
            pieChart.invalidate();
            return;
        }
        if (otherSum > 0.0) {
            entries.add(new PieEntry((float) otherSum, "Other"));
            colors.add(Color.LTGRAY);
        }
        PieDataSet ds = new PieDataSet(entries, "");
        ds.setSliceSpace(2f);
        ds.setColors(colors);
        ds.setValueTextSize(12f);
        PieData pd = new PieData(ds);
        pieChart.setData(pd);
        pieChart.invalidate();
    }

    private void renderBarChart(List<SeriesTotal> sorted) {
        if (barChart == null) return;
        barChart.clear();
        if (sorted.isEmpty()) {
            barChart.setNoDataText("No data");
            barChart.invalidate();
            return;
        }
        int take = Math.min(sorted.size(), MAX_SERIES);
        List<BarEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < take; i++) {
            SeriesTotal st = sorted.get(i);
            entries.add(new BarEntry(i, (float) st.total));
            labels.add(st.key);
            colors.add(PALETTE[i % PALETTE.length]);
        }
        BarDataSet ds = new BarDataSet(entries, "");
        ds.setColors(colors);
        ds.setValueTextSize(11f);
        BarData bd = new BarData(ds);
        bd.setBarWidth(0.9f);
        barChart.setData(bd);
        XAxis x = barChart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setLabelRotationAngle(-45f);
        x.setGranularity(1f);
        barChart.invalidate();
    }

    private void renderLineChart(List<SeriesTotal> sorted, PeriodBuckets buckets) {
        if (lineChart == null) return;
        lineChart.clear();
        if (sorted.isEmpty()) {
            lineChart.setNoDataText("No data");
            lineChart.invalidate();
            return;
        }
        int take = Math.min(sorted.size(), MAX_SERIES);
        List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet> sets = new ArrayList<>();
        for (int s = 0; s < take; s++) {
            SeriesTotal st = sorted.get(s);
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < st.series.length; i++) {
                entries.add(new Entry(i, (float) st.series[i]));
            }
            LineDataSet ds = new LineDataSet(entries, st.key);
            ds.setLineWidth(2f);
            ds.setCircleRadius(3f);
            ds.setColor(PALETTE[s % PALETTE.length]);
            ds.setCircleColor(PALETTE[s % PALETTE.length]);
            ds.setValueTextSize(9f);
            sets.add(ds);
        }
        LineData ld = new LineData(sets);
        lineChart.setData(ld);
        XAxis x = lineChart.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(buckets.labels));
        x.setLabelRotationAngle(-40f);
        x.setGranularity(1f);
        lineChart.invalidate();
    }
}

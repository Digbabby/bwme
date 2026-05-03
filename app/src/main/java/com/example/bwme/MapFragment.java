package com.example.bwme;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapFragment extends Fragment {

    private LocationAdapter adapter;
    private MapView map;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private MyLocationNewOverlay myLocationOverlay;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    SharedPreferences session = requireContext().getSharedPreferences("UserSession", Context.MODE_PRIVATE);
    final String loggedInUser = session.getString("username", "");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Configuration.getInstance().load(getContext(), PreferenceManager.getDefaultSharedPreferences(getContext()));
        Configuration.getInstance().setUserAgentValue(getContext().getPackageName());

        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(18.0);

        setupMyLocationOverlay();

        RecyclerView rv = view.findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LocationAdapter(new ArrayList<>());
        adapter.setOnLocationDeleteListener(this::confirmDelete);
        rv.setAdapter(adapter);

        fusedClient = LocationServices.getFusedLocationProviderClient(getContext());
        setupLocationTracking();

        view.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            executor.execute(() -> {
                AppDatabase.getInstance(requireContext(), loggedInUser).visitedPlaceDao().deleteAll();
                mainHandler.post(() -> {
                    refreshHistory();
                });
            });
        });

        refreshHistory();
        requestGpsPermission();

        getParentFragmentManager().setFragmentResultListener("expenses_changed", getViewLifecycleOwner(), (requestKey, result) -> {
            refreshHistory();
        });
    }

    private void setupMyLocationOverlay(){
        GpsMyLocationProvider provider = new GpsMyLocationProvider(getContext());
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
        myLocationOverlay = new MyLocationNewOverlay(provider, map);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        myLocationOverlay.runOnFirstFix(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (map != null && myLocationOverlay != null) {
                        map.getController().animateTo(myLocationOverlay.getMyLocation());
                    }
                });
            }
        });
        map.getOverlays().add(myLocationOverlay);
    }

    private void showDeleteDialog(List<Expense> locExpenses, List<VisitedPlace> locPlaces) {
        List<Object> combined = new ArrayList<>();
        Set<Long> processedTs = new HashSet<>();

        if (locExpenses != null) {
            for (Expense e : locExpenses) {
                combined.add(e);
                processedTs.add(e.ts);
            }
        }
        if (locPlaces != null) {
            for (VisitedPlace p : locPlaces) {
                if (!processedTs.contains(p.timestamp)) {
                    combined.add(p);
                }
            }
        }

        if (combined.isEmpty()) return;

        String[] displayItems = new String[combined.size()];
        for (int i = 0; i < combined.size(); i++) {
            Object obj = combined.get(i);
            if (obj instanceof Expense) {
                Expense e = (Expense) obj;
                displayItems[i] = "[Record] " + e.category + ": ₱" + String.format(Locale.getDefault(), "%.2f", e.amount);
            } else if (obj instanceof VisitedPlace) {
                VisitedPlace p = (VisitedPlace) obj;
                displayItems[i] = "[Visit] " + (p.category != null ? p.category : "Location") + " (" + p.readableDate + ")";
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Items at this location")
                .setItems(displayItems, (dialog, which) -> {
                    confirmDelete(combined.get(which));
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void confirmDelete(Object item) {
        long timestamp;
        String label;
        if (item instanceof Expense) {
            timestamp = ((Expense) item).ts;
            label = "Expense (" + ((Expense) item).category + ")";
        } else if (item instanceof VisitedPlace) {
            timestamp = ((VisitedPlace) item).timestamp;
            label = "Visit (" + (((VisitedPlace) item).category != null ? ((VisitedPlace) item).category : "Location") + ")";
        } else return;

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Record")
                .setMessage("Delete this " + label + " and its associated map data?")
                .setPositiveButton("Delete", (d, w) -> deleteRecord(timestamp))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRecord(long timestamp) {
        executor.execute(() -> {
            Context ctx = getContext();
            if (ctx == null) return;

            // 1. Delete from Room (VisitedPlace)
            AppDatabase.getInstance(ctx).visitedPlaceDao().deleteByTimestamp(timestamp);

            // 2. Delete from SharedPrefs (Expense)
            SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
            String json = prefs.getString("expenses_json", "[]");
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<Expense>>(){}.getType();
            List<Expense> expenses = new com.google.gson.Gson().fromJson(json, type);

            if (expenses != null) {
                boolean removed = false;
                for (int i = 0; i < expenses.size(); i++) {
                    if (expenses.get(i).ts == timestamp) {
                        expenses.remove(i);
                        removed = true;
                        break;
                    }
                }
                if (removed) {
                    prefs.edit().putString("expenses_json", new com.google.gson.Gson().toJson(expenses)).apply();
                    mainHandler.post(() -> getParentFragmentManager().setFragmentResult("expenses_changed", new Bundle()));
                }
            }

            mainHandler.post(() -> {
                refreshHistory();
                Toast.makeText(ctx, "Record deleted", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void refreshHistory() {
        executor.execute(() -> {
            Context ctx = getContext();
            if (ctx == null) return;

            List<VisitedPlace> history = AppDatabase.getInstance(ctx, loggedInUser).visitedPlaceDao().getAllPlaces();
            SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
            String json = prefs.getString("expenses_json", "[]");
            List<Expense> expenses = new com.google.gson.Gson().fromJson(json, new com.google.gson.reflect.TypeToken<List<Expense>>(){}.getType());

            mainHandler.post(() -> {
                if (!isAdded()) return;
                adapter.updateData(history);
                map.getOverlays().clear();
                if (myLocationOverlay != null) {
                    map.getOverlays().add(myLocationOverlay);
                }

                // Consolidated Grouping
                Map<String, List<Object>> consolidated = new HashMap<>();

                if (history != null) {
                    for (VisitedPlace p : history) {
                        String key = p.latitude + "," + p.longitude;
                        if (!consolidated.containsKey(key)) consolidated.put(key, new ArrayList<>());
                        consolidated.get(key).add(p);
                    }
                }

                if (expenses != null) {
                    for (Expense e : expenses) {
                        if (e.lat != null && e.lng != null) {
                            String key = e.lat + "," + e.lng;
                            if (!consolidated.containsKey(key)) consolidated.put(key, new ArrayList<>());
                            consolidated.get(key).add(e);
                        }
                    }
                }

                for (Map.Entry<String, List<Object>> entry : consolidated.entrySet()) {
                    String[] coords = entry.getKey().split(",");
                    double lat = Double.parseDouble(coords[0]);
                    double lng = Double.parseDouble(coords[1]);
                    List<Object> items = entry.getValue();

                    Marker m = new Marker(map);
                    m.setPosition(new GeoPoint(lat, lng));

                    String locationName = null;
                    List<Expense> locExpenses = new ArrayList<>();
                    List<VisitedPlace> locPlaces = new ArrayList<>();
                    for (Object item : items) {
                        if (item instanceof VisitedPlace) {
                            VisitedPlace p = (VisitedPlace) item;
                            locPlaces.add(p);
                            locationName = p.category;
                        } else if (item instanceof Expense) {
                            locExpenses.add((Expense) item);
                        }
                    }

                    if (locExpenses.isEmpty()) {
                        m.setTitle(locationName);
                        m.setIcon(getResources().getDrawable(android.R.drawable.btn_star_big_on));
                    } else if (locExpenses.size() == 1) {
                        Expense e = locExpenses.get(0);
                        m.setTitle((locationName != null ? locationName : e.category) + ": ₱" + String.format(Locale.getDefault(), "%.2f", e.amount));
                        m.setSnippet(e.desc);

                        int resId = android.R.drawable.ic_menu_myplaces;
                        switch (e.category) {
                            case "Food":
                                resId = R.drawable.map_food_icon_158315;
                                break;
                            case "Transport":
                                resId = R.drawable.bus_map_pin_icon;
                                break;
                            case "Entertainment":
                                resId = R.drawable.theater_comedy_icon;
                                break;
                            case "Utilities":
                                resId = R.drawable.payments_icon;
                                break;
                        }
                        m.setIcon(getResources().getDrawable(resId));
                    } else {
                        double total = 0;
                        StringBuilder sb = new StringBuilder();
                        for (Expense e : locExpenses) {
                            total += e.amount;
                            sb.append("• ").append(e.category).append(": ₱").append(String.format(Locale.getDefault(), "%.2f", e.amount)).append(" (").append(e.desc).append(")\n");
                        }
                        m.setTitle((locationName != null ? locationName : "Multiple Expenses") + ": ₱" + String.format(Locale.getDefault(), "%.2f", total));
                        m.setSnippet(sb.toString().trim());
                        m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_agenda));
                    }

                    m.setOnMarkerClickListener((marker, mapView) -> {
                        showDeleteDialog(locExpenses, locPlaces);
                        return true; // Consume the event to show our custom dialog
                    });

                    map.getOverlays().add(m);
                }
                map.invalidate();
            });
        });
    }

    private void setupLocationTracking() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
            }
        };

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        }
    }

    private void requestGpsPermission() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            Toast.makeText(getContext(), "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (map != null) map.onResume();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (map != null) map.onPause();
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

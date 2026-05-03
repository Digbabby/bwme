package com.example.bwme;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.appcompat.app.AlertDialog;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private String loggedInUser = "";
    private Context appContext;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        appContext = context.getApplicationContext();

        SharedPreferences session = appContext.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        loggedInUser = session.getString("username", "");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        if (appContext != null) {
            Configuration.getInstance().load(appContext, PreferenceManager.getDefaultSharedPreferences(appContext));
            Configuration.getInstance().setUserAgentValue(appContext.getPackageName());
        }

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
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LocationAdapter(new ArrayList<>(), this::confirmDeleteVisitedPlace);
        rv.setAdapter(adapter);

        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());
        setupLocationTracking();

        view.findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDeleteAllVisitedPlaces());

        refreshHistory();
        requestGpsPermission();

        getParentFragmentManager().setFragmentResultListener(
                "expenses_changed",
                getViewLifecycleOwner(),
                (requestKey, result) -> refreshHistory()
        );
    }

    private void confirmDeleteVisitedPlace(@NonNull VisitedPlace place) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete location?")
                .setMessage("Delete this location record and its matching expense entry?")
                .setPositiveButton("Delete", (d, w) -> deleteVisitedPlace(place))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteAllVisitedPlaces() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete all locations?")
                .setMessage("Delete all location records and their matching expense entries?")
                .setPositiveButton("Delete all", (d, w) -> deleteAllVisitedPlaces())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteVisitedPlace(@NonNull VisitedPlace place) {
        final Context ctx = getContext();
        if (ctx == null) return;

        executor.execute(() -> {
            try {
                AppDatabase.getInstance(ctx, loggedInUser)
                        .visitedPlaceDao()
                        .delete(place);

                removeMatchingExpenseFromPrefs(ctx, place.timestamp, place.latitude, place.longitude);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Deleted location", Toast.LENGTH_SHORT).show();
                    refreshHistory();
                    getParentFragmentManager().setFragmentResult("expenses_changed", new Bundle());
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Delete failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void deleteAllVisitedPlaces() {
        final Context ctx = getContext();
        if (ctx == null) return;

        executor.execute(() -> {
            try {
                List<VisitedPlace> places = AppDatabase.getInstance(ctx, loggedInUser)
                        .visitedPlaceDao()
                        .getAllPlaces();

                AppDatabase.getInstance(ctx, loggedInUser)
                        .visitedPlaceDao()
                        .deleteAll();

                if (places != null && !places.isEmpty()) {
                    SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
                    String json = prefs.getString("expenses_json", "[]");
                    if (json == null) json = "[]";

                    List<Expense> expenses;
                    try {
                        expenses = new Gson().fromJson(
                                json,
                                new TypeToken<List<Expense>>() {}.getType()
                        );
                        if (expenses == null) expenses = new ArrayList<>();
                    } catch (Exception e) {
                        expenses = new ArrayList<>();
                    }

                    final double EPS = 0.000001;
                    Iterator<Expense> exIt = expenses.iterator();
                    while (exIt.hasNext()) {
                        Expense ex = exIt.next();
                        if (ex == null || ex.lat == null || ex.lng == null) continue;

                        boolean match = false;
                        for (VisitedPlace p : places) {
                            if (p == null) continue;
                            boolean tsMatch = ex.ts == p.timestamp;
                            boolean coordMatch = Math.abs(ex.lat - p.latitude) < EPS
                                    && Math.abs(ex.lng - p.longitude) < EPS;
                            if (tsMatch || coordMatch) {
                                match = true;
                                break;
                            }
                        }

                        if (match) exIt.remove();
                    }

                    prefs.edit().putString("expenses_json", new Gson().toJson(expenses)).apply();
                }

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "All locations deleted", Toast.LENGTH_SHORT).show();
                    refreshHistory();
                    getParentFragmentManager().setFragmentResult("expenses_changed", new Bundle());
                });
            } catch (Throwable t) {
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Delete all failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void removeMatchingExpenseFromPrefs(@NonNull Context ctx, long ts, double lat, double lng) {
        SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
        String json = prefs.getString("expenses_json", "[]");
        if (json == null) json = "[]";

        List<Expense> expenses;
        try {
            expenses = new Gson().fromJson(
                    json,
                    new TypeToken<List<Expense>>() {}.getType()
            );
            if (expenses == null) expenses = new ArrayList<>();
        } catch (Exception e) {
            expenses = new ArrayList<>();
        }

        boolean changed = false;
        final double EPS = 0.000001;

        Iterator<Expense> it = expenses.iterator();
        while (it.hasNext()) {
            Expense ex = it.next();
            if (ex == null) continue;

            boolean tsMatch = ex.ts == ts;
            boolean coordMatch = ex.lat != null && ex.lng != null
                    && Math.abs(ex.lat - lat) < EPS
                    && Math.abs(ex.lng - lng) < EPS;

            if (tsMatch || coordMatch) {
                it.remove();
                changed = true;
            }
        }

        if (changed) {
            prefs.edit().putString("expenses_json", new Gson().toJson(expenses)).apply();
        }
    }

    private void setupMyLocationOverlay() {
        if (map == null || appContext == null) return;

        GpsMyLocationProvider provider = new GpsMyLocationProvider(appContext);
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

    private void refreshHistory() {
        executor.execute(() -> {
            Context ctx = getContext();
            if (ctx == null) return;

            List<VisitedPlace> history = AppDatabase.getInstance(ctx, loggedInUser)
                    .visitedPlaceDao()
                    .getAllPlaces();

            SharedPreferences prefs = MainActivity.getUserPrefs(ctx);
            String json = prefs.getString("expenses_json", "[]");

            List<Expense> expenses;
            try {
                expenses = new Gson().fromJson(
                        json,
                        new TypeToken<List<Expense>>() {}.getType()
                );
                if (expenses == null) expenses = new ArrayList<>();
            } catch (Exception e) {
                expenses = new ArrayList<>();
            }

            final List<VisitedPlace> finalHistory = history;
            final List<Expense> finalExpenses = expenses;

            mainHandler.post(() -> {
                if (!isAdded() || map == null) return;

                adapter.updateData(finalHistory);
                map.getOverlays().clear();
                if (myLocationOverlay != null) {
                    map.getOverlays().add(myLocationOverlay);
                }

                Map<String, List<Object>> consolidated = new HashMap<>();

                if (finalHistory != null) {
                    for (VisitedPlace p : finalHistory) {
                        String key = p.latitude + "," + p.longitude;
                        if (!consolidated.containsKey(key)) consolidated.put(key, new ArrayList<>());
                        consolidated.get(key).add(p);
                    }
                }

                if (finalExpenses != null) {
                    for (Expense e : finalExpenses) {
                        if (e != null && e.lat != null && e.lng != null) {
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

                    for (Object item : items) {
                        if (item instanceof VisitedPlace) {
                            locationName = ((VisitedPlace) item).category;
                        } else if (item instanceof Expense) {
                            locExpenses.add((Expense) item);
                        }
                    }

                    if (locExpenses.isEmpty()) {
                        m.setTitle(locationName);
                        m.setIcon(getResources().getDrawable(android.R.drawable.btn_star_big_on));
                    } else if (locExpenses.size() == 1) {
                        Expense e = locExpenses.get(0);
                        m.setTitle((locationName != null ? locationName : e.category) + ": ₱" +
                                String.format(Locale.getDefault(), "%.2f", e.amount));
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
                            sb.append("• ")
                                    .append(e.category)
                                    .append(": ₱")
                                    .append(String.format(Locale.getDefault(), "%.2f", e.amount))
                                    .append(" (")
                                    .append(e.desc)
                                    .append(")\n");
                        }
                        m.setTitle((locationName != null ? locationName : "Multiple Expenses") + ": ₱" +
                                String.format(Locale.getDefault(), "%.2f", total));
                        m.setSnippet(sb.toString().trim());
                        m.setIcon(getResources().getDrawable(android.R.drawable.ic_menu_agenda));
                    }

                    map.getOverlays().add(m);
                }

                map.invalidate();
            });
        });
    }

    private void setupLocationTracking() {
        if (getContext() == null) return;

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
            }
        };

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        }
    }

    private void requestGpsPermission() {
        if (getContext() == null) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
    }

    private void startLocationUpdates() {
        if (getContext() == null || fusedClient == null) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
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
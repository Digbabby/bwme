package com.example.bwme;

import android.Manifest;
import android.content.Context;
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
import android.widget.EditText;
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

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapFragment extends Fragment {

    private LocationAdapter adapter;
    private Location lastLocation;
    private MapView map;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private MyLocationNewOverlay myLocationOverlay;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 1. IMPORTANT: Load configuration for osmdroid
        Configuration.getInstance().load(getContext(), PreferenceManager.getDefaultSharedPreferences(getContext()));
        Configuration.getInstance().setUserAgentValue(getContext().getPackageName());

        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 3. Initialize your Map
        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(18.0);

        setupMyLocationOverlay();

        // 4. Initialize Table (RecyclerView)
        RecyclerView rv = view.findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LocationAdapter(new ArrayList<>());
        rv.setAdapter(adapter);

        // 5. GPS Setup
        fusedClient = LocationServices.getFusedLocationProviderClient(getContext());
        setupLocationTracking();

        // 6. Buttons
        view.findViewById(R.id.btnSave).setOnClickListener(v -> {
            if (lastLocation != null) {
                showCategoryDialog(lastLocation);
            } else {
                Toast.makeText(getContext(), "Wait for GPS...", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            executor.execute(() -> {
                AppDatabase.getInstance(getContext()).visitedPlaceDao().deleteAll();
                mainHandler.post(() -> {
                    refreshHistory();
                    map.getOverlays().clear();
                    map.getOverlays().add(myLocationOverlay);
                    map.invalidate();
                });
            });
        });

        // 7. Load Data on Startup
        refreshHistory();
        requestGpsPermission();
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

    private void showCategoryDialog(Location loc) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Name this place");
        final EditText input = new EditText(getContext());
        input.setHint("e.g. Home, Cafe, Work");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String category = input.getText().toString();
            saveToDb(loc, category);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveToDb(Location loc, String category) {
        long time = System.currentTimeMillis();
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(time));

        VisitedPlace vp = new VisitedPlace(loc.getLatitude(), loc.getLongitude(), time, date, category);
        executor.execute(() -> {
            AppDatabase.getInstance(getContext()).visitedPlaceDao().insert(vp);
            mainHandler.post(() -> {
                refreshHistory();
                Marker m = new Marker(map);
                m.setPosition(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle(category + " (" + date + ")");
                map.getOverlays().add(m);
                map.invalidate();
                Toast.makeText(getContext(), "Location Saved!", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void refreshHistory() {
        executor.execute(() -> {
            List<VisitedPlace> history = AppDatabase.getInstance(getContext()).visitedPlaceDao().getAllPlaces();
            mainHandler.post(() -> {
                adapter.updateData(history);
                // Add markers to map
                map.getOverlays().clear();
                if (myLocationOverlay != null) {
                    map.getOverlays().add(myLocationOverlay);
                }
                for (VisitedPlace p : history) {
                    Marker m = new Marker(map);
                    m.setPosition(new GeoPoint(p.latitude, p.longitude));
                    m.setTitle(p.category + " (" + p.readableDate + ")");
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
                lastLocation = result.getLastLocation();
                if (lastLocation != null && map != null) {
                    map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude()));
                }
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

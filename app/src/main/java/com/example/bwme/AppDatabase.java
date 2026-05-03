package com.example.bwme;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.HashMap;
import java.util.Map;

@Database(entities = {VisitedPlace.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract VisitedPlaceDao visitedPlaceDao();

    private static final Map<String, AppDatabase> INSTANCES = new HashMap<>();

    public static AppDatabase getInstance(final Context context) {
        return getInstance(context, "");
    }

    public static AppDatabase getInstance(final Context context, String username) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }

        if (username == null) username = "";

        synchronized (AppDatabase.class) {
            AppDatabase db = INSTANCES.get(username);
            if (db == null || !db.isOpen()) {
                String dbName = "travel_tracker_" + (username.isEmpty() ? "default" : username) + ".db";
                db = Room.databaseBuilder(
                                context.getApplicationContext(),
                                AppDatabase.class,
                                dbName
                        )
                        .fallbackToDestructiveMigration()
                        .build();
                INSTANCES.put(username, db);
            }
            return db;
        }
    }
}
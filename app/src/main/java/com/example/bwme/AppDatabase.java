package com.example.bwme;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {VisitedPlace.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract VisitedPlaceDao visitedPlaceDao();

    private static volatile AppDatabase INSTANCE;
    private static String CURRENT_DB_USER = "";

    public static AppDatabase getInstance(final Context context) {
        return getInstance(context, "");
    }

    public static AppDatabase getInstance(final Context context, String username) {
        if (username == null) username = "";
        if (INSTANCE == null || !CURRENT_DB_USER.equals(username)) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null || !CURRENT_DB_USER.equals(username)) {
                    if (INSTANCE != null) {
                        INSTANCE.close();
                    }
                    String dbName = "travel_tracker_" + (username.isEmpty() ? "default" : username) + ".db";
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, dbName)
                            .fallbackToDestructiveMigration()
                            .build();
                    CURRENT_DB_USER = username;
                }
            }
        }
        return INSTANCE;
    }
}

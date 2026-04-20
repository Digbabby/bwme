package com.example.bwme;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "visited_places")
public class VisitedPlace {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "readable_date")
    public String readableDate;

    @ColumnInfo(name = "category")
    public String category;

    public VisitedPlace(double latitude, double longitude, long timestamp, String readableDate, String category) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.readableDate = readableDate;
        this.category = category;
    }
}

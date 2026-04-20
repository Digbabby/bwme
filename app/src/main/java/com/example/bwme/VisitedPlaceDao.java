package com.example.bwme;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import java.util.List;

@Dao
public interface VisitedPlaceDao {
    //SQL LESSONS insert table//
    @Insert
    void insert(VisitedPlace place);

    @Query("SELECT * FROM visited_places ORDER BY timestamp DESC")
    List<VisitedPlace> getAllPlaces();

    @Delete
    void delete(VisitedPlace place);
    @Query("DELETE FROM visited_places")
    void deleteAll();
}
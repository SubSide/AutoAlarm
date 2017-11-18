package nl.thomasvdbulk.autoalarm.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface JourneyDao {
    @Query("SELECT * FROM journey")
    List<JourneyWithLegs> getAll();

    @Insert
    void insert(Journey... journeys);

    @Insert
    void insert(Leg... legs);

    @Delete
    void delete(Journey journey);

    @Delete
    void delete(Leg journey);

    @Query("DELETE FROM journey")
    void deleteAll();
}

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
    long insert(Journey journeys);

    @Insert
    long[] insert(Leg... legs);

    @Delete
    void delete(Journey journey);

    @Delete
    void delete(Leg journey);

    @Query("DELETE FROM journey")
    void deleteAllJourneys();

    @Query("DELETE FROM leg")
    void deletAllLegs();
}

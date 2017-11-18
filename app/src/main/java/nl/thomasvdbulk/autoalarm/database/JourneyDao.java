package nl.thomasvdbulk.autoalarm.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface JourneyDao {
    @Query("SELECT * FROM journey")
    List<Journey> getAll();

    @Insert
    void insertAll(Journey... journeys);

    @Delete
    void delete(Journey journey);
}

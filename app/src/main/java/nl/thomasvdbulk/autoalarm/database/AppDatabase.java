package nl.thomasvdbulk.autoalarm.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {Journey.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract JourneyDao journeyDao();
}

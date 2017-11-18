package nl.thomasvdbulk.autoalarm.database;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity
public class Journey {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int numberOfChanges;
    public String realDeparture;
    public String departure;
    public String realArrival;
    public String arrival;
}

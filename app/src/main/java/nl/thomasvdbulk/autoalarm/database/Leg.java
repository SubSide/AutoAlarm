package nl.thomasvdbulk.autoalarm.database;

import android.arch.persistence.room.Embedded;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity
public class Leg {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long journeyId;

    public String type;
    public String duration;
    public String realDeparture;
    public String departure;
    public String realArrival;
    public String arrival;

    @Embedded(prefix = "from_")
    public Location from;

    @Embedded(prefix = "to_")
    public Location to;
}

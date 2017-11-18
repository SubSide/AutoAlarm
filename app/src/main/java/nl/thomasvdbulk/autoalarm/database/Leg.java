package nl.thomasvdbulk.autoalarm.database;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Embedded;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity
public class Leg {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String type;
    @ColumnInfo(name = "real_departure")
    public String realDeparture;
    public String departure;
    @ColumnInfo(name = "real_arrival")
    public String realArrival;
    public String arrival;

    @Embedded(prefix = "from")
    public Location from;

    @Embedded(prefix = "to")
    public Location to;
}

package nl.thomasvdbulk.autoalarm.database;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.Relation;

import java.util.List;

@Entity
public class Journey {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "number_of_changes")
    public int numberOfChanges;

    @ColumnInfo(name = "real_departure")
    public String realDeparture;
    public String departure;
    @ColumnInfo(name = "real_arrival")
    public String realArrival;
    public String arrival;

    @Relation(parentColumn = "id", entityColumn = "journeyId")
    public List<Leg> legs;

}

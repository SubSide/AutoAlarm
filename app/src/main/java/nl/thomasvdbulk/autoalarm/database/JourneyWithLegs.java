package nl.thomasvdbulk.autoalarm.database;

import android.arch.persistence.room.Embedded;
import android.arch.persistence.room.Relation;

import java.util.List;

public class JourneyWithLegs {
    @Embedded
    public Journey journey;

    @Relation(parentColumn = "id", entityColumn = "journeyId", entity = Leg.class)
    public List<Leg> legs;
}

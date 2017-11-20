package nl.thomasvdbulk.autoalarm;

import android.os.AsyncTask;

import java.util.List;

import nl.thomasvdbulk.autoalarm.database.AppDatabase;
import nl.thomasvdbulk.autoalarm.database.JourneyWithLegs;

class AsyncViewLoader extends AsyncTask<MainActivity, Void, Void> {

    private AppDatabase db;

    AsyncViewLoader(AppDatabase db){
        super();
        this.db = db;
    }

    @Override
    protected Void doInBackground(MainActivity... contexts) {
        List<JourneyWithLegs> list = db.journeyDao().getAll();

        if(list.size() < 1)
            return null;

        contexts[0].renderEvents(list);

        return null;
    }
}
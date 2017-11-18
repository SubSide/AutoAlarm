package nl.thomasvdbulk.autoalarm.background;

import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.thomasvdbulk.autoalarm.MainActivity;
import nl.thomasvdbulk.autoalarm.R;
import nl.thomasvdbulk.autoalarm.database.AppDatabase;
import nl.thomasvdbulk.autoalarm.database.Journey;
import nl.thomasvdbulk.autoalarm.database.JourneyWithLegs;
import nl.thomasvdbulk.autoalarm.database.Leg;
import nl.thomasvdbulk.autoalarm.database.Location;

import static android.content.Context.POWER_SERVICE;

public class ApiRequestAlarm extends BroadcastReceiver {

    public static final String WAKE_TAG = "nl.thomasvdbulk.autoalarm.WAKE_TAG";
    public static final long WAKE_TIMEOUT = 15 * 1000;

    public PowerManager.WakeLock wakeLock;
    private Context context;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
        wakeLock.acquire(WAKE_TIMEOUT);

//        Map<String, String> args = new HashMap<>();
//        args.put("lang", "en-GB");
//        args.put("from", "station-leiden-lammenschans");
//        args.put("to", "warmond_ganzenwei");
//        args.put("searchType", "departure");
//        args.put("dateTime", "2017-11-25T1230");
//        args.put("sequence", "1");
//        args.put("byTrain", "true");
//        args.put("byBus", "true");
//        args.put("bySubway", "true");
//        args.put("byTram", "true");
//        args.put("byFerry", "true");
//        args.put("interchangeTime", "standard");
//        args.put("planWithAccessibility", "false");
//        args.put("before", "0");
//        args.put("after", "0");
//        args.put("realtime", "true");
//
//        new HttpRequestTask().execute("-----------"+urlEncodeUTF8(args));

        InputStream is = null;
        BufferedReader br = null;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            InputStream raw = context.getResources().openRawResource(R.raw.test);
            is = new BufferedInputStream(raw);
            br = new BufferedReader(new InputStreamReader(is));

            String line;
            String newLine = System.getProperty("line.separator");
            while ((line = br.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(newLine);
            }
            br.close();
        } catch(IOException e){
            Log.d(MainActivity.LOG_TAG, "Error recieving API data", e);
        } finally {
            if(is != null){
                try {
                    is.close();
                } catch(IOException e){
                    Log.d(MainActivity.LOG_TAG, "InputStream couldn't be closed.", e);
                }
            }

            if(br != null){
                try {
                    br.close();
                } catch(IOException e){
                    Log.d(MainActivity.LOG_TAG, "BufferedReader couldn't be closed.", e);
                }
            }
        }

        try {
            JSONObject obj = new JSONObject(stringBuilder.toString());
            new JsonProcessingTask().execute(obj);



        } catch(JSONException e){
            Log.d(MainActivity.LOG_TAG, "Error loading JSON Object", e);
        }
    }

    public void handleJson(JSONObject object) throws JSONException {
        JSONArray journeysArray = object.getJSONArray("journeys");


        AppDatabase db = Room.databaseBuilder(context,
                AppDatabase.class, "journeys").build();

        db.journeyDao().deleteAll();

        for(int i = 0; i < journeysArray.length(); i++){
            JSONObject journeyObject = journeysArray.getJSONObject(i);

            // Create a new journey and fill in the information we need
            Journey journey = new Journey();
            journey.departure = journeyObject.getString("departure");
            journey.realDeparture = journeyObject.getString("realtimeDeparture");
            journey.arrival = journeyObject.getString("arrival");
            journey.realArrival = journeyObject.getString("realtimeArrival");
            journey.numberOfChanges = journeyObject.getInt("numberOfChanges");
            db.journeyDao().insert(journey);

            List<Leg> legs = new ArrayList<>();

            // Get all the intermediate places
            JSONArray legsArray = journeyObject.getJSONArray("legs");
            for(int j = 0; j < legsArray.length(); j++){
                JSONObject legObject = legsArray.getJSONObject(j);

                // Get the array of stops, we only need the first and the last one though
                // as we are not interested in intermediate stops.
                JSONArray stopsArray = legObject.getJSONArray("stops");
                JSONObject departureObject = stopsArray.getJSONObject(0);
                JSONObject arrivalObject = stopsArray.getJSONObject(stopsArray.length()-1);

                Leg leg = new Leg();
                leg.type = legObject.getJSONObject("mode").getString("type");

                // From stuff
                leg.departure = departureObject.getString("departure");
                leg.realDeparture = departureObject.getString("realtimeDeparture");
                Location fromLoc = new Location();
                JSONObject fromLocationObject = departureObject.getJSONObject("location");
                // We save the name as "name, place, region"
                fromLoc.name =
                        fromLocationObject.getJSONObject("place").getString("name") + ", " +
                                fromLocationObject.getString("name") + ", " +
                                fromLocationObject.getJSONObject("place").getString("regionName");
                JSONObject fromLatLng = fromLocationObject.getJSONObject("latLong");
                fromLoc.latitude = fromLatLng.getDouble("lat");
                fromLoc.longitude = fromLatLng.getDouble("long");
                fromLoc.type = fromLocationObject.getString("type");

                // To stuff
                leg.arrival = arrivalObject.getString("arrival");
                leg.realArrival = arrivalObject.getString("realtimeArrival");
                Location toLoc = new Location();
                JSONObject toLocationObject = arrivalObject.getJSONObject("location");
                // We save the name as "name, place, region"
                toLoc.name =
                        toLocationObject.getJSONObject("place").getString("name") + ", " +
                                toLocationObject.getString("name") + ", " +
                                toLocationObject.getJSONObject("place").getString("regionName");
                JSONObject toLatLng = toLocationObject.getJSONObject("latLong");
                toLoc.latitude = toLatLng.getDouble("lat");
                toLoc.longitude = toLatLng.getDouble("long");
                toLoc.type = toLocationObject.getString("type");


                leg.from = fromLoc;
                leg.to = toLoc;
                leg.journeyId = journey.id;

                legs.add(leg);
            }

            db.journeyDao().insert(legs.toArray(new Leg[legs.size()]));
        }

    }


    class JsonProcessingTask extends AsyncTask<JSONObject, Void, String> {
        @Override
        protected String doInBackground(JSONObject... objects) {
            try {
                handleJson(objects[0]);
            } catch(JSONException e){
                Log.d(MainActivity.LOG_TAG, "Error handling JSON", e);
            }
            return "";
        }

        @Override
        public void onCancelled(){
            wakeLock.release();
        }

        @Override
        public void onPostExecute(String result){
            wakeLock.release();
        }
    }

    class HttpRequestTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            HttpURLConnection urlConnection = null;
            InputStream is = null;
            BufferedReader br = null;
            StringBuilder stringBuilder = new StringBuilder();
            try {
                URL url = new URL(strings[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                is = new BufferedInputStream(urlConnection.getInputStream());
                br = new BufferedReader(new InputStreamReader(is));

                String line;
                String newLine = System.getProperty("line.separator");
                while ((line = br.readLine()) != null) {
                    stringBuilder.append(line);
                    stringBuilder.append(newLine);
                }
                br.close();
            } catch(IOException e){
                Log.d(MainActivity.LOG_TAG, "Error recieving API data", e);
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }

                if(is != null){
                    try {
                        is.close();
                    } catch(IOException e){
                        Log.d(MainActivity.LOG_TAG, "InputStream couldn't be closed.", e);
                    }
                }

                if(br != null){
                    try {
                        br.close();
                    } catch(IOException e){
                        Log.d(MainActivity.LOG_TAG, "BufferedReader couldn't be closed.", e);
                    }
                }
            }
            return stringBuilder.toString();
        }

        @Override
        public void onCancelled(){
            wakeLock.release();
        }
    }

    private String urlEncodeUTF8(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(String.format("%s=%s",
                        URLEncoder.encode(entry.getKey(), "UTF-8"),
                        URLEncoder.encode(entry.getValue(), "UTF-8")
                ));
            }
        } catch(UnsupportedEncodingException e){
            Log.d(MainActivity.LOG_TAG, "Encoding type is not supported!", e);
        }
        return sb.toString();
    }
}

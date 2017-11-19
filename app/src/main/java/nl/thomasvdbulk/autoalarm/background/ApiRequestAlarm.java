package nl.thomasvdbulk.autoalarm.background;

import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.provider.AlarmClock;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import nl.thomasvdbulk.autoalarm.MainActivity;
import nl.thomasvdbulk.autoalarm.database.AppDatabase;
import nl.thomasvdbulk.autoalarm.database.Journey;
import nl.thomasvdbulk.autoalarm.database.Leg;
import nl.thomasvdbulk.autoalarm.database.Location;

import static android.content.Context.POWER_SERVICE;

public class ApiRequestAlarm extends BroadcastReceiver {

    public static final String WAKE_TAG = "nl.thomasvdbulk.autoalarm.WAKE_TAG";
    public static final long WAKE_TIMEOUT = 15 * 1000;

    public PowerManager.WakeLock wakeLock;

    AppDatabase db;



    @Override
    public void onReceive(Context context, Intent intent) {
        this.db = Room.databaseBuilder(context, AppDatabase.class, "journeys").build();

        CalendarEvent calendarEvent = new CalendarEvent();
        calendarEvent.title = intent.getStringExtra("title");
        calendarEvent.begin = intent.getStringExtra("begin");
        calendarEvent.location = intent.getStringExtra("location");

        PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        try {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
            wakeLock.acquire(WAKE_TIMEOUT);

            new MainProcessingTask(calendarEvent, db, wakeLock).execute(context);
        } catch(NullPointerException e){
            Log.d(MainActivity.LOG_TAG, "NPE thrown on acquiring wakelock", e);
        }

    }


    static class MainProcessingTask extends AsyncTask<Context, Void, String> {
        private CalendarEvent calendarEvent;
        private PowerManager.WakeLock wakeLock;
        private AppDatabase db;

        MainProcessingTask(CalendarEvent calendarEvent, AppDatabase db, PowerManager.WakeLock wakeLock){
            this.db = db;
            this.calendarEvent = calendarEvent;
            this.wakeLock = wakeLock;
        }

        @Override
        protected String doInBackground(Context... contexts) {

            // Create a DateFormatter object for displaying date in specified format.
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'hhmm", Locale.ENGLISH);

            // Create a calendar object that will convert the date and time value in milliseconds to date.
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(Long.parseLong(calendarEvent.begin));
            String dateTime = formatter.format(calendar.getTime());

            try {
                Map<String, String> destinationArgs = new HashMap<>();
                destinationArgs.put("address", calendarEvent.location);
                destinationArgs.put("key", "AIzaSyDJ7wCLa4EpqPQFaiaTNCacpuc6_PEakuI");
                String destinationUrl = "https://maps.googleapis.com/maps/api/geocode/json?" + urlEncodeUTF8(destinationArgs);
                String fromLatLng = handleMapsJsonLookup(new JSONObject(getDataFromUrl(destinationUrl)));

                Map<String, String> journeyArgs = new HashMap<>();
                journeyArgs.put("lang", "en-GB");
                journeyArgs.put("from", "warmond_ganzenwei");
                journeyArgs.put("to", fromLatLng);
                journeyArgs.put("searchType", "arrival");
                journeyArgs.put("dateTime", dateTime);
                journeyArgs.put("sequence", "1");
                journeyArgs.put("byTrain", "true");
                journeyArgs.put("byBus", "true");
                journeyArgs.put("bySubway", "true");
                journeyArgs.put("byTram", "true");
                journeyArgs.put("byFerry", "true");
                journeyArgs.put("interchangeTime", "standard");
                journeyArgs.put("planWithAccessibility", "false");
                journeyArgs.put("before", "0");
                journeyArgs.put("after", "0");
                journeyArgs.put("realtime", "true");
                String journeyUrl = "------------?" + urlEncodeUTF8(journeyArgs);
                handleJourneyJson(contexts[0], db, new JSONObject(getDataFromUrl(journeyUrl)));
            } catch(JSONException e){
                Log.d(MainActivity.LOG_TAG, "Error handling JSON Data", e);
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

    public static String handleMapsJsonLookup(JSONObject data) throws JSONException {
        JSONObject location = data.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
        return location.getString("lat")+","+location.getString("lng");
    }

    public static void handleJourneyJson(Context context, AppDatabase db, JSONObject object) throws JSONException {
        JSONArray journeysArray = object.getJSONArray("journeys");

        db.journeyDao().deleteAllJourneys();
        db.journeyDao().deletAllLegs();

        Journey journey = null;

        for(int i = 0; i < journeysArray.length(); i++){
            JSONObject journeyObject = journeysArray.getJSONObject(i);

            // Create a new journey and fill in the information we need
            journey = new Journey();
            journey.departure = journeyObject.getString("departure");
            journey.realDeparture = journeyObject.getString("realtimeDeparture");
            journey.arrival = journeyObject.getString("arrival");
            journey.realArrival = journeyObject.getString("realtimeArrival");
            journey.numberOfChanges = journeyObject.getInt("numberOfChanges");
            journey.id = db.journeyDao().insert(journey);

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
                if(legObject.has("duration")){
                    leg.duration = legObject.getString("duration");
                }

                if(legObject.has("service")){
                    leg.service = legObject.getString("service");
                }

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
                if(toLocationObject.has("place")) {
                    toLoc.name = toLocationObject.getJSONObject("place").getString("name") + ", " +
                            toLocationObject.getString("name") + ", " +
                            toLocationObject.getJSONObject("place").getString("regionName");
                } else {
                    toLoc.name = "Your destination!";
                }
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

        if(journey == null)
            return;

        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm", Locale.ENGLISH);
            Calendar cal = Calendar.getInstance();
            cal.setTime(formatter.parse(journey.departure));
            cal.add(Calendar.HOUR_OF_DAY, -1);

            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY));
            intent.putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE));
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Good morning!");

            context.startActivity(intent);
        } catch(ParseException e){
            Log.d(MainActivity.LOG_TAG, "Problem converting date to a calendar!", e);
        }

    }

    public static String getDataFromUrl(String urlString){
        Log.d(MainActivity.LOG_TAG, urlString);
        HttpURLConnection urlConnection = null;
        InputStream is = null;
        BufferedReader br = null;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            URL url = new URL(urlString);
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

    private static String urlEncodeUTF8(Map<String, String> map) {
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

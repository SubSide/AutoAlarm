package nl.thomasvdbulk.autoalarm.background;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.provider.AlarmClock;
import android.support.v4.app.NotificationCompat;
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
import nl.thomasvdbulk.autoalarm.R;
import nl.thomasvdbulk.autoalarm.database.AppDatabase;
import nl.thomasvdbulk.autoalarm.database.Journey;
import nl.thomasvdbulk.autoalarm.database.Leg;
import nl.thomasvdbulk.autoalarm.database.Location;

public class WebRequestTask extends AsyncTask<Context, Void, String> {
    private CalendarEvent calendarEvent;
    private PowerManager.WakeLock wakeLock;
    private AppDatabase db;

    WebRequestTask(CalendarEvent calendarEvent, AppDatabase db, PowerManager.WakeLock wakeLock){
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
            String journeyUrl = "---------------?" + urlEncodeUTF8(journeyArgs);
            handleJourneyJson(contexts[0], db, new JSONObject(getDataFromUrl(journeyUrl)));
        } catch(JSONException e){
            Log.d(MainActivity.LOG_TAG, "Error handling JSON Data", e);
        }

        return "";
    }

    @Override
    public void onCancelled(){
        if(wakeLock.isHeld())
            wakeLock.release();
    }

    @Override
    public void onPostExecute(String result){
        if(wakeLock.isHeld())
            wakeLock.release();
    }

    private static String handleMapsJsonLookup(JSONObject data) throws JSONException {
        JSONObject location = data.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
        return location.getString("lat")+","+location.getString("lng");
    }

    private static void handleJourneyJson(Context context, AppDatabase db, JSONObject object) throws JSONException {
        JSONArray journeysArray = object.getJSONArray("journeys");

        db.journeyDao().deleteAllJourneys();
        db.journeyDao().deletAllLegs();

        Journey journey = null;
        List<Leg> legs = null;

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

            legs = new ArrayList<>();

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


                // Now we add some stuff for later

                if(leg.type.equalsIgnoreCase("train")){
                    leg.service = "platform " + departureObject.getString("platform") +" -> "+arrivalObject.getString("platform");
                } else if(legObject.has("service")){
                    leg.service = legObject.getString("service");
                }



                leg.from = fromLoc;
                leg.to = toLoc;
                leg.journeyId = journey.id;

                legs.add(leg);
            }

            db.journeyDao().insert(legs.toArray(new Leg[legs.size()]));
        }

        if(journey == null)
            return;

        // We send an update to the GUI here
        // Which needs to be done here... Because setting an alarm will
        // pause the main activity for just a brief moment, ending up with
        // The broadcast not being displayed.
        // We commented it out because we lose focus, which means onResume is getting called,
        // which calls showEvents()
        Intent broadcastIntent = new Intent(MainActivity.BROADCAST_UPDATE_GUI);
        context.sendBroadcast(broadcastIntent);

        try {
            SimpleDateFormat timeFormatter = new SimpleDateFormat("hh:mm", Locale.ENGLISH);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm", Locale.ENGLISH);
            Calendar cal = Calendar.getInstance();
            cal.setTime(formatter.parse(journey.departure));
            String time = timeFormatter.format(cal.getTime());

            cal.add(Calendar.HOUR_OF_DAY, -1);

            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY));
            intent.putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE));
            intent.putExtra(AlarmClock.EXTRA_DAYS, cal.get(Calendar.DAY_OF_WEEK));
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Good morning!");

            context.startActivity(intent);

            String alarmTime = timeFormatter.format(cal.getTime());

            String type = "";
            if(legs != null) {
                for (Leg leg : legs) {
                    if (leg.type.equalsIgnoreCase("walk")) {
                        continue;
                    }

                    type = leg.type;

                    if(leg.service != null)
                        type += " " + leg.service;
                    break;
                }
            } else {
                type = "O.o";
            }

            sendNotifcation(context, alarmTime, time, type);
        } catch(ParseException e){
            Log.d(MainActivity.LOG_TAG, "Problem converting date to a calendar!", e);
        }

    }

    private static String getDataFromUrl(String urlString){
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

    private static void sendNotifcation(Context context, String alarmTime, String time, String reach){

        String text = "Automatic alarm set at "+alarmTime+" to get " + reach + " at " + time + "\n" +
                "Don't be late!! :)";
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, ApiRequestAlarm.NOTIFICATION_TAG)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(context.getString(R.string.notification_title))
                        .setContentText(text);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(context, MainActivity.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your app to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(MainActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                        0,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // mNotificationId is a unique integer your app uses to identify the
        // notification. For example, to cancel the notification, you can pass its ID
        // number to NotificationManager.cancel().
        mNotificationManager.notify(MainActivity.NOTIFICATION_UID, mBuilder.build());
    }
}

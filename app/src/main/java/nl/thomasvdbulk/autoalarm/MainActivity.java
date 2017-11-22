package nl.thomasvdbulk.autoalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import nl.thomasvdbulk.autoalarm.background.ApiRequestAlarm;
import nl.thomasvdbulk.autoalarm.background.CalendarEvent;
import nl.thomasvdbulk.autoalarm.database.AppDatabase;
import nl.thomasvdbulk.autoalarm.database.JourneyWithLegs;
import nl.thomasvdbulk.autoalarm.database.Leg;

public class MainActivity extends BaseActivity {
    public static final int NOTIFICATION_UID = 1337;
    private static final int PERMISSION_REQUEST_PERMISSIONS = 1;
    public static final String DATA_SHARED_FILE = "calendarIds";
    public static final String DATA_CALENDAR_ID_KEY = "nl.thomasvdbulk.autoalarm.calendarids";
    public static final String BROADCAST_UPDATE_GUI = "nl.thomasvdbulk.autoalarm.BROADCAST_UPDATE_GUI";

    public static final String DATA_EVENT_TITLE = "nl.thomasvdbulk.autoalarm.event.TITLE";
    public static final String DATA_EVENT_BEGIN = "nl.thomasvdbulk.autoalarm.event.BEGIN";
    public static final String DATA_EVENT_LOCATION = "nl.thomasvdbulk.autoalarm.event.LOCATION";
    public static final String DATA_EVENT_DESCRIPTION = "nl.thomasvdbulk.autoalarm.event.DESCRIPTION";

    public static final String LOG_TAG = "AutoAlarm debugging";

    private boolean pickedCalendars = false;
    private long timeToWakeUp = 60 * 60 * 1000;

    AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_main);

        db = Room.databaseBuilder(this, AppDatabase.class, "journeys").build();


        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WAKE_LOCK, Manifest.permission.SET_ALARM},
                PERMISSION_REQUEST_PERMISSIONS);

        FloatingActionButton fab = findViewById(R.id.refresh);
        fab.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                setAlarm(Calendar.getInstance());
            }
        });

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        setAlarm(calendar);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_PERMISSIONS) {
            return;
        }

        for(int i = 0; i < permissions.length; i++){
            if(grantResults[i] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this.getApplicationContext(), "We need calendar permissions to read out the events!", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
//        IntentFilter intentFilter = new IntentFilter(MainActivity.BROADCAST_UPDATE_GUI);
//        mReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                showEvents();
//            }
//        };
//
//        this.registerReceiver(mReceiver, intentFilter);


        // If we've already selected the calendar we want to make sure we don't send the user to
        // the pick calendars over and over again
        SharedPreferences sharedPref = this.getSharedPreferences(MainActivity.DATA_SHARED_FILE, Context.MODE_PRIVATE);
        if(sharedPref.contains(MainActivity.DATA_CALENDAR_ID_KEY)){
            pickedCalendars = true;
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
            showEvents();
    }

    @Override
    public void onPause(){
        super.onPause();

//        this.unregisterReceiver(mReceiver);
    }


    public void showEvents(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this.getApplicationContext(), "We need calendar permissions to read out the events!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if(!pickedCalendars) {
            Intent intent = new Intent(this, PickCalendarActivity.class);
            startActivity(intent);
            return;
        }

        new AsyncViewLoader(db).execute(this);
    }

    public void renderEvents(final List<JourneyWithLegs> journeyWithLegsList){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CalendarEvent calendarObject = getCalendarEvent();
                if(calendarObject != null) {
                    try {

                        SimpleDateFormat timeFormatter = new SimpleDateFormat("hh:mm", Locale.ENGLISH);
                        ((TextView) findViewById(R.id.event_what)).setText(calendarObject.title + " at " + timeFormatter.format(new Date(Long.parseLong(calendarObject.begin))));
                    } catch(NumberFormatException e){
                        Log.d(MainActivity.LOG_TAG, "'begin' could not be converted to a long", e);
                    }
                    ((TextView) findViewById(R.id.event_where)).setText(("At " + calendarObject.location));
                    ((TextView) findViewById(R.id.event_description)).setText(calendarObject.description);
                }



                LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                LinearLayout linearLayout = findViewById(R.id.journey_list);
                linearLayout.removeAllViews();
                List<Leg> legs = journeyWithLegsList.get(0).legs;
                for(int i = 0; i < legs.size(); i++) {
                    Leg leg = legs.get(i);
                    // Create a new view
                    View legView = inflater.inflate(R.layout.journey_list_item, null);

                    // Set the text to the calendar name
                    String type = leg.type;
                    if(leg.service != null){
                        type += " " + leg.service;
                    }

                    type = type.substring(0, 1).toUpperCase() + type.substring(1);

                    ((TextView)legView.findViewById(R.id.type)).setText(type);
                    ((TextView)legView.findViewById(R.id.duration)).setText(leg.duration);
                    ((TextView)legView.findViewById(R.id.from)).setText("From: " + leg.from.name);
                    ((TextView)legView.findViewById(R.id.from_time)).setText(formatDate(leg.departure));
                    ((TextView)legView.findViewById(R.id.to)).setText("To: " + leg.to.name);
                    ((TextView)legView.findViewById(R.id.to_time)).setText(formatDate(leg.arrival));

                    // Alternate it!
                    if(i % 2 == 0)
                        legView.setBackgroundColor(getColor(R.color.tableAlternate));

                    linearLayout.addView(legView);
                }
            }
        });
    }

    private CalendarEvent getCalendarEvent(){
        SharedPreferences sharedPref = this.getSharedPreferences(MainActivity.DATA_SHARED_FILE, Context.MODE_PRIVATE);
        if(!sharedPref.contains(DATA_EVENT_TITLE)) {
            return null;
        }

        CalendarEvent calendarObject = new CalendarEvent();
        calendarObject.title = sharedPref.getString(DATA_EVENT_TITLE, "");
        calendarObject.location = sharedPref.getString(DATA_EVENT_LOCATION, "");
        calendarObject.begin = sharedPref.getString(DATA_EVENT_BEGIN, "");
        calendarObject.description = sharedPref.getString(DATA_EVENT_DESCRIPTION, "");

        return calendarObject;
    }

    public String formatDate(String date){
        if(date == null || !date.contains("T"))
            return "";

        return date.split("T")[1];
    }

    protected void setAlarm(Calendar calendar){
        AlarmManager alarmManager = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ApiRequestAlarm.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        calendar.set(Calendar.AM_PM, Calendar.AM);
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.add(Calendar.SECOND, new Random().nextInt(60));

        long startTime = calendar.getTimeInMillis();

        Log.d(MainActivity.LOG_TAG, "Alarm intent will start at: "+(new Date(startTime).toString()));

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, startTime, pendingIntent);
    }
}

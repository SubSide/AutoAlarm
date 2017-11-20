package nl.thomasvdbulk.autoalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
    public static final String LOG_TAG = "AutoAlarm debugging";

    protected CalendarEvent calendarObject;
    private boolean pickedCalendars = false;
    private Set<String> calendarIds;
    private long timeToWakeUp = 60 * 60 * 1000;

    private BroadcastReceiver mReceiver;

    AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_main);

        db = Room.databaseBuilder(this, AppDatabase.class, "journeys").build();

        SharedPreferences sharedPref = this.getSharedPreferences(MainActivity.DATA_SHARED_FILE, Context.MODE_PRIVATE);
        if(sharedPref.contains(MainActivity.DATA_CALENDAR_ID_KEY)){
            calendarIds = sharedPref.getStringSet(MainActivity.DATA_CALENDAR_ID_KEY, new HashSet<String>());
            pickedCalendars = true;
        } else {
            calendarIds = new HashSet<>();
        }

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WAKE_LOCK, Manifest.permission.SET_ALARM},
                PERMISSION_REQUEST_PERMISSIONS);

        FloatingActionButton fab = findViewById(R.id.refresh);
        final Context thiz = this;
        fab.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                setAlarm(Calendar.getInstance(), calendarObject);
            }
        });
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

        new AsyncCalendarEventLoader(calendarIds, timeToWakeUp).execute(this);
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

        showEvents();
    }

    @Override
    public void onPause(){
        super.onPause();

//        this.unregisterReceiver(mReceiver);
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }

        return super.onOptionsItemSelected(item);
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
                LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                LinearLayout linearLayout = findViewById(R.id.journey_list);
                linearLayout.removeAllViews();
                List<Leg> legs = journeyWithLegsList.get(0).legs;
                for(Leg leg : legs) {
                    // Create a new view
                    View legView = inflater.inflate(R.layout.journey_list_item, null);

                    // Set the text to the calendar name
                    ((TextView)legView.findViewById(R.id.type)).setText(leg.type+" "+leg.service);
                    ((TextView)legView.findViewById(R.id.duration)).setText(leg.duration);
                    ((TextView)legView.findViewById(R.id.from)).setText(leg.from.name);
                    ((TextView)legView.findViewById(R.id.from_time)).setText(formatDate(leg.departure));
                    ((TextView)legView.findViewById(R.id.to)).setText(leg.to.name);
                    ((TextView)legView.findViewById(R.id.to_time)).setText(formatDate(leg.arrival));

                    linearLayout.addView(legView);
                }
            }
        });
    }

    public String formatDate(String date){
        if(date == null || !date.contains("T"))
            return "";

        return date.split("T")[1];
    }

    protected void setAlarm(Calendar calendar, CalendarEvent event){
        if(event == null){
            Log.d(MainActivity.LOG_TAG, "calendarObject is null!");
        }

        calendarObject = event;

        AlarmManager alarmManager = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ApiRequestAlarm.class);
        intent.putExtra("title", event.title);
        intent.putExtra("begin", event.begin);
        intent.putExtra("location", event.location);
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

package nl.thomasvdbulk.autoalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.arch.persistence.room.Room;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import nl.thomasvdbulk.autoalarm.background.ApiRequestAlarm;
import nl.thomasvdbulk.autoalarm.background.CalendarEvent;
import nl.thomasvdbulk.autoalarm.database.AppDatabase;
import nl.thomasvdbulk.autoalarm.database.Journey;
import nl.thomasvdbulk.autoalarm.database.JourneyWithLegs;
import nl.thomasvdbulk.autoalarm.database.Leg;

public class MainActivity extends BaseActivity {
    public static final int NOTIFICATION_UID = 1337;
    private static final int PERMISSION_REQUEST_PERMISSIONS = 1;
    public static final String DATA_SHARED_FILE = "calendarIds";
    public static final String DATA_CALENDAR_ID_KEY = "nl.thomasvdbulk.autoalarm.calendarids";
    public static final String LOG_TAG = "AutoAlarm debugging";

    private boolean pickedCalendars = false;
    private Set<String> calendarIds;
    private long timeToWakeUp = 60 * 60 * 1000;

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

        setAlarmOnFirstEvent(calendarIds);
        showEvents();
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

        new AsyncViewLoader(this).execute(db);
    }

    public void setAlarmOnFirstEvent(Set<String> calendarIds){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED){
            return;
        }


        Calendar calendar = Calendar.getInstance();
        long startDay = calendar.getTimeInMillis();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        long endDay = calendar.getTimeInMillis();


        Cursor cur = getCursorFromTo(startDay, endDay);

        if(cur.moveToNext()){
            // Check if it's necessary to set a timer now, or tomorrow
            if(cur.getLong(cur.getColumnIndex(CalendarContract.Instances.BEGIN)) < Calendar.getInstance().getTimeInMillis() - timeToWakeUp){

                calendar = Calendar.getInstance();
                calendar.add(Calendar.DATE, 1);
                startDay = calendar.getTimeInMillis();
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                endDay = calendar.getTimeInMillis();
                cur = getCursorFromTo(startDay, endDay);
                if(!cur.moveToNext()){
                    cur.close();
                    return;
                }
            }


            CalendarEvent event = new CalendarEvent();
            event.title = cur.getString(cur.getColumnIndex(CalendarContract.Instances.TITLE));
            event.begin = cur.getString(cur.getColumnIndex(CalendarContract.Instances.BEGIN));
            event.location = cur.getString(cur.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION));
            setAlarm(calendar, event);
        }

        cur.close();
    }

    public Cursor getCursorFromTo(long startDay, long endDay){
        String[] projection = new String[] { BaseColumns._ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_LOCATION };

        String selection = CalendarContract.Instances.CALENDAR_ID + " IN ("+makePlaceholders(calendarIds.size()) + ")";
        String[] selectionArgs = calendarIds.toArray(new String[calendarIds.size()]);

        String order = CalendarContract.Instances.BEGIN+" ASC LIMIT 1";

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, startDay);
        ContentUris.appendId(builder, endDay);


        return getContentResolver().query(builder.build(), projection, selection, selectionArgs, order);

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

    String makePlaceholders(int len) {
        if (len < 1) {
            return "";
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }

    public void setAlarm(Calendar calendar, CalendarEvent event){
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

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, startTime, pendingIntent);
    }

    static class AsyncViewLoader extends AsyncTask<AppDatabase, Void, List<JourneyWithLegs>> {

        private MainActivity main;

        public AsyncViewLoader(MainActivity main){
            super();
            this.main = main;
        }

        @Override
        protected List<JourneyWithLegs> doInBackground(AppDatabase... dbs) {
            return dbs[0].journeyDao().getAll();
        }

        @Override
        protected void onPostExecute(List<JourneyWithLegs> list) {
            if(list.size() < 1)
                return;
            LayoutInflater inflater = (LayoutInflater)main.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            LinearLayout linearLayout = main.findViewById(R.id.journey_list);
            Journey journey = list.get(0).journey;
            List<Leg> legs = list.get(0).legs;
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

        public String formatDate(String date){
            if(date == null || !date.contains("T"))
                return "";

            return date.split("T")[1];
        }
    }
}

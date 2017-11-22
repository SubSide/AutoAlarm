package nl.thomasvdbulk.autoalarm.background;

import android.Manifest;
import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import nl.thomasvdbulk.autoalarm.MainActivity;
import nl.thomasvdbulk.autoalarm.database.AppDatabase;

import static android.content.Context.POWER_SERVICE;

public class ApiRequestAlarm extends BroadcastReceiver {

    public static final String NOTIFICATION_TAG = "nl.thomasvdbulk.autoalarm.NOTIFICATION_TAG";
    public static final String WAKE_TAG = "nl.thomasvdbulk.autoalarm.WAKE_TAG";
    public static final long WAKE_TIMEOUT = 15 * 1000;

    public PowerManager.WakeLock wakeLock;

    AppDatabase db;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // We want to make sure we have selected some calendars
        SharedPreferences sharedPref = context.getSharedPreferences(MainActivity.DATA_SHARED_FILE, Context.MODE_PRIVATE);
        if(!sharedPref.contains(MainActivity.DATA_CALENDAR_ID_KEY)){
            return;
        }

        Set<String> calendarIds = sharedPref.getStringSet(MainActivity.DATA_CALENDAR_ID_KEY, new HashSet<String>());

        // We grab the start day and the end day, if it has been provided, otherwise we use today
        long startDay = intent.getLongExtra("startDay", 0);
        long endDay = intent.getLongExtra("endDay", 0);


        this.db = Room.databaseBuilder(context, AppDatabase.class, "journeys").build();

        PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        try {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
            wakeLock.acquire(WAKE_TIMEOUT);

            CalendarEvent event = getCalendarEvent(context, calendarIds, startDay, endDay);
            if(event != null)
                new WebRequestTask(event, db, wakeLock).execute(context);
        } catch (NullPointerException e) {
            Log.d(MainActivity.LOG_TAG, "NPE thrown on acquiring wakelock", e);
        }

    }

    private CalendarEvent getCalendarEvent(Context context, Set<String> calenderIds, long startDay, long endDay){
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED){
            return null;
        }

        if(startDay == 0 && endDay == 0) {
            Calendar calendar = getCalendarAtMidnight();
            startDay = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            endDay = calendar.getTimeInMillis();
        }


        Cursor cur = getCursorFromTo(context, calenderIds, startDay, endDay);

        // Check if we can find any calendar item or if the first event of the day is already past
        if(!cur.moveToNext() || cur.getLong(cur.getColumnIndex(CalendarContract.Instances.END)) < Calendar.getInstance().getTimeInMillis()){
            Log.d(MainActivity.LOG_TAG, "Event not found, will look for next day!");
            Calendar calendar = getCalendarAtMidnight();
            calendar.add(Calendar.DATE, 1);
            startDay = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            endDay = calendar.getTimeInMillis();
            cur  = getCursorFromTo(context, calenderIds, startDay, endDay);

            // Check if we can find a new calendar event
            if(!cur.moveToNext()){
                cur.close();
                return null;
            }
        }

        CalendarEvent event = new CalendarEvent();
        event.title = cur.getString(cur.getColumnIndex(CalendarContract.Instances.TITLE));
        event.begin = cur.getString(cur.getColumnIndex(CalendarContract.Instances.BEGIN));
        event.location = cur.getString(cur.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION));
        event.description = cur.getString(cur.getColumnIndex(CalendarContract.Instances.DESCRIPTION));
        cur.close();

        // We save it to persistent storage so we can use it later!
        SharedPreferences sharedPref = context.getSharedPreferences(MainActivity.DATA_SHARED_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(MainActivity.DATA_EVENT_TITLE, event.title);
        editor.putString(MainActivity.DATA_EVENT_BEGIN, event.begin);
        editor.putString(MainActivity.DATA_EVENT_LOCATION, event.location);
        editor.putString(MainActivity.DATA_EVENT_DESCRIPTION, event.description);
        editor.apply();

        return event;
    }

    private Calendar getCalendarAtMidnight(){
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        return calendar;
    }


    private Cursor getCursorFromTo(Context context, Set<String> calendarIds, long startDay, long endDay) {
        String[] projection = new String[]{BaseColumns._ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION};

        String selection = CalendarContract.Instances.CALENDAR_ID + " IN (" + makePlaceholders(calendarIds.size()) + ")";
        String[] selectionArgs = calendarIds.toArray(new String[calendarIds.size()]);

        String order = CalendarContract.Instances.BEGIN + " ASC LIMIT 1";

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, startDay);
        ContentUris.appendId(builder, endDay);


        return context.getContentResolver().query(builder.build(), projection, selection, selectionArgs, order);

    }

    private String makePlaceholders(int len) {
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
}

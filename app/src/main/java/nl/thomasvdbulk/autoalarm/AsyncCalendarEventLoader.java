package nl.thomasvdbulk.autoalarm;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.support.v4.content.ContextCompat;

import java.util.Set;

import nl.thomasvdbulk.autoalarm.background.CalendarEvent;

public class AsyncCalendarEventLoader extends AsyncTask<MainActivity, Void, Void> {

    private Set<String> calendarIds;
    private long timeToWakeUp;

    AsyncCalendarEventLoader(Set<String> calendarIds, long timeToWakeUp){
        super();
        this.calendarIds = calendarIds;
        this.timeToWakeUp = timeToWakeUp;
    }

    @Override
    protected Void doInBackground(MainActivity... mainActivities) {
        MainActivity mainActivity = mainActivities[0];
        if(ContextCompat.checkSelfPermission(mainActivity, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED){
            return null;
        }

        Calendar calendar = getCalendarAtMidnight();
        long startDay = calendar.getTimeInMillis();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        long endDay = calendar.getTimeInMillis();


        Cursor cur = getCursorFromTo(mainActivity, startDay, endDay);

        // Check if it's necessary to set a timer now, or tomorrow
        if(!cur.moveToNext() || // If we can't find any events today
                // We also check if the begin of the first event of the day, is earlier than the current time, plus some time to wake up
                cur.getLong(cur.getColumnIndex(CalendarContract.Instances.BEGIN)) < Calendar.getInstance().getTimeInMillis() + timeToWakeUp){

            // If one of these are true, we will look for a next event
            calendar = getCalendarAtMidnight();
            calendar.add(Calendar.DATE, 1);
            startDay = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            endDay = calendar.getTimeInMillis();
            cur = getCursorFromTo(mainActivity, startDay, endDay);
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
        mainActivity.setAlarm(calendar, event);

        cur.close();
        return null;
    }

    private Calendar getCalendarAtMidnight(){
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        return calendar;
    }

    private Cursor getCursorFromTo(Context context, long startDay, long endDay){
        String[] projection = new String[] { BaseColumns._ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_LOCATION };

        String selection = CalendarContract.Instances.CALENDAR_ID + " IN ("+makePlaceholders(calendarIds.size()) + ")";
        String[] selectionArgs = calendarIds.toArray(new String[calendarIds.size()]);

        String order = CalendarContract.Instances.BEGIN+" ASC LIMIT 1";

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

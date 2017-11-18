package nl.thomasvdbulk.autoalarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.util.Calendar;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MenuItem;

import nl.thomasvdbulk.autoalarm.background.ApiRequestAlarm;

public class MainActivity extends BaseActivity {
    private static final int PERMISSION_REQUEST_READ_CALENDAR = 1;
    public static final String DATA_CALENDAR_ID_KEY = "nl.thomasvdbulk.autoalarm.calendarids";
    public static final String LOG_TAG = "AutoAlarm debugging";

    private boolean pickedCalendars = false;
    private AlarmManager alarmManager;
    private PendingIntent pendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_main);

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_CALENDAR},
                PERMISSION_REQUEST_READ_CALENDAR);


        alarmManager = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ApiRequestAlarm.class);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Calendar startCalendar = Calendar.getInstance();
//        startCalendar.add(Calendar.DAY_OF_MONTH, 1);
//        startCalendar.add(Calendar.HOUR_OF_DAY, 4);
//        startCalendar.add(Calendar.MINUTE, 2);
        startCalendar.add(Calendar.SECOND, 1);
//        startCalendar.set(Calendar.SECOND, 0);

        long startTime = startCalendar.getTimeInMillis();

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, startTime, pendingIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_READ_CALENDAR: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if(!pickedCalendars) {
                        shouldPickCalendars();
                    }
                } else {
                    //TODO Shit, we don't have permissions
                }
                return;
            }
        }
    }

    public void shouldPickCalendars(){
        if(pickedCalendars){
            return;
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED){
            return;
        }

        Intent intent = new Intent(this, PickCalendarActivity.class);
        startActivity(intent);
    }

    public void retrieveEvents(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED){
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long startDay = calendar.getTimeInMillis();
        calendar.set(Calendar.HOUR, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        long endDay = calendar.getTimeInMillis();

        String[] projection = new String[] { BaseColumns._ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.DTSTART };
        String selection = CalendarContract.Instances.DTSTART + " >= ? AND " + CalendarContract.Instances.DTSTART + "<= ?";
        String[] selectionArgs = new String[] { Long.toString(startDay), Long.toString(endDay) };

        Cursor cur = getContentResolver().query(CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs, null);
        while(cur.moveToNext()){
            Log.d("Calendar info", cur.getString(cur.getColumnIndex(CalendarContract.Instances.TITLE)));
        }
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
}

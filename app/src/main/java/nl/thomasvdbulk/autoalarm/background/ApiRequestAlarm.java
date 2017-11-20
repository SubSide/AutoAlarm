package nl.thomasvdbulk.autoalarm.background;

import android.arch.persistence.room.Room;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

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
        this.db = Room.databaseBuilder(context, AppDatabase.class, "journeys").build();

        CalendarEvent calendarEvent = new CalendarEvent();
        calendarEvent.title = intent.getStringExtra("title");
        calendarEvent.begin = intent.getStringExtra("begin");
        calendarEvent.location = intent.getStringExtra("location");

        PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        try {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
            wakeLock.acquire(WAKE_TIMEOUT);

            new WebRequestTask(calendarEvent, db, wakeLock).execute(context);
        } catch(NullPointerException e){
            Log.d(MainActivity.LOG_TAG, "NPE thrown on acquiring wakelock", e);
        }

    }
}

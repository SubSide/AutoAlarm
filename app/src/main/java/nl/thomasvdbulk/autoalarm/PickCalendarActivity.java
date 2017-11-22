package nl.thomasvdbulk.autoalarm;


import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

public class PickCalendarActivity extends BaseActivity {


    // This is the Adapter being used to display the list's data
    SimpleCursorAdapter mAdapter;

    Set<String> itemIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_calendar);

        // Get and init Recyclerview
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout linearLayout = findViewById(R.id.calendar_list);

        //Get the preferences
        SharedPreferences sharedPref = this.getSharedPreferences(MainActivity.DATA_SHARED_FILE, Context.MODE_PRIVATE);
        itemIds = sharedPref.getStringSet(MainActivity.DATA_CALENDAR_ID_KEY, new HashSet<String>());

        // Load the calendars
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED){
            return;
        }


        String[] projection = new String[] { BaseColumns._ID, CalendarContract.Calendars.NAME };
        Cursor cur = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null);

        View.OnClickListener listener = new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                boolean isSelected = v.getTag(R.string.calendar_selected) != null && (boolean)v.getTag(R.string.calendar_selected);
                // If the tag is null or false, we set it to true and the other way around.
                isSelected = !isSelected;
                v.setTag(R.string.calendar_selected, isSelected);

                String id = ""+v.getTag(R.string.calendar_id);
                if(isSelected){
                    itemIds.add(id);
                } else {
                    itemIds.remove(id);
                }

                // We grab the image so we can check if it is selected or not
                ImageView img = v.findViewById(R.id.checkbox);

                img.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            }
        };

        while(cur.moveToNext()) {
            // Create a new view
            View calendar = inflater.inflate(R.layout.calendar_list_item, null);
            // Set click handler
            calendar.setOnClickListener(listener);

            // Set the ID tag
            calendar.setTag(R.string.calendar_id, cur.getInt(cur.getColumnIndex(BaseColumns._ID)));
            // Set the text to the calendar name
            TextView text = calendar.findViewById(R.id.item);
            text.setText(cur.getString(cur.getColumnIndex(CalendarContract.Calendars.NAME)));

            // If the view was already selected in the past, we want to set the tag to true and show the checkmark
            if(itemIds.contains(""+cur.getInt(cur.getColumnIndex(BaseColumns._ID)))){
                calendar.setTag(R.string.calendar_selected, true);
                calendar.findViewById(R.id.checkbox).setVisibility(View.VISIBLE);
            } else {
                calendar.setTag(R.string.calendar_selected, false);
            }
            linearLayout.addView(calendar);
        }
        cur.close();

        // Now add a button onclick handler
        findViewById(R.id.button_done).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });
    }

    @Override
    public void onBackPressed() {
        SharedPreferences sharedPref = this.getSharedPreferences(MainActivity.DATA_SHARED_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putStringSet(MainActivity.DATA_CALENDAR_ID_KEY, itemIds);
        editor.commit();

        super.onBackPressed();
    }
}

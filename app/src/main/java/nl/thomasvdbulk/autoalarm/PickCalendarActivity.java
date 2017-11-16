package nl.thomasvdbulk.autoalarm;


import android.Manifest;
import android.app.ListActivity;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class PickCalendarActivity extends ListActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {


    // This is the Adapter being used to display the list's data
    SimpleCursorAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_calendar);

        retrieveCalendars();

        // For the cursor adapter, specify which columns go into which views
        String[] fromColumns = {CalendarContract.Calendars.NAME };
        int[] toViews = {android.R.id.text1}; // The TextView in simple_list_item_1

        // Create an empty adapter we will use to display the loaded data.
        // We pass null for the cursor, then update it in onLoadFinished()
        mAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_1, null,
                fromColumns, toViews, 0);
        setListAdapter(mAdapter);

        // Prepare the loader.  Either re-connect with an existing one,
        // or start a new one.
        getLoaderManager().initLoader(0, null, this);
    }

    // Called when a new Loader needs to be created
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Now create and return a CursorLoader that will take care of
        // creating a Cursor for the data being displayed.
        return new CursorLoader(this, CalendarContract.Calendars.CONTENT_URI,
                new String[]{ BaseColumns._ID, CalendarContract.Calendars.NAME }, null, null, null);
    }

    // Called when a previously created loader has finished loading
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in.  (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.swapCursor(data);
    }

    // Called when a previously created loader is reset, making the data unavailable
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Do something when a list item is clicked
    }

    public void retrieveCalendars(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED){
            return;
        }

        String[] projection = new String[] { BaseColumns._ID, CalendarContract.Calendars.NAME, CalendarContract.Calendars.CALENDAR_COLOR };
        Cursor cur = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null);

        while(cur.moveToNext()){
            Log.d("Calendar info", cur.getString(cur.getColumnIndex(CalendarContract.Calendars.NAME)));
        }
    }
}

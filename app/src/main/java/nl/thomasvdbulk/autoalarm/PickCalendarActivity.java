package nl.thomasvdbulk.autoalarm;


import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.CalendarContract;
import android.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

public class PickCalendarActivity extends ListActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {


    // This is the Adapter being used to display the list's data
    SimpleCursorAdapter mAdapter;

    Set<String> itemIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_calendar);

        // For the cursor adapter, specify which columns go into which views
        String[] fromColumns = { CalendarContract.Calendars.NAME, BaseColumns._ID };
        int[] toViews = { R.id.item }; // The TextView in simple_list_item_1

        // Create an empty adapter we will use to display the loaded data.
        // We pass null for the cursor, then update it in onLoadFinished()
        mAdapter = new SimpleCursorAdapter(this,
                R.layout.calendar_list_item, null,
                fromColumns, toViews, 0);
        setListAdapter(mAdapter);

        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder(){

            @Override
            public boolean setViewValue(View view, Cursor cursor, int i) {
                if(view instanceof TextView) {
                    view.setTag(R.string.calendar_id, cursor.getInt(cursor.getColumnIndex(BaseColumns._ID)));
                    view.setTag(R.string.calendar_selected, false);
                    // TODO set selected to true and show image if already selected
                }
                return false;
            }
        });

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
        TextView view = v.findViewById(R.id.item);
        boolean isSelected = view.getTag(R.string.calendar_selected) != null && (boolean)view.getTag(R.string.calendar_selected);
        // If the tag is null or false, we set it to true and the other way around.
        isSelected = !isSelected;
        view.setTag(R.string.calendar_selected, isSelected);

        // We grab the image so we can check if it is selected or not
        ImageView img = v.findViewById(R.id.checkbox);

        img.setVisibility(isSelected ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
//        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
//        SharedPreferences.Editor editor = sharedPref.edit();
//
//
//
//        editor.putStringSet(MainActivity.DATA_CALENDAR_ID_KEY, newHighScore);
//        editor.commit();

        super.onBackPressed();
    }
}

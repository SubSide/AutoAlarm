package nl.thomasvdbulk.autoalarm.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import nl.thomasvdbulk.autoalarm.MainActivity;
import nl.thomasvdbulk.autoalarm.R;

import static android.content.Context.POWER_SERVICE;

public class ApiRequestAlarm extends BroadcastReceiver {

    public static final String WAKE_TAG = "nl.thomasvdbulk.autoalarm.WAKE_TAG";
    public static final long WAKE_TIMEOUT = 10 * 1000;

    public PowerManager.WakeLock wakeLock;

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG);
        wakeLock.acquire(WAKE_TIMEOUT);

//        Map<String, String> args = new HashMap<>();
//        args.put("lang", "en-GB");
//        args.put("from", "station-leiden-lammenschans");
//        args.put("to", "warmond_ganzenwei");
//        args.put("searchType", "departure");
//        args.put("dateTime", "2017-11-25T1230");
//        args.put("sequence", "1");
//        args.put("byTrain", "true");
//        args.put("byBus", "true");
//        args.put("bySubway", "true");
//        args.put("byTram", "true");
//        args.put("byFerry", "true");
//        args.put("interchangeTime", "standard");
//        args.put("planWithAccessibility", "false");
//        args.put("before", "0");
//        args.put("after", "0");
//        args.put("realtime", "true");
//
//        new HttpRequestTask().execute("-----------"+urlEncodeUTF8(args));

        InputStream is = null;
        BufferedReader br = null;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            InputStream raw = context.getResources().openRawResource(R.raw.test);
            is = new BufferedInputStream(raw);
            br = new BufferedReader(new InputStreamReader(is));

            String line;
            String newLine = System.getProperty("line.separator");
            while ((line = br.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(newLine);
            }
            br.close();
        } catch(IOException e){
            Log.d(MainActivity.LOG_TAG, "Error recieving API data", e);
        } finally {
            if(is != null){
                try {
                    is.close();
                } catch(IOException e){
                    Log.d(MainActivity.LOG_TAG, "InputStream couldn't be closed.", e);
                }
            }

            if(br != null){
                try {
                    br.close();
                } catch(IOException e){
                    Log.d(MainActivity.LOG_TAG, "BufferedReader couldn't be closed.", e);
                }
            }
        }
        Log.d(MainActivity.LOG_TAG, stringBuilder.toString());
    }

    public void handleJson(JSONObject object){
        Log.d(MainActivity.LOG_TAG, object.toString());
    }

    class HttpRequestTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            HttpURLConnection urlConnection = null;
            InputStream is = null;
            BufferedReader br = null;
            StringBuilder stringBuilder = new StringBuilder();
            try {
                URL url = new URL(strings[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                is = new BufferedInputStream(urlConnection.getInputStream());
                br = new BufferedReader(new InputStreamReader(is));

                String line;
                String newLine = System.getProperty("line.separator");
                while ((line = br.readLine()) != null) {
                    stringBuilder.append(line);
                    stringBuilder.append(newLine);
                }
                br.close();
            } catch(IOException e){
                Log.d(MainActivity.LOG_TAG, "Error recieving API data", e);
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }

                if(is != null){
                    try {
                        is.close();
                    } catch(IOException e){
                        Log.d(MainActivity.LOG_TAG, "InputStream couldn't be closed.", e);
                    }
                }

                if(br != null){
                    try {
                        br.close();
                    } catch(IOException e){
                        Log.d(MainActivity.LOG_TAG, "BufferedReader couldn't be closed.", e);
                    }
                }
            }
            return stringBuilder.toString();
        }

        @Override
        public void onCancelled(){
            wakeLock.release();
        }

        @Override
        public void onPostExecute(String result){
            try {
                JSONObject obj = new JSONObject(result);
                handleJson(obj);
            } catch(JSONException e){
                Log.d(MainActivity.LOG_TAG, "Error loading JSON Object", e);
            }
            wakeLock.release();
        }
    }

    private String urlEncodeUTF8(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(String.format("%s=%s",
                        URLEncoder.encode(entry.getKey(), "UTF-8"),
                        URLEncoder.encode(entry.getValue(), "UTF-8")
                ));
            }
        } catch(UnsupportedEncodingException e){
            Log.d(MainActivity.LOG_TAG, "Encoding type is not supported!", e);
        }
        return sb.toString();
    }
}

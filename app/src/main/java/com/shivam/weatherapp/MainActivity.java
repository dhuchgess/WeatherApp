package com.shivam.weatherapp;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Time;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
String apiJSONData = "";
private int tempUnitTracker = 0; // 0 for C, 1 for F (extracted from the radioButton via the setTempUnit method)
private int maxTemp, minTemp, currTemp;
private LruCache<String, Bitmap> mBitmapCache;
    private class OpenWeatherDataReceiver extends AsyncTask<String, Void, String>{
        String apiURL = "https://api.openweathermap.org/data/2.5/weather?";
        String apiKEY = BuildConfig.OpenWeatherApiKey;
        @Override
        protected String doInBackground(String... strings) {
            apiURL += "lat=" + strings[0] + "&";   // Adding latitude of the selected place
            apiURL += "lon=" + strings[1] + "&";  // Adding longitude of the selected place
            apiURL += "appid=" + apiKEY + "&";
            apiURL += tempUnitTracker == 0 ? "units=metric" : "units=imperial";   // Force to show temperature in Celsius
            Log.d("apiURL", apiURL);
            try{
                URL url =  new URL(apiURL);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                InputStream inputStream = httpURLConnection.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String read;
                StringBuilder strBuilder = new StringBuilder();
                while ((read = bufferedReader.readLine()) != null)
                {
                    strBuilder.append(read);
                }
                Log.d("Location", strBuilder.toString());
                return strBuilder.toString();
            } catch (Exception e){
                e.printStackTrace();
                return null;
            }
        }
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            parseJSON(s);
        }
    }
    private class WeatherIconDownloader extends AsyncTask<String, Void, Bitmap>{
        @Override
        protected Bitmap doInBackground(String... strings) {
            Bitmap bitmap = getBitmapFromMemCache(strings[0]);
            if (bitmap != null) {
                // Image present in cache memory
                Log.d("BitmapCache", "Image already in memory, loading from cache");
                ((ImageView) findViewById(R.id.weatherIcon)).setImageBitmap(bitmap);
                return bitmap;
            } else {
                // Image not present in cache, hence download and add it to cache memory
                Log.d("BitmapCache", "Image not present in cache memory, downloading and caching now");
                String apiURL = "http://openweathermap.org/img/w/" + strings[0] + ".png";
                Log.d("ImageDownloader", apiURL);
                try {
                    URL url = new URL(apiURL);
                    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                    InputStream is = httpURLConnection.getInputStream();
                    bitmap = BitmapFactory.decodeStream(is);
                    addBitmapToMemoryCache(strings[0], bitmap);
                    return bitmap;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            ((ImageView) findViewById(R.id.weatherIcon)).setImageBitmap(bitmap);
        }
    }

    private void parseJSON(String JsonData){
        try {
            JSONObject jsonObject = new JSONObject(JsonData);
            String str = jsonObject.getString("weather");
            JSONArray jsonArr = new JSONArray(str);
            JSONObject jsonObject1 = new JSONObject(jsonArr.getString(0));

            WeatherIconDownloader wd = new WeatherIconDownloader();
            wd.execute(jsonObject1.getString("icon"));
            ((TextView) findViewById(R.id.description))
                    .setText(jsonObject1.getString("main"));

            Log.d("JSON", jsonObject.getString("main"));
            jsonObject1 = new JSONObject(jsonObject.getString("main"));
            currTemp = jsonObject1.getInt("temp");
            minTemp = jsonObject1.getInt("temp_min");
            maxTemp = jsonObject1.getInt("temp_max");

            ((TextView) findViewById(R.id.currTemp))
                    .setText(getString(R.string.currTemp, currTemp, getTempUnit()));

            ((TextView) findViewById(R.id.minTemp))
                    .setText(getString(R.string.minTemp, minTemp, getTempUnit()));

            ((TextView) findViewById(R.id.maxTemp))
                    .setText(getString(R.string.maxTemp, maxTemp, getTempUnit()));

            ((TextView) findViewById(R.id.pressureTextView))
                    .setText(String.valueOf(jsonObject1.getInt("pressure")));

            ((TextView) findViewById(R.id.humidityTextView))
                    .setText(String.valueOf(jsonObject1.getInt("humidity")));

            // Fetching data from sys:
            jsonObject1 = new JSONObject(jsonObject.getString("sys"));

            Time time = new Time(jsonObject1.getLong("sunrise") * 1000); // Fetching sunrise time and converting to proper unit
            ((TextView) findViewById(R.id.sunriseTextView)).setText(time.toString());

            time = new Time(jsonObject1.getLong("sunset") * 1000);
            ((TextView) findViewById(R.id.sunsetTextView)).setText(time.toString());

        } catch (Exception e){
            e.printStackTrace();
        }

    }

    private void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mBitmapCache.put(key, bitmap);
        }
    }

    private Bitmap getBitmapFromMemCache(String key) {
        return mBitmapCache.get(key);
    }
    // Converts to celsius
    public int toCelsius(int fahrenheit) {
        Log.d("temperatureF", String.valueOf(fahrenheit));
        return ((fahrenheit - 32) * 5 / 9);
    }

    // Converts to fahrenheit
    public int toFahrenheit(int celsius) {
        Log.d("temperatureC", String.valueOf(celsius));
        return ((celsius * 9) / 5) + 32;
    }

    public void setTempUnit(View view){
        if(!(((TextView) findViewById(R.id.currTemp)).getText().toString().isEmpty()) && !(view.getTag().toString().equals(tempUnitTracker))) {
            tempUnitTracker = Integer.parseInt(view.getTag().toString());
            if(view.getTag().toString().equals("0")) {
                currTemp = toCelsius(currTemp);
                minTemp = toCelsius(minTemp);
                maxTemp = toCelsius(maxTemp);
                ((TextView) findViewById(R.id.currTemp))
                        .setText(getString(R.string.currTemp, currTemp, getTempUnit()));
                ((TextView) findViewById(R.id.minTemp))
                        .setText(getString(R.string.minTemp, minTemp, getTempUnit()));
                ((TextView) findViewById(R.id.maxTemp))
                        .setText(getString(R.string.minTemp, maxTemp, getTempUnit()));
            }
            else {
                currTemp = toFahrenheit(currTemp);
                minTemp = toFahrenheit(minTemp);
                maxTemp = toFahrenheit(maxTemp);
                ((TextView) findViewById(R.id.currTemp))
                        .setText(getString(R.string.currTemp, currTemp, getTempUnit()));
                ((TextView) findViewById(R.id.minTemp))
                        .setText(getString(R.string.minTemp, minTemp, getTempUnit()));
                ((TextView) findViewById(R.id.maxTemp))
                        .setText(getString(R.string.minTemp, maxTemp, getTempUnit()));
            }
        }
    }

    private String getTempUnit(){

        return tempUnitTracker == 0 ? getString(R.string.celsiusSymbol) : getString(R.string.fahrenheitSymbol);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final SharedPreferences sharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        double lat = sharedPreferences.getFloat("latitude", 0), lon = sharedPreferences.getFloat("longitude", 0);
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            String cityName = geocoder.getFromLocation(lat, lon, 1).get(0).getLocality();
            ((TextView) findViewById(R.id.placeTextView)).setText(cityName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        SharedPreferences sp = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        tempUnitTracker = sp.getInt("tempUnitTracker", 0);
        RadioGroup radioGroup = findViewById(R.id.tempUnitToggle);
        radioGroup.check(radioGroup.getChildAt(tempUnitTracker).getId());
        OpenWeatherDataReceiver openWeatherDataReceiver = new OpenWeatherDataReceiver();
        openWeatherDataReceiver.execute(String.valueOf(lat), String.valueOf(lon));
        PlaceAutocompleteFragment autocompleteFragment  = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {            @Override
            public void onPlaceSelected(Place place) {
                apiJSONData = "";   // Setting it to an empty string, so that it wont be have previous location data
                sharedPreferences.edit().putFloat("latitude", (float) place.getLatLng().latitude).apply();
                sharedPreferences.edit().putFloat("longitude", (float) place.getLatLng().longitude).apply();
                Log.i("locationFragment", place.getName().toString() + " : getting weather details");
                OpenWeatherDataReceiver openWeatherDataReceiver = new OpenWeatherDataReceiver();
                openWeatherDataReceiver.execute(Double.toString(place.getLatLng().latitude), Double.toString(place.getLatLng().longitude));
                // Setting placeTextView
                ((TextView) findViewById(R.id.placeTextView)).setText(place.getName());
            }

            @Override
            public void onError(Status status) {
                Log.e("locationFragment", "An error occurred: " + status);
            }
        });

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;

        mBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than
                // number of items.
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences sp = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        sp.edit().putInt("tempUnitTracker", tempUnitTracker).apply();
    }
}

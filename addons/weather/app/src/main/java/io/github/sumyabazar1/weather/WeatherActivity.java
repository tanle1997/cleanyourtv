package io.github.sumyabazar1.weather;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.format.TextStyle;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The weather for one place, full screen: now, the air quality and the next days (options.xml). It reads Open-Meteo only
 * while it is on screen (every {@link Forecast#REFRESH_MS}, or {@link Forecast#RETRY_MS} after a failure) and
 * does nothing in the background. The place is chosen once with a search and kept in the app's preferences.
 */
public final class WeatherActivity extends Activity {
    private static final String PREFS = "place";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refresh;
    private SharedPreferences place;
    private View root;
    private View content;
    private TextView message;
    private boolean shown;
    /** options.xml: days after today, and the extras on the "Feels like" line. */
    private int forecastDays;
    private boolean showHumidity;
    private boolean showWind;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_weather);
        place = getSharedPreferences(PREFS, MODE_PRIVATE);
        forecastDays = Forecast.forecastDays(getResources().getInteger(R.integer.forecast_days));
        showHumidity = getResources().getBoolean(R.bool.show_humidity);
        showWind = getResources().getBoolean(R.bool.show_wind);
        root = findViewById(R.id.root);
        content = findViewById(R.id.content);
        message = findViewById(R.id.message);
        View changeCity = findViewById(R.id.changeCity);
        changeCity.setOnClickListener(v -> pickCity());
        changeCity.requestFocus();
        if (!place.contains("name")) {
            message.setText(R.string.pick_first);
            pickCity();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (place.contains("name")) refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);   // nothing runs while the page is not on screen
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private double getLat() {
        String s = place.getString("lat_str", null);
        if (s != null) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return Double.longBitsToDouble(place.getLong("lat", 0));
    }

    private double getLon() {
        String s = place.getString("lon_str", null);
        if (s != null) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return Double.longBitsToDouble(place.getLong("lon", 0));
    }

    private void savePlace(String name, double lat, double lon) {
        place.edit()
                .putString("name", name)
                .putString("lat_str", String.valueOf(lat))
                .putString("lon_str", String.valueOf(lon))
                .putLong("lat", Double.doubleToRawLongBits(lat))
                .putLong("lon", Double.doubleToRawLongBits(lon))
                .apply();
    }

    private void refresh() {
        main.removeCallbacks(tick);
        io.execute(() -> {
            double lat = getLat();
            double lon = getLon();

            // Auto-detect GPS location via IP if no city is manually set
            if (lat == 0 && lon == 0) {
                Forecast.Place auto = Forecast.autoPlace();
                if (auto != null) {
                    savePlace(auto.name, auto.latitude, auto.longitude);
                    lat = auto.latitude;
                    lon = auto.longitude;
                }
            }

            Forecast.Report report;
            try {
                report = Forecast.fetch(lat, lon, forecastDays);
            } catch (Exception e) {
                report = null;
            }
            Forecast.Report result = report;
            main.post(() -> {
                if (isDestroyed()) return;
                if (result != null) {
                    show(result);
                } else {
                    if (!shown) message.setText(R.string.offline);
                    Intent noDataIntent = new Intent("com.gathin.tvhome.WEATHER_UPDATE");
                    noDataIntent.putExtra("weatherNoData", true);
                    sendBroadcast(noDataIntent);
                }
                main.postDelayed(tick, result != null ? Forecast.REFRESH_MS : Forecast.RETRY_MS);
            });
        });
    }

    private void show(Forecast.Report r) {
        shown = true;
        root.setBackground(Forecast.backdrop(r.code, r.day));
        ((TextView) findViewById(R.id.place)).setText(place.getString("name", ""));
        ((TextView) findViewById(R.id.temp)).setText(r.tempC + "°");
        ((TextView) findViewById(R.id.sky)).setText(getWeatherEmoji(r.code) + " " + getString(Forecast.sky(r.code, r.day)));
        StringBuilder feels = new StringBuilder(getString(R.string.feels_like, r.feelsC));
        if (showHumidity) feels.append(" · ").append(getString(R.string.humidity, r.humidity));
        if (showWind) feels.append(" · ").append(getString(R.string.wind, r.windKmh));
        ((TextView) findViewById(R.id.feels)).setText(feels);

        TextView air = findViewById(R.id.airQuality);
        if (r.aqi >= 0) {
            air.setText(getString(R.string.aqi_line, r.aqi, getString(Forecast.aqiLabel(r.aqi))));
            air.setTextColor(Forecast.aqiTextColour(r.aqi));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Forecast.aqiColour(r.aqi));
            pill.setCornerRadius(100 * getResources().getDisplayMetrics().density);
            air.setBackground(pill);
            air.setVisibility(View.VISIBLE);
        } else {
            air.setVisibility(View.GONE);
        }

        LinearLayout days = findViewById(R.id.days);
        days.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        // More than three days: the cards share the row, with short day names, so a week fits on screen.
        boolean compact = r.days.size() > Forecast.MIN_DAYS;
        if (compact) days.getLayoutParams().width = LinearLayout.LayoutParams.MATCH_PARENT;
        for (int i = 0; i < r.days.size(); i++) {
            Forecast.Day d = r.days.get(i);
            View card = inflater.inflate(R.layout.item_day, days, false);
            ((TextView) card.findViewById(R.id.dayName)).setText(d.date.getDayOfWeek()
                    .getDisplayName(compact ? TextStyle.SHORT : TextStyle.FULL, Locale.getDefault()));
            ((TextView) card.findViewById(R.id.daySky)).setText(getWeatherEmoji(d.code) + " " + getString(Forecast.sky(d.code, true)));
            TextView temps = card.findViewById(R.id.dayTemps);
            temps.setText(getString(R.string.high_low, d.maxC, d.minC));
            if (compact) {
                int pad = Math.round(14 * getResources().getDisplayMetrics().density);
                card.setPadding(pad, pad, pad, pad);
                TextView sky = card.findViewById(R.id.daySky);
                sky.setTextSize(15);
                sky.setSingleLine(false);   // "Partly cloudy" on two lines rather than cut; every card two lines tall
                sky.setLines(2);
                temps.setTextSize(21);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginEnd(i < r.days.size() - 1 ? Math.round(12 * getResources().getDisplayMetrics().density) : 0);
                card.setLayoutParams(lp);
            }
            days.addView(card);
        }

        ((TextView) findViewById(R.id.updated))
                .setText(getString(R.string.updated, DateFormat.getTimeFormat(this).format(new Date())));
        message.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        findViewById(R.id.changeCity).requestFocus();

        // Broadcast weather data to the launcher for canvas display
        Intent weatherIntent = new Intent("com.gathin.tvhome.WEATHER_UPDATE");
        String cityName = place.getString("name", "HÀ NỘI").toUpperCase(Locale.getDefault());
        if (cityName.length() > 22) {
            cityName = cityName.substring(0, 20) + "…";
        }

        weatherIntent.putExtra("weatherToday", cityName + " · " + r.tempC + "°C  " + getString(Forecast.sky(r.code, r.day)));
        weatherIntent.putExtra("weatherFeelsLike", "(Cảm giác " + r.feelsC + "°)");
        weatherIntent.putExtra("weatherSummary", getUpcomingWeatherSummary(r));
        if (r.days.size() >= 3) {
            Forecast.Day d1 = r.days.get(0);
            Forecast.Day d2 = r.days.get(1);
            Forecast.Day d3 = r.days.get(2);
            String day1Name = d1.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("vi", "VN")).toUpperCase(Locale.getDefault());
            String day2Name = d2.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("vi", "VN")).toUpperCase(Locale.getDefault());
            String day3Name = d3.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("vi", "VN")).toUpperCase(Locale.getDefault());
            weatherIntent.putExtra("day1Text", day1Name + ": " + d1.maxC + "°/" + d1.minC + "° " + getWeatherEmoji(d1.code));
            weatherIntent.putExtra("day2Text", day2Name + ": " + d2.maxC + "°/" + d2.minC + "° " + getWeatherEmoji(d2.code));
            weatherIntent.putExtra("day3Text", day3Name + ": " + d3.maxC + "°/" + d3.minC + "° " + getWeatherEmoji(d3.code));
        }
        sendBroadcast(weatherIntent);
    }

    private static String getWeatherEmoji(int code) {
        if (code == 0) return "☀️";
        if (code == 1 || code == 2) return "⛅";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "☁️"; // Use cloud emoji instead of fog to avoid grey box
        if (code >= 51 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code >= 85 && code <= 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "☁️";
    }

    private String getUpcomingWeatherSummary(Forecast.Report r) {
        if (r.hoursUntilRain > 0) {
            if (r.isStorm) {
                return getString(R.string.weather_alert_thunderstorm_in_hours, r.hoursUntilRain);
            } else {
                return getString(R.string.weather_alert_rain_in_hours, r.hoursUntilRain);
            }
        }
        int code = r.code;
        if (code >= 95) return getString(R.string.weather_summary_thunderstorm);
        if (code >= 80 && code <= 82) return getString(R.string.weather_summary_rain);
        if (code >= 51 && code <= 67) return getString(R.string.weather_summary_rain);
        if (code >= 45 && code <= 48) return getString(R.string.weather_summary_fog);
        if (code >= 71 && code <= 77) return getString(R.string.weather_summary_snow);
        if (code == 0 || code == 1) return getString(R.string.weather_summary_sunny);
        if (code == 2 || code == 3) return getString(R.string.weather_summary_cloudy);
        return getString(R.string.weather_summary_default);
    }

    private void resetToAutoLocation() {
        place.edit().clear().apply();
        shown = false;
        message.setText(R.string.loading);
        message.setVisibility(View.VISIBLE);
        content.setVisibility(View.GONE);

        Intent resetIntent = new Intent("com.gathin.tvhome.WEATHER_UPDATE");
        resetIntent.putExtra("weatherReset", true);
        sendBroadcast(resetIntent);

        refresh();
    }

    /** Custom TV-optimized single-dialog search: In-place results, no screen flicker. */
    private void pickCity() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pick_city, null);
        EditText name = dialogView.findViewById(R.id.cityNameInput);
        TextView searchStatus = dialogView.findViewById(R.id.searchStatus);
        LinearLayout resultsContainer = dialogView.findViewById(R.id.resultsContainer);
        View btnSearch = dialogView.findViewById(R.id.btnSearch);
        View btnAuto = dialogView.findViewById(R.id.btnAuto);

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams wlp = dialog.getWindow().getAttributes();
            wlp.y = Math.round(32 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setAttributes(wlp);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }

        // Hide soft keyboard when D-pad focus leaves input field
        name.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        });

        Runnable doSearch = () -> {
            String query = name.getText().toString().trim();
            if (query.isEmpty()) return;

            searchStatus.setText("Đang tìm kiếm " + query + "...");
            searchStatus.setVisibility(View.VISIBLE);
            resultsContainer.removeAllViews();
            resultsContainer.setVisibility(View.GONE);

            io.execute(() -> {
                List<Forecast.Place> found;
                try {
                    found = Forecast.search(query);
                } catch (Exception e) {
                    found = null;
                }
                List<Forecast.Place> places = found;
                main.post(() -> {
                    if (isDestroyed() || !dialog.isShowing()) return;
                    searchStatus.setVisibility(View.GONE);
                    if (places == null || places.isEmpty()) {
                        searchStatus.setText(getString(R.string.pick_none, query));
                        searchStatus.setVisibility(View.VISIBLE);
                    } else {
                        resultsContainer.removeAllViews();
                        for (Forecast.Place p : places) {
                            TextView item = new TextView(WeatherActivity.this);
                            String label = p.detail.isEmpty() ? p.name : p.name + " · " + p.detail;
                            item.setText(label);
                            item.setTextColor(getResources().getColorStateList(R.color.btn_tv_text, getTheme()));
                            item.setBackgroundResource(R.drawable.btn_tv_selector);
                            int pad = Math.round(10 * getResources().getDisplayMetrics().density);
                            item.setPadding(pad * 2, pad, pad * 2, pad);
                            item.setTextSize(15);
                            item.setFocusable(true);
                            item.setClickable(true);

                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            lp.setMargins(0, 0, 0, Math.round(6 * getResources().getDisplayMetrics().density));
                            item.setLayoutParams(lp);

                            item.setOnClickListener(v -> {
                                dialog.dismiss();
                                savePlace(p.name, p.latitude, p.longitude);
                                message.setText(R.string.loading);
                                message.setVisibility(View.VISIBLE);
                                content.setVisibility(View.GONE);
                                refresh();
                            });
                            resultsContainer.addView(item);
                        }

                        // Hide soft keyboard so D-pad focus moves directly to results
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(name.getWindowToken(), 0);
                        }

                        resultsContainer.setVisibility(View.VISIBLE);
                        if (resultsContainer.getChildCount() > 0) {
                            View firstItem = resultsContainer.getChildAt(0);
                            firstItem.post(() -> {
                                firstItem.setFocusable(true);
                                firstItem.requestFocus();
                            });
                        }
                    }
                });
            });
        };

        btnSearch.setOnClickListener(v -> doSearch.run());

        btnAuto.setOnClickListener(v -> {
            dialog.dismiss();
            resetToAutoLocation();
        });

        name.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                doSearch.run();
                return true;
            }
            return false;
        });

        dialog.show();
        name.requestFocus();
    }
}

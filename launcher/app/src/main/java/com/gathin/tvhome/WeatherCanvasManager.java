package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * Self-contained Manager for Weather Canvas UI, 0-ms Cache, Uninstallation checks,
 * and Can Chi Vietnamese Lunar Calendar computation.
 */
public class WeatherCanvasManager {

    private static final String WEATHER_PKG = "io.github.sumyabazar1.weather";
    public static final IntentFilter WEATHER_FILTER = new IntentFilter("com.gathin.tvhome.WEATHER_UPDATE");

    public static boolean isWeatherAppInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(WEATHER_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void clearWeatherCache(Context context, View weatherPanel) {
        context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove("cache_weather_today")
                .remove("cache_weather_feels")
                .remove("cache_weather_summary")
                .remove("cache_day1")
                .remove("cache_day2")
                .remove("cache_day3")
                .apply();
        if (weatherPanel != null) weatherPanel.setVisibility(View.GONE);
    }

    public static void loadCachedWeather(Context context, TextView mainInfo, TextView feels, TextView summary,
                                          TextView d1, TextView d2, TextView d3, View weatherPanel) {
        if (!isWeatherAppInstalled(context)) {
            clearWeatherCache(context, weatherPanel);
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String today = prefs.getString("cache_weather_today", null);
        if (today != null && !today.isEmpty()) {
            if (mainInfo != null) mainInfo.setText(today);
            String f = prefs.getString("cache_weather_feels", null);
            if (f != null && feels != null) feels.setText(f);
            String s = prefs.getString("cache_weather_summary", null);
            if (s != null && !s.isEmpty() && summary != null) {
                summary.setText(s);
                summary.setVisibility(View.VISIBLE);
            }
            String day1 = prefs.getString("cache_day1", null);
            if (day1 != null && d1 != null) d1.setText(day1);
            String day2 = prefs.getString("cache_day2", null);
            if (day2 != null && d2 != null) d2.setText(day2);
            String day3 = prefs.getString("cache_day3", null);
            if (day3 != null && d3 != null) d3.setText(day3);

            if (weatherPanel != null) weatherPanel.setVisibility(View.VISIBLE);
        }
    }

    public static BroadcastReceiver createWeatherReceiver(final TextView mainInfo, final TextView feels,
                                                          final TextView summary, final TextView d1,
                                                          final TextView d2, final TextView d3,
                                                          final View weatherPanel) {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.gathin.tvhome.WEATHER_UPDATE".equals(intent.getAction()) && weatherPanel != null) {
                    if (intent.getBooleanExtra("weatherReset", false)) {
                        clearWeatherCache(context, weatherPanel);
                        if (mainInfo != null) mainInfo.setText(R.string.loading);
                        if (feels != null) feels.setText("");
                        if (summary != null) summary.setVisibility(View.GONE);
                        weatherPanel.setVisibility(View.VISIBLE);
                        return;
                    }

                    if (intent.getBooleanExtra("weatherNoData", false)) {
                        clearWeatherCache(context, weatherPanel);
                        if (mainInfo != null) mainInfo.setText(R.string.no_weather_data);
                        if (feels != null) feels.setText("");
                        if (summary != null) summary.setVisibility(View.GONE);
                        weatherPanel.setVisibility(View.VISIBLE);
                        return;
                    }

                    String today = intent.getStringExtra("weatherToday");
                    String feelStr = intent.getStringExtra("weatherFeelsLike");
                    String sumStr = intent.getStringExtra("weatherSummary");
                    String day1Str = intent.getStringExtra("day1Text");
                    String day2Str = intent.getStringExtra("day2Text");
                    String day3Str = intent.getStringExtra("day3Text");

                    boolean hasData = false;
                    if (today != null && !today.isEmpty()) {
                        if (mainInfo != null) mainInfo.setText(today);
                        hasData = true;
                    }
                    if (feelStr != null && feels != null) feels.setText(feelStr);
                    if (sumStr != null && !sumStr.isEmpty() && summary != null) {
                        summary.setText(sumStr);
                        summary.setVisibility(View.VISIBLE);
                    }
                    if (day1Str != null && d1 != null) d1.setText(day1Str);
                    if (day2Str != null && d2 != null) d2.setText(day2Str);
                    if (day3Str != null && d3 != null) d3.setText(day3Str);

                    if (hasData) {
                        weatherPanel.setVisibility(View.VISIBLE);
                    }

                    SharedPreferences.Editor ed = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit();
                    if (today != null) ed.putString("cache_weather_today", today);
                    if (feelStr != null) ed.putString("cache_weather_feels", feelStr);
                    if (sumStr != null) ed.putString("cache_weather_summary", sumStr);
                    if (day1Str != null) ed.putString("cache_day1", day1Str);
                    if (day2Str != null) ed.putString("cache_day2", day2Str);
                    if (day3Str != null) ed.putString("cache_day3", day3Str);
                    ed.apply();
                }
            }
        };
    }

    public static String getLunarDateString() {
        Calendar cal = Calendar.getInstance();
        Calendar base = Calendar.getInstance();
        base.set(2026, Calendar.OCTOBER, 24);

        long diffMillis = cal.getTimeInMillis() - base.getTimeInMillis();
        long diffDays = diffMillis / (24 * 60 * 60 * 1000);

        int lunarDay = (int) (15 + diffDays);
        int lunarMonth = 9;

        while (lunarDay > 30) {
            lunarDay -= 30;
            lunarMonth++;
        }
        while (lunarDay < 1) {
            lunarDay += 30;
            lunarMonth--;
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth < 1) lunarMonth += 12;

        String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
        String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};
        int year = cal.get(Calendar.YEAR);
        int canIdx = (year - 4) % 10;
        if (canIdx < 0) canIdx += 10;
        int chiIdx = (year - 4) % 12;
        if (chiIdx < 0) chiIdx += 12;
        String canChiYear = CAN[canIdx] + " " + CHI[chiIdx];

        return String.format(Locale.getDefault(), "%02d/%02d Âm lịch • %s", lunarDay, lunarMonth, canChiYear);
    }
}

package com.gathin.tvhome;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

public class UsageTimerService extends Service {

    private static long tvUsedSeconds = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            checkAndTick();
            handler.postDelayed(this, 10_000L);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
        return START_STICKY;
    }

    private void checkAndTick() {
        SharedPreferences prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        boolean locked = prefs.getBoolean("parental_lock_enabled", false);
        long limitMin = prefs.getLong("parental_lock_limit_minutes", 0);

        if (limitMin > 0 && !locked) {
            tvUsedSeconds += 10;
            if (tvUsedSeconds >= limitMin * 60) {
                prefs.edit()
                        .putBoolean("parental_lock_enabled", true)
                        .putLong("tv_used_seconds", tvUsedSeconds)
                        .commit();

                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        long now = SystemClock.uptimeMillis();
                        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
                        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
                        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP, 0));
                        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP, 0));
                    }
                } catch (Exception ignored) {}

                Intent homeIntent = new Intent(this, MainActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(homeIntent);
            }
        }
    }

    public static void resetTimer() {
        tvUsedSeconds = 0;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

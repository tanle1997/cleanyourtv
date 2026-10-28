package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

public class ParentalLockAlarmReceiver extends BroadcastReceiver {

    public static final int ALARM_REQ_CODE = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        // 1. Enable parental lock state
        context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("parental_lock_enabled", true)
                .commit();

        // 2. Use Accessibility Service to perform Global HOME action and kick foreground apps out
        HomeKeyService service = HomeKeyService.getInstance();
        if (service != null) {
            service.forceGoHomeAndLock();
        } else {
            // Fallback
            try {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    long now = SystemClock.uptimeMillis();
                    am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
                    am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
                    am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP, 0));
                    am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP, 0));
                }
            } catch (Exception ignored) {}

            Intent homeIntent = new Intent(context, MainActivity.class);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(homeIntent);
        }
    }

    public static void scheduleAlarm(Context context, long limitMinutes) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ParentalLockAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (am != null) {
            if (limitMinutes > 0) {
                long triggerAtMs = SystemClock.elapsedRealtime() + (limitMinutes * 60 * 1000L);
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pi);
            } else {
                am.cancel(pi);
            }
        }
    }

    public static void cancelAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ParentalLockAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, ALARM_REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am != null) {
            am.cancel(pi);
        }
    }
}

package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.widget.TextView;

import java.util.Locale;

/**
 * Self-contained Manager for 5-second dynamic RAM/ROM usage polling.
 * Keeps MainActivity decoupled and clean.
 */
public class StatsMonitorManager {

    private final Handler statsHandler = new Handler(Looper.getMainLooper());
    private Context context;
    private TextView statsText;

    private final Runnable statsRunnable = new Runnable() {
        @Override
        public void run() {
            updateStats();
            statsHandler.postDelayed(this, 5000L);
        }
    };

    public void start(Context context, TextView statsText) {
        this.context = context;
        this.statsText = statsText;
        statsHandler.removeCallbacks(statsRunnable);
        statsHandler.post(statsRunnable);
    }

    public void stop() {
        statsHandler.removeCallbacks(statsRunnable);
    }

    private void updateStats() {
        if (context == null || statsText == null) return;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) am.getMemoryInfo(mi);

            long freeRamMb = mi.availMem / (1024 * 1024);
            long totalRamMb = mi.totalMem / (1024 * 1024);

            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long freeRomBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            long freeRomGbInt = freeRomBytes / (1024 * 1024 * 1024);
            long freeRomGbDec = (freeRomBytes % (1024 * 1024 * 1024)) / (100 * 1024 * 1024);

            String text = String.format(Locale.ROOT, "BỘ NHỚ %d / %d MB  ·  CÒN TRỐNG %d,%d GB",
                    totalRamMb - freeRamMb, totalRamMb, freeRomGbInt, freeRomGbDec);
            statsText.setText(text);
        } catch (Exception ignored) {}
    }
}

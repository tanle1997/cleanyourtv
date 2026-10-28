package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.view.KeyEvent;

/**
 * Self-contained Manager for TV Input/Source key checks, HDMI/AV source queries,
 * and system input handler toggles. Decouples MainActivity.
 */
public class TvInputSourceManager {

    public static boolean isInputKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_TV_INPUT // 178
                || keyCode == KeyEvent.KEYCODE_AVR_INPUT // 182
                || keyCode == KeyEvent.KEYCODE_MEDIA_TOP_MENU // 226
                || keyCode == 259 // KEYCODE_TV_MEDIA_CONTEXT_MENU
                || keyCode == 227 // KEYCODE_TV_ANTENNA_CABLE
                || keyCode == KeyEvent.KEYCODE_TV_DATA_SERVICE // 170
                || keyCode == 235 || keyCode == 236 || keyCode == 237;
    }

    public static void applyViewInputsHandler(Context context, boolean on) {
        ComponentName handler = new ComponentName(context, InputsRequestActivity.class);
        PackageManager pm = context.getPackageManager();
        int want = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        if (pm.getComponentEnabledSetting(handler) != want) {
            pm.setComponentEnabledSetting(handler, want, PackageManager.DONT_KILL_APP);
        }
    }

    public static boolean hasAvailableInputs(Context context, boolean showInputsPill, String sourcePanelService, boolean coocaaSources, Intent liveTvIntent) {
        if (!showInputsPill) return false;
        if (liveTvIntent != null || !sourcePanelService.isEmpty() || (coocaaSources && !CoocaaSources.list(context).isEmpty())) {
            return true;
        }
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && !info.isHidden(context)) {
                    return true;
                }
            }
        }
        return false;
    }
}

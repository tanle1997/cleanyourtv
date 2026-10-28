package com.gathin.tvhome;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Self-contained Manager for Top Navigation Bar buttons, uniform 42dp icon sizing,
 * dead-center icon alignment, and expand-on-focus description updates.
 */
public class TopBarManager {

    private boolean wifiFocused = false;

    public boolean isWifiFocused() {
        return wifiFocused;
    }

    public void setupTopButtons(final MainActivity activity, final View btnInputs, final View btnWifi,
                                final View btnTimerLock, final View btnSettings, final boolean launcherSettingsButton) {
        int btnWidth = activity.getResources().getDimensionPixelSize(R.dimen.top_button_icon_size);
        int padIcon = activity.getResources().getDimensionPixelSize(R.dimen.top_button_padding_icon);
        int padStart = activity.getResources().getDimensionPixelSize(R.dimen.top_button_padding_start);
        int padEnd = activity.getResources().getDimensionPixelSize(R.dimen.top_button_padding_end);

        View.OnFocusChangeListener topButtonFocusListener = (v, hasFocus) -> {
            if (v instanceof TextView tv) {
                int id = v.getId();
                ViewGroup.LayoutParams lp = tv.getLayoutParams();
                if (hasFocus) {
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    tv.setLayoutParams(lp);
                    tv.setPadding(padStart, padIcon, padEnd, padIcon);
                    if (id == R.id.btnWifi) {
                        wifiFocused = true;
                        activity.updateWifiLabel();
                    } else if (id == R.id.btnSettings) {
                        tv.setText(activity.getString(launcherSettingsButton ? R.string.launcher_settings : R.string.settings));
                    } else if (id == R.id.btnInputs) {
                        tv.setText(activity.getString(R.string.inputs));
                    }
                } else {
                    lp.width = btnWidth;
                    tv.setLayoutParams(lp);
                    tv.setPadding(padIcon, padIcon, padIcon, padIcon);
                    tv.setGravity(Gravity.CENTER);
                    if (id == R.id.btnWifi) {
                        wifiFocused = false;
                        activity.updateWifiLabel();
                    } else if (id == R.id.btnSettings || id == R.id.btnInputs) {
                        tv.setContentDescription(tv.getText());
                        tv.setText(null);
                    }
                }
            }
        };

        for (View btn : new View[]{btnInputs, btnWifi, btnTimerLock, btnSettings}) {
            if (btn != null) {
                ViewGroup.LayoutParams lp = btn.getLayoutParams();
                lp.width = btnWidth;
                btn.setLayoutParams(lp);
                btn.setPadding(padIcon, padIcon, padIcon, padIcon);
                if (btn instanceof TextView) {
                    ((TextView) btn).setGravity(Gravity.CENTER);
                    ((TextView) btn).setText(null);
                }
                btn.setOnFocusChangeListener(topButtonFocusListener);
            }
        }
    }
}

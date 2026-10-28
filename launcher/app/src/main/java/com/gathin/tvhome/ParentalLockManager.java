package com.gathin.tvhome;

import android.app.AlertDialog;

import androidx.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Self-contained Manager for Parental Lock, Screen Time Limit, 6-Digit Dynamic PIN,
 * and Whitelist App Checking. Keeps MainActivity decoupled and lightweight.
 */
public class ParentalLockManager {

    private static final String PREF_PARENTAL_LOCK = "parental_lock_enabled";
    private static final String PREF_LOCK_LIMIT = "parental_lock_limit_minutes";
    private static final String PREF_USED_SECONDS = "tv_used_seconds";
    private static final String PREF_LOCK_WHITELIST = "parental_lock_whitelist";

    private boolean parentalLockEnabled = false;
    private long tvUsedSeconds = 0;
    private Set<String> parentalLockWhitelist;

    public void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        parentalLockEnabled = prefs.getBoolean(PREF_PARENTAL_LOCK, false);
        tvUsedSeconds = prefs.getLong(PREF_USED_SECONDS, 0);
        if (!parentalLockEnabled) {
            ParentalLockAlarmReceiver.cancelAlarm(context);
        }
    }

    public boolean isParentalLocked() {
        return parentalLockEnabled;
    }

    public void setParentalLockEnabled(Context context, boolean enabled) {
        this.parentalLockEnabled = enabled;
        context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_PARENTAL_LOCK, enabled)
                .apply();
    }

    public boolean isPackageWhitelisted(Context context, String pkg) {
        if (pkg == null) return false;
        if (parentalLockWhitelist == null) {
            SharedPreferences prefs = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            Set<String> saved = prefs.getStringSet(PREF_LOCK_WHITELIST, null);
            if (saved != null) {
                parentalLockWhitelist = new HashSet<>(saved);
            } else {
                String[] defaults = context.getResources().getStringArray(R.array.default_parental_lock_whitelist);
                parentalLockWhitelist = new HashSet<>(Arrays.asList(defaults));
            }
        }
        return parentalLockWhitelist.contains(pkg);
    }

    public void showLockBlockedToast(MainActivity activity) {
        Toast.makeText(activity, activity.getString(R.string.parental_lock_blocked_toast), Toast.LENGTH_SHORT).show();
        showPinUnlockDialog(activity);
    }

    public void handleTimerLockClick(MainActivity activity) {
        if (parentalLockEnabled) {
            showPinUnlockDialog(activity);
        } else {
            showTimerLockOptions(activity);
        }
    }

    public void showTimerLockOptions(MainActivity activity) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_launcher_modern, null);
        TextView title = v.findViewById(R.id.dialogTitle);
        title.setText(R.string.timer_lock_title);
        LinearLayout optionsContainer = v.findViewById(R.id.optionsContainer);
        optionsContainer.setVisibility(View.VISIBLE);

        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(v)
                .create();

        CharSequence[] options = new CharSequence[]{
                activity.getString(R.string.timer_lock_option_timer),
                activity.getString(R.string.timer_lock_option_lock),
                activity.getString(R.string.timer_lock_option_usage)
        };

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView item = new TextView(activity);
            item.setText(options[i]);
            item.setTextColor(activity.getResources().getColorStateList(R.color.launcher_btn_text, activity.getTheme()));
            item.setBackgroundResource(R.drawable.launcher_btn_selector);
            int pad = Math.round(12 * activity.getResources().getDisplayMetrics().density);
            item.setPadding(pad * 2, pad, pad * 2, pad);
            item.setTextSize(16);
            item.setFocusable(true);
            item.setClickable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, Math.round(8 * activity.getResources().getDisplayMetrics().density));
            item.setLayoutParams(lp);

            item.setOnClickListener(v1 -> {
                dialog.dismiss();
                if (index == 0) {
                    activity.showSleepTimerDialog();
                } else if (index == 1) {
                    setParentalLockEnabled(activity, true);
                    activity.updateLockIcon();
                    Toast.makeText(activity, activity.getString(R.string.parental_lock_enabled_toast), Toast.LENGTH_LONG).show();
                } else if (index == 2) {
                    showUsageTimerDialog(activity);
                }
            });
            optionsContainer.addView(item);
        }

        dialog.show();
        if (optionsContainer.getChildCount() > 0) {
            optionsContainer.getChildAt(0).requestFocus();
        }
    }

    public void showUsageTimerDialog(MainActivity activity) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_launcher_modern, null);
        TextView title = v.findViewById(R.id.dialogTitle);
        title.setText(R.string.timer_lock_usage_title);
        LinearLayout optionsContainer = v.findViewById(R.id.optionsContainer);
        optionsContainer.setVisibility(View.VISIBLE);

        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(v)
                .create();

        CharSequence[] times = activity.getResources().getStringArray(R.array.timer_options_labels);
        final int[] minutes = activity.getResources().getIntArray(R.array.timer_minutes);

        for (int i = 0; i < times.length; i++) {
            final int index = i;
            TextView item = new TextView(activity);
            item.setText(times[i]);
            item.setTextColor(activity.getResources().getColorStateList(R.color.launcher_btn_text, activity.getTheme()));
            item.setBackgroundResource(R.drawable.launcher_btn_selector);
            int pad = Math.round(10 * activity.getResources().getDisplayMetrics().density);
            item.setPadding(pad * 2, pad, pad * 2, pad);
            item.setTextSize(15);
            item.setFocusable(true);
            item.setClickable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, Math.round(6 * activity.getResources().getDisplayMetrics().density));
            item.setLayoutParams(lp);

            item.setOnClickListener(v1 -> {
                dialog.dismiss();
                if (index >= 0 && index < minutes.length) {
                    int min = minutes[index];
                    tvUsedSeconds = 0;
                    SharedPreferences prefs = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putLong(PREF_LOCK_LIMIT, min)
                            .putLong(PREF_USED_SECONDS, 0)
                            .apply();
                    ParentalLockAlarmReceiver.scheduleAlarm(activity, min);
                    if (min == 0) {
                        Toast.makeText(activity, activity.getString(R.string.timer_lock_usage_canceled), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, activity.getString(R.string.timer_lock_usage_set, min), Toast.LENGTH_LONG).show();
                    }
                }
            });
            optionsContainer.addView(item);
        }

        dialog.show();
        if (optionsContainer.getChildCount() > 0) {
            optionsContainer.getChildAt(0).requestFocus();
        }
    }

    public void showPinUnlockDialog(MainActivity activity) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_launcher_modern, null);
        TextView title = v.findViewById(R.id.dialogTitle);
        title.setText(R.string.parental_lock_title_enter_pin);
        EditText pinInput = v.findViewById(R.id.dialogInput);
        pinInput.setHint("");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setTransformationMethod(new ImmediatePasswordTransformationMethod());
        pinInput.setShowSoftInputOnFocus(false);
        pinInput.setVisibility(View.VISIBLE);

        View buttonRow = v.findViewById(R.id.buttonRow);
        buttonRow.setVisibility(View.VISIBLE);
        TextView btnConfirm = v.findViewById(R.id.btnConfirm);
        TextView btnCancel = v.findViewById(R.id.btnCancel);
        btnConfirm.setText(R.string.btn_confirm);
        btnCancel.setText(R.string.btn_cancel);

        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(v)
                .create();

        btnConfirm.setOnClickListener(v1 -> {
            String typed = pinInput.getText().toString().trim();
            if (typed.equals(getDynamic6DigitPin())) {
                setParentalLockEnabled(activity, false);
                tvUsedSeconds = 0;
                UsageTimerService.resetTimer();
                ParentalLockAlarmReceiver.cancelAlarm(activity);
                activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_PARENTAL_LOCK, false)
                        .putLong(PREF_LOCK_LIMIT, 0)
                        .putLong(PREF_USED_SECONDS, 0)
                        .apply();
                activity.updateLockIcon();
                Toast.makeText(activity, activity.getString(R.string.parental_lock_unlocked_toast), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(activity, activity.getString(R.string.parental_lock_incorrect_pin), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(v1 -> dialog.dismiss());

        pinInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() == 6) {
                    btnConfirm.performClick();
                }
            }
        });

        pinInput.setOnEditorActionListener((v1, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE) {
                btnConfirm.performClick();
                return true;
            }
            return false;
        });

        dialog.show();
        pinInput.requestFocus();
    }

    public String getDynamic6DigitPin() {
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_MONTH);
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        return String.format(Locale.ROOT, "%02d%02d%02d", day, hour, minute);
    }

    private static class ImmediatePasswordTransformationMethod extends PasswordTransformationMethod {
        @NonNull
        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            return new PasswordCharSequence(source);
        }

        private static class PasswordCharSequence implements CharSequence {
            private final CharSequence source;
            PasswordCharSequence(CharSequence source) { this.source = source; }
            @Override public int length() { return source.length(); }
            @Override public char charAt(int index) { return '•'; }
            @NonNull
            @Override public CharSequence subSequence(int start, int end) { return source.subSequence(start, end); }
        }
    }
}

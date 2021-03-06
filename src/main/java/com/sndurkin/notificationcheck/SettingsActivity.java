package com.sndurkin.notificationcheck;


import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

// This PreferenceActivity is the main activity for the application,
// as it mostly runs in the background.
public class SettingsActivity extends PreferenceActivity {

    enum WhatToCheck {
        ALL_NOTIFICATIONS,
        ONLY_SELECTED_NOTIFICATIONS,
        ALL_BUT_SELECTED_NOTIFICATIONS
    }
    public static final String PREF_WHAT_DEFAULT = String.valueOf(WhatToCheck.ALL_NOTIFICATIONS.ordinal());

    private static final int ACCESSIBILITY_ALERT_DIALOG = 0;
    private static final int HELP_DIALOG = 1;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setupUI();
        launchHelpIfApplicable();
        startService(new Intent(this, ScreenOnService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();

        final CheckBoxPreference prefActive = (CheckBoxPreference) findPreference("pref_active");
        if(!isNotificationServiceEnabled()) {
            prefActive.setChecked(false);
        }
    }

    private void setupUI() {
        addPreferencesFromResource(R.xml.pref_general);

        final CheckBoxPreference prefActive = (CheckBoxPreference) findPreference("pref_active");
        if(!isNotificationServiceEnabled()) {
            prefActive.setChecked(false);
        }
        prefActive.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                if(prefActive.isChecked() && !isNotificationServiceEnabled()) {
                    prefActive.setChecked(false);
                    showDialog(ACCESSIBILITY_ALERT_DIALOG);
                }
                return true;
            }
        });

        final Preference prefNotifications  = findPreference("pref_notifications");
        final ListPreference prefWhatToCheck = (ListPreference) findPreference("pref_what");
        prefWhatToCheck.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                bindPreferenceSummaryListener.onPreferenceChange(preference, newValue);

                int whatToCheck = Integer.parseInt(newValue.toString());
                prefNotifications.setEnabled(whatToCheck != WhatToCheck.ALL_NOTIFICATIONS.ordinal());

                return true;
            }
        });
        initPreferenceSummaryValue(prefWhatToCheck);

        int whatToCheck = Integer.parseInt(prefWhatToCheck.getValue().toString());
        prefNotifications.setEnabled(whatToCheck != WhatToCheck.ALL_NOTIFICATIONS.ordinal());

        findPreference("pref_help").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showDialog(HELP_DIALOG);
                return true;
            }
        });
    }

    // Launches a Help dialog if this is the first run of the application.
    private void launchHelpIfApplicable() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean previouslyStarted = preferences.getBoolean("pref_previously_started", false);
        if(!previouslyStarted) {
            SharedPreferences.Editor edit = preferences.edit();
            edit.putBoolean("pref_previously_started", Boolean.TRUE);
            edit.commit();

            showDialog(HELP_DIALOG);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id) {
            case ACCESSIBILITY_ALERT_DIALOG:
                return new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.accessibility_alert_title)
                    .setMessage(R.string.accessibility_alert_message)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                            }
                            catch (ActivityNotFoundException e) {
                                startActivity(new Intent(Settings.ACTION_SETTINGS));
                            }
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .create();
            case HELP_DIALOG:
                return new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle(R.string.help_dialog_title)
                    .setMessage(R.string.help_dialog_message)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(null, null)
                    .create();
            default:
                return null;
        }
    }

    private boolean isNotificationServiceEnabled(){
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(this.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        }
        catch (Settings.SettingNotFoundException e) {
            //Log.d("NotificationCheck", "Error finding setting, default accessibility to not found: " + e.getMessage());
        }

        if (accessibilityEnabled == 1) {
            //Log.d("NotificationCheck", "Accessibility is enabled");

            String accessibilityServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            //Log.d("NotificationCheck", "Enabled accessibility services: " + accessibilityServices);
            if (accessibilityServices != null) {
                for(String accessibilityService : accessibilityServices.split(":")) {
                    if (accessibilityService.equalsIgnoreCase(NotificationService.SERVICE_NAME)){
                        return true;
                    }
                }
            }
        }
        else {
            //Log.d("NotificationCheck", "Accessibility is disabled");
        }

        return false;
    }

    // A preference value change listener that updates the preference's summary to reflect its new value.
    private static Preference.OnPreferenceChangeListener bindPreferenceSummaryListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if(preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index]
                        : null);
            }
            else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    // Binds a preference's summary to its value. More specifically, when the
    // preference's value is changed, its summary (line of text below the
    // preference title) is updated to reflect the value. The summary is also
    // immediately updated upon calling this method. The exact display format is
    // dependent on the type of preference.
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(bindPreferenceSummaryListener);
        initPreferenceSummaryValue(preference);
    }

    private static void initPreferenceSummaryValue(Preference preference) {
        String val = PreferenceManager.getDefaultSharedPreferences(preference.getContext())
                                      .getString(preference.getKey(), "");
        bindPreferenceSummaryListener.onPreferenceChange(preference, val);
    }

}
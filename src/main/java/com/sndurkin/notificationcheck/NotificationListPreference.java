package com.sndurkin.notificationcheck;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Debug;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Sean on 6/12/13.
 */
public class NotificationListPreference extends DialogPreference {

    private static Map<String, Drawable> CACHED_APP_ICONS = new HashMap<String, Drawable>();

    private static final String SEPARATOR = "|";
    private static final String SEPARATOR_REGEX = "\\|";

    private List<String> appPackageNames = new ArrayList<String>();

    private CharSequence[] entries;
    private CharSequence[] entryValues;
    private String value;
    private String summary;
    private boolean [] selectedIndices;

    public NotificationListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NotificationListPreference(Context context) {
        this(context, null);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        updateEntries();
        updateSummary();
        return view;
    }

    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        updateEntries();
        builder.setAdapter(new AppAdapter(getContext()), null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        ArrayList<String> values = new ArrayList<String>();
        if (positiveResult && entryValues != null) {
            for(int i = 0; i < entryValues.length; ++i) {
                if(selectedIndices[i]) {
                    values.add(entryValues[i].toString());
                }
            }

            if (callChangeListener(values)) {
                saveList(values);
            }
        }
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if(restoreValue) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            value = preferences.getString("pref_notifications", "");
        }
        else {
            value = (String) defaultValue;
        }
    }

    // Fetches all applications and lists them as entries.
    private void updateEntries() {
        final PackageManager pm = getContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);

        Set<String> duplicateAppNames = findDuplicateAppNames(apps);

        List<String> selectedPackages = fetchList();
        List<App> sortedApps = new ArrayList<App>();
        for (ApplicationInfo appInfo : apps) {
            App app = new App();
            app.name = pm.getApplicationLabel(appInfo).toString().trim();
            app.packageName = appInfo.packageName;

            if(app.name.equals(app.packageName)) {
                // Don't include applications that don't have a proper name.
                continue;
            }

            if(duplicateAppNames.contains(app.name)) {
                // If there's a duplicate application name, we want to append the package name
                // to eliminate confusion.
                app.name = app.name + " (" + app.packageName + ")";
            }

            sortedApps.add(app);
        }

        // Sort by checked first, then alphabetically.
        Collections.sort(sortedApps, new AppComparator(selectedPackages));
        List<String> appNames = new ArrayList<String>();
        List<String> appPackageNames = new ArrayList<String>();
        for(App app : sortedApps) {
            appNames.add(app.name);
            appPackageNames.add(app.packageName);
        }
        this.appPackageNames = appPackageNames;

        entries = appNames.toArray(new String[0]);
        entryValues = appPackageNames.toArray(new String[0]);
        selectedIndices = new boolean[appNames.size()];

        restoreSelectedEntries();
    }

    private void restoreSelectedEntries() {
        for(int i = 0; i < selectedIndices.length; ++i) {
            selectedIndices[i] = false;
        }

        List<String> values = fetchList();
        if(!values.isEmpty()) {
            for(int i = 0; i < entryValues.length; ++i) {
                if(values.contains(entryValues[i])) {
                    selectedIndices[i] = true;
                }
            }
        }
    }

    // Returns the TODO
    public static List<String> extractListFromPref(String prefVal) {
        if(prefVal.equals("")) {
            return new ArrayList<String>();
        }
        else {
            return Arrays.asList(prefVal.split(SEPARATOR_REGEX));
        }
    }

    // Extracts the serialized string saved in SharedPreferences.
    private List<String> fetchList() {
        if(value == null) {
            value = "";
        }

        Log.d("NotificationCheck", "Extracting list from pref: " + value);
        return extractListFromPref(value.toString());
    }

    // Joins the string and stores the serialized value in SharedPreferences.
    private void saveList(List<String> values) {
        Iterator<String> iter = values.iterator();
        StringBuilder sb = new StringBuilder();
        if(iter.hasNext()) {
            sb.append(iter.next());
        }
        while(iter.hasNext()) {
            sb.append(SEPARATOR).append(iter.next());
        }
        value = sb.toString();
        Log.d("NotificationCheck", "Saving list to pref: " + value);
        persistString(value);
        updateSummary();
    }

    private void updateSummary() {
        List<String> values = fetchList();
        if(values.isEmpty()) {
            setSummary(getContext().getString(R.string.pref_notifications_summary_pattern_none));
        }
        else {
            setSummary(String.format(getContext().getString(R.string.pref_notifications_summary_pattern), values.size()));
        }
    }

    private Set<String> findDuplicateAppNames(List<ApplicationInfo> apps) {
        final PackageManager pm = getContext().getPackageManager();

        HashSet<String> appNames = new HashSet<String>();
        HashSet<String> duplicateAppNames = new HashSet<String>();
        for(ApplicationInfo app : apps) {
            String appName = pm.getApplicationLabel(app).toString().trim();
            if(appNames.contains(appName)) {
                duplicateAppNames.add(appName);
            }
            appNames.add(appName);
        }
        return duplicateAppNames;
    }

    class App {
        public String name;
        public String packageName;
    }

    class AppComparator implements Comparator<App> {

        private List<String> selectedPackages;

        public AppComparator(List<String> selectedPackages) {
            this.selectedPackages = selectedPackages;
        }

        @Override
        public int compare(App a, App b) {
            boolean selectedA = selectedPackages.contains(a.packageName);
            boolean selectedB = selectedPackages.contains(b.packageName);
            if(selectedA && !selectedB) {
                return -1;
            }
            else if(!selectedA && selectedB) {
                return 1;
            }

            return a.name.compareTo(b.name);
        }
    }

    class AppAdapter extends BaseAdapter {

        private LayoutInflater inflater;
        private PackageManager pm;

        public AppAdapter(Context context) {
            inflater = LayoutInflater.from(context);
            pm = context.getPackageManager();
        }

        @Override
        public int getCount() {
            return entries.length;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CustomHolder holder = null;

            convertView = inflater.inflate(R.layout.list_preference_row, parent, false);
            holder = new CustomHolder(convertView, position);
            convertView.setTag(holder);

            return convertView;
        }

        class CustomHolder {
            private ImageView iconView = null;
            private TextView textView = null;
            private CheckBox checkbox = null;

            CustomHolder(final View row, final int position) {
                row.setClickable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        v.requestFocus();
                        selectedIndices[position] = !selectedIndices[position];
                        checkbox.setChecked(selectedIndices[position]);
                    }
                });

                textView = (TextView) row.findViewById(R.id.row_text);
                textView.setText(entries[position]);

                iconView = (ImageView) row.findViewById(R.id.row_icon);

                String packageName = entryValues[position].toString();
                Drawable icon;
                if(CACHED_APP_ICONS.containsKey(packageName)) {
                    icon = CACHED_APP_ICONS.get(packageName);
                }
                else {
                    try {
                        icon = pm.getApplicationIcon(entryValues[position].toString());
                    }
                    catch(PackageManager.NameNotFoundException e) {
                        icon = getContext().getResources().getDrawable(R.drawable.ic_launcher);
                    }

                    CACHED_APP_ICONS.put(packageName, icon);
                }
                iconView.setImageDrawable(icon);
                iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                checkbox = (CheckBox) row.findViewById(R.id.row_check);
                // TODO: (remove this) checkbox.setId(position);
                checkbox.setClickable(true);
                checkbox.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        row.performClick();
                    }
                });
                checkbox.setChecked(selectedIndices[position]);
            }
        }
    }

}

package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    /** The rows Launcher settings can switch on and off, in panel order; Launcher settings itself always shows. */
    static final String[] ROW_KEYS = {"quick_tv_settings", "quick_display", "quick_network", "quick_bluetooth", "quick_apps"};
    static final int[] ROW_TITLES = {R.string.tv_settings, R.string.quick_picture, R.string.quick_network,
            R.string.quick_bluetooth, R.string.quick_apps};
    static final boolean[] ROW_DEFAULTS = {true, false, false, true, true};
    private static final int[] ROW_ICONS = {R.drawable.ic_settings, R.drawable.ic_picture, R.drawable.ic_ethernet,
            R.drawable.ic_bluetooth, R.drawable.ic_apps};
    private static final int[] ROW_HINTS = {0, 0, 0, 0, R.string.quick_apps_hint};
    // TV Settings answers its own action, not Settings.ACTION_BLUETOOTH_SETTINGS (TCL G09: "Remotes & Accessories").
    private static final String[] ROW_ACTIONS = {Settings.ACTION_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS,
            Settings.ACTION_WIFI_SETTINGS, BluetoothPill.ACCESSORIES,
            Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS};

    static final String ORDER_KEY = "quick_order";
    /** Opened from Home (MainActivity.quickPanel()): add the Home rows, which Home itself runs. */
    static final String EXTRA_HOME = "home", EXTRA_MODULE_CARDS = "module_cards", EXTRA_DOCK_ROW = "dock_row";
    /** Sent back to Home with ACTION_LAUNCHER_SETTINGS: module_cards, dock_edit or reorder. */
    static final String EXTRA_HOME_ROW = "home_row";

    static boolean shows(SharedPreferences choices, int row) {
        return choices.getBoolean(ROW_KEYS[row], ROW_DEFAULTS[row]);
    }

    /** The rows in the order chosen in Launcher settings, as indexes into ROW_KEYS; rows missing from the
     *  saved order (one added in a later build) follow in their built-in order. */
    static int[] order(SharedPreferences choices) {
        java.util.List<Integer> rows = new java.util.ArrayList<>();
        for (String key : choices.getString(ORDER_KEY, "").split(",")) {
            int i = java.util.Arrays.asList(ROW_KEYS).indexOf(key);
            if (i >= 0 && !rows.contains(i)) rows.add(i);
        }
        for (int i = 0; i < ROW_KEYS.length; i++) if (!rows.contains(i)) rows.add(i);
        int[] out = new int[rows.size()];
        for (int i = 0; i < out.length; i++) out[i] = rows.get(i);
        return out;
    }

    static void saveOrder(SharedPreferences choices, int[] order) {
        StringBuilder keys = new StringBuilder();
        for (int i : order) keys.append(keys.length() == 0 ? "" : ",").append(ROW_KEYS[i]);
        choices.edit().putString(ORDER_KEY, keys.toString()).apply();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        SharedPreferences choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        for (int i : order(choices)) {
            if (shows(choices, i)) {
                addRow(rows, ROW_ICONS[i], ROW_TITLES[i], ROW_HINTS[i], SystemSettings.intent(this, ROW_ACTIONS[i]));
            }
        }
        Intent from = getIntent();
        if (from.getBooleanExtra(EXTRA_HOME, false)) {
            if (from.getBooleanExtra(EXTRA_MODULE_CARDS, false)) addRow(rows, R.drawable.ic_module_cards, R.string.module_cards, 0, homeRow("module_cards"));
            if (from.getBooleanExtra(EXTRA_DOCK_ROW, false)) addRow(rows, R.drawable.ic_dock_edit, R.string.dock_edit, 0, homeRow("dock_edit"));
            addRow(rows, R.drawable.ic_reorder, R.string.reorder, 0, homeRow("reorder"));
        }
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        rows.getChildAt(0).requestFocus();
    }

    private Intent homeRow(String row) {
        return new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS).putExtra(EXTRA_HOME_ROW, row);
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }
}

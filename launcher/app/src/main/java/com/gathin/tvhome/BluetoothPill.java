package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.SparseArray;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * config bluetooth_pill: the network pill shows the connected Bluetooth audio device instead of the network,
 * by the name it was given in TV Settings, with a glyph for its kind (earbuds, headphones), a plain Bluetooth pill
 * when none is connected, or a crossed-out mark when the adapter is off. Only audio profiles count, so the remote's own Bluetooth link never shows. Needs BLUETOOTH_CONNECT
 * on Android 12 and later, granted over adb; without it the pill stays plain.
 */
final class BluetoothPill {
    /** TV Settings' "Remotes & Accessories"; Settings.ACTION_BLUETOOTH_SETTINGS opens nothing on TCL Google TV. */
    static final String ACCESSORIES = "com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES";
    private static final int[] PROFILES = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
    /** Not in the public SDK; sent when a device is renamed in TV Settings. */
    private static final String ALIAS_CHANGED = "android.bluetooth.device.action.ALIAS_CHANGED";

    private final Activity activity;
    private final TextView pill;
    private final SparseArray<BluetoothProfile> proxies = new SparseArray<>();
    private BluetoothAdapter adapter;
    private boolean started;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }
    };

    private final BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (!started) {
                adapter.closeProfileProxy(profile, proxy);
                return;
            }
            proxies.put(profile, proxy);
            update();
        }

        @Override
        public void onServiceDisconnected(int profile) {
            proxies.remove(profile);
            update();
        }
    };

    BluetoothPill(Activity activity, TextView pill) {
        this.activity = activity;
        this.pill = pill;
        BluetoothManager bm = activity.getSystemService(BluetoothManager.class);
        adapter = bm != null ? bm.getAdapter() : null;
    }

    void start() {
        started = true;
        IntentFilter filter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(ALIAS_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
        if (adapter != null && allowed()) {
            for (int profile : PROFILES) {
                try {
                    adapter.getProfileProxy(activity, listener, profile);
                } catch (SecurityException ignored) {
                }
            }
        }
        update();
    }

    void stop() {
        started = false;
        activity.unregisterReceiver(receiver);
        for (int i = 0; i < proxies.size(); i++) adapter.closeProfileProxy(proxies.keyAt(i), proxies.valueAt(i));
        proxies.clear();
    }

    Intent settings() {
        return SystemSettings.intent(activity, ACCESSORIES);
    }

    private boolean allowed() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void update() {
        BluetoothDevice device = connectedDevice();
        String name = null, label = null;
        try {
            if (device != null) {
                name = device.getName();
                // getName() is the device's own name; a rename in TV Settings is its alias.
                label = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? device.getAlias() : name;
            }
        } catch (SecurityException ignored) {
        }
        if (device == null) {
            boolean on = isOn();
            pill.setText(R.string.quick_bluetooth);
            pill.setCompoundDrawablesRelativeWithIntrinsicBounds(on ? R.drawable.ic_bluetooth : R.drawable.ic_bluetooth_off, 0, 0, 0);
            pill.setContentDescription(activity.getString(on ? R.string.quick_bluetooth : R.string.bluetooth_off));
            return;
        }
        // The glyph goes by the device's own name, so a renamed pair of AirPods keeps the earbuds.
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(glyph(device, name), 0, 0, 0);
        if (label == null || label.isEmpty()) label = name;
        pill.setText(label != null && !label.isEmpty() ? label : activity.getString(R.string.quick_bluetooth));
        pill.setContentDescription(pill.getText());
    }

    /** Reading the adapter's state needs no permission, so this shows even before BLUETOOTH_CONNECT is granted. */
    private boolean isOn() {
        try {
            return adapter != null && adapter.isEnabled();
        } catch (SecurityException e) {
            return false;
        }
    }

    private BluetoothDevice connectedDevice() {
        if (adapter == null || !allowed()) return null;
        try {
            if (!adapter.isEnabled()) return null;
            for (int profile : PROFILES) {
                BluetoothProfile proxy = proxies.get(profile);
                if (proxy == null) continue;
                List<BluetoothDevice> devices = proxy.getConnectedDevices();
                if (!devices.isEmpty()) return devices.get(0);
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    /** AirPods and other earbuds by name, headphones by device class, otherwise the Bluetooth mark. */
    private static int glyph(BluetoothDevice device, String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("airpods max")) return R.drawable.ic_headphones;
        if (n.contains("airpods") || n.contains("buds")) return R.drawable.ic_earbuds;
        BluetoothClass cls = null;
        try {
            cls = device.getBluetoothClass();
        } catch (SecurityException ignored) {
        }
        if (cls != null) {
            switch (cls.getDeviceClass()) {
                case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                    return R.drawable.ic_headphones;
                default:
                    break;
            }
        }
        return R.drawable.ic_bluetooth;
    }
}

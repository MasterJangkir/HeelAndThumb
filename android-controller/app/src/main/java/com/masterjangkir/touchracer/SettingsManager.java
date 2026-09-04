package com.masterjangkir.touchracer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages persistent configuration settings via SharedPreferences.
 */
public class SettingsManager {

    private static final String PREF_NAME = "touch_racer_enhanced_prefs";

    private static final String KEY_HOST = "target_host";
    private static final String KEY_PORT = "target_port";
    private static final String KEY_CONN_TYPE = "conn_type";
    private static final String KEY_BT_ADDRESS = "bt_address";
    private static final String KEY_STEER_SENSITIVITY = "steer_sensitivity";
    private static final String KEY_STEER_DEADZONE = "steer_deadzone";
    private static final String KEY_INVERT_PEDALS = "invert_pedals";
    private static final String KEY_MIX_MODE = "mix_mode";
    private static final String KEY_PEDAL_MODE = "pedal_mode";
    private static final String KEY_VOLUME_SHIFTER = "volume_shifter";
    private static final String KEY_HAPTIC = "haptic_enabled";
    private static final String KEY_REVERSE_LANDSCAPE = "reverse_landscape";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getHost() {
        return prefs.getString(KEY_HOST, "192.168.1.100");
    }

    public void setHost(String host) {
        prefs.edit().putString(KEY_HOST, host).apply();
    }

    public int getPort() {
        return prefs.getInt(KEY_PORT, NetworkManager.DEFAULT_PORT);
    }

    public void setPort(int port) {
        prefs.edit().putInt(KEY_PORT, port).apply();
    }

    public NetworkManager.ConnectionType getConnectionType() {
        String name = prefs.getString(KEY_CONN_TYPE, NetworkManager.ConnectionType.WIFI_TCP.name());
        try {
            return NetworkManager.ConnectionType.valueOf(name);
        } catch (Exception e) {
            return NetworkManager.ConnectionType.WIFI_TCP;
        }
    }

    public void setConnectionType(NetworkManager.ConnectionType type) {
        prefs.edit().putString(KEY_CONN_TYPE, type.name()).apply();
    }

    public String getBluetoothAddress() {
        return prefs.getString(KEY_BT_ADDRESS, "");
    }

    public void setBluetoothAddress(String address) {
        prefs.edit().putString(KEY_BT_ADDRESS, address).apply();
    }

    public float getSteeringSensitivity() {
        return prefs.getFloat(KEY_STEER_SENSITIVITY, 90.0f);
    }

    public void setSteeringSensitivity(float degrees) {
        prefs.edit().putFloat(KEY_STEER_SENSITIVITY, degrees).apply();
    }

    public float getSteeringDeadzone() {
        return prefs.getFloat(KEY_STEER_DEADZONE, 0.02f);
    }

    public void setSteeringDeadzone(float ratio) {
        prefs.edit().putFloat(KEY_STEER_DEADZONE, ratio).apply();
    }

    public boolean isInvertPedals() {
        return prefs.getBoolean(KEY_INVERT_PEDALS, false);
    }

    public void setInvertPedals(boolean invert) {
        prefs.edit().putBoolean(KEY_INVERT_PEDALS, invert).apply();
    }

    public int getMixMode() {
        return prefs.getInt(KEY_MIX_MODE, TouchRacerPacket.MIX_ANALOG_BLEND);
    }

    public void setMixMode(int mode) {
        prefs.edit().putInt(KEY_MIX_MODE, mode).apply();
    }

    public int getPedalControlMode() {
        return prefs.getInt(KEY_PEDAL_MODE, PedalTouchView.CONTROL_MODE_ABSOLUTE);
    }

    public void setPedalControlMode(int mode) {
        prefs.edit().putInt(KEY_PEDAL_MODE, mode).apply();
    }

    public boolean isVolumeShifterEnabled() {
        return prefs.getBoolean(KEY_VOLUME_SHIFTER, true);
    }

    public void setVolumeShifterEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOLUME_SHIFTER, enabled).apply();
    }

    public boolean isHapticEnabled() {
        return prefs.getBoolean(KEY_HAPTIC, true);
    }

    public void setHapticEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply();
    }

    public boolean isReverseLandscape() {
        return prefs.getBoolean(KEY_REVERSE_LANDSCAPE, false);
    }

    public void setReverseLandscape(boolean reverse) {
        prefs.edit().putBoolean(KEY_REVERSE_LANDSCAPE, reverse).apply();
    }
}

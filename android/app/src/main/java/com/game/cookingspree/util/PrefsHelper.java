package com.game.cookingspree.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.game.cookingspree.BaseActivity;

import java.util.Map;
import java.util.HashMap;

public final class PrefsHelper {
    public interface GameSaveVerifier { boolean isValid(Map<String, ?> storedValues); }
    public interface SyncAdapter {
        void updateSetting(String key, Object value);
        void updateStat(String key, Object value);
        void updateProfileField(String key, Object value);
    }

    private static SharedPreferences prefs;
    private static SharedPreferences gameSavePrefs;
    private static SyncAdapter syncAdapter;
    private static final CloudSyncGate syncGate = new CloudSyncGate();

    private PrefsHelper() {}

    public static void init(Context context, SyncAdapter adapter) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences("chef_prefs", Context.MODE_PRIVATE);
        gameSavePrefs = appContext.getSharedPreferences("GameSave", Context.MODE_PRIVATE);
        syncAdapter = adapter;
    }

    /** Apply server values to the local cache without echoing them back to the server. */
    public static void withoutCloudSync(Runnable action) {
        syncGate.withoutSync(action);
    }

    private static void write(Runnable localWrite, Runnable cloudWrite) {
        LocalFirstWrite.run(localWrite, () -> {
            if (syncAdapter != null) syncGate.runIfAllowed(cloudWrite);
        }, failure -> Log.w("PrefsSync", "Cloud sync request failed (" + failure.getClass().getSimpleName() + ")"));
    }

    public static void setVolume(int volume) {
        write(() -> prefs.edit().putInt("settings_volume", volume).apply(),
                () -> syncAdapter.updateSetting("volume", volume));
    }
    public static int getVolume() { return prefs.getInt("settings_volume", 100); }

    public static void setJoystickScale(float scale) {
        write(() -> prefs.edit().putFloat("settings_joystick_scale", scale).apply(),
                () -> syncAdapter.updateSetting("joystickScale", scale));
    }
    public static float getJoystickScale() { return prefs.getFloat("settings_joystick_scale", BaseActivity.JOYSTICK_SCALE_DEFAULT); }

    public static void setLanguage(String langCode) {
        write(() -> prefs.edit().putString("settings_language", langCode).apply(),
                () -> syncAdapter.updateSetting("language", langCode));
    }
    public static String getLanguage() { return prefs.getString("settings_language", "en"); }

    public static void setGamesPlayed(int count) {
        write(() -> prefs.edit().putInt("stats_games_played", count).apply(),
                () -> syncAdapter.updateStat("gamesPlayed", count));
    }
    public static int getGamesPlayed() { return prefs.getInt("stats_games_played", 0); }

    public static void setAverageScore(float score) {
        write(() -> prefs.edit().putFloat("stats_average_score", score).apply(),
                () -> syncAdapter.updateStat("averageScore", score));
    }
    public static float getAverageScore() { return prefs.getFloat("stats_average_score", 0f); }

    public static void setHighScore(int score) {
        write(() -> prefs.edit().putInt("stats_high_score", score).apply(),
                () -> syncAdapter.updateStat("highScore", score));
    }
    public static int getHighScore() { return prefs.getInt("stats_high_score", 0); }

    public static void setChefName(String name) {
        write(() -> prefs.edit().putString("profile_chef_name", name).apply(),
                () -> syncAdapter.updateProfileField("chefName", name));
    }
    public static String getChefName() { return prefs.getString("profile_chef_name", "No chef name"); }
    public static void clearChefName() { prefs.edit().remove("profile_chef_name").apply(); }

    public static void setChefCode(String code) {
        write(() -> prefs.edit().putString("profile_chef_code", code).apply(),
                () -> syncAdapter.updateProfileField("chefCode", code));
    }
    public static String getChefCode() { return prefs.getString("profile_chef_code", ""); }

    public static void setPhotoUrl(String url) {
        write(() -> prefs.edit().putString("profile_photo_url", url).apply(),
                () -> syncAdapter.updateProfileField("photoUrl", url));
    }
    public static String getPhotoUrl() { return prefs.getString("profile_photo_url", ""); }

    public static void setDailyStreak(int count) {
        write(() -> prefs.edit().putInt("profile_daily_streak", count).apply(),
                () -> syncAdapter.updateProfileField("dailyStreak", count));
    }
    public static int getDailyStreak() { return prefs.getInt("profile_daily_streak", 0); }

    public static void clearAll() { prefs.edit().clear().apply(); clearSaveState(); }
    public static synchronized void clearSaveState() { gameSavePrefs.edit().clear().commit(); }
    public static synchronized boolean writeGameSaveValues(Map<String, ?> values) {
        return writeGameSaveValues(values, stored -> values.equals(stored));
    }
    public static synchronized boolean writeGameSaveValues(Map<String, ?> values, GameSaveVerifier verifier) {
        if (gameSavePrefs == null || values == null || verifier == null) return false;
        for (Object value : values.values()) {
            if (!(value instanceof String || value instanceof Integer || value instanceof Float
                    || value instanceof Long || value instanceof Boolean)) return false;
        }
        Map<String, ?> previous = new HashMap<>(gameSavePrefs.getAll());
        SharedPreferences.Editor editor = gameSavePrefs.edit().clear();
        putValues(editor, values);
        boolean verified = false;
        try {
            verified = editor.commit() && values.equals(gameSavePrefs.getAll())
                    && verifier.isValid(new HashMap<>(gameSavePrefs.getAll()));
        } catch (RuntimeException failure) {
            Log.w("GameSave", "Stored save verification failed (" + failure.getClass().getSimpleName() + ")");
        }
        if (verified) return true;
        SharedPreferences.Editor rollback = gameSavePrefs.edit().clear();
        putValues(rollback, previous);
        boolean restored = rollback.commit() && previous.equals(gameSavePrefs.getAll());
        if (!restored) Log.e("GameSave", "Failed to restore the previous verified save");
        return false;
    }

    private static void putValues(SharedPreferences.Editor editor, Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        }
    }
    public static boolean hasSyncedThisSession() { return prefs.getBoolean("synced_once", false); }
    public static void setSyncedThisSession(boolean synced) { prefs.edit().putBoolean("synced_once", synced).apply(); }
    public static Map<String, ?> getAll() { return prefs.getAll(); }
}

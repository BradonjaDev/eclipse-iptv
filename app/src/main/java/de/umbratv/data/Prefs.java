package de.umbratv.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private final SharedPreferences p;

    public Prefs(Context c) {
        p = c.getApplicationContext().getSharedPreferences("umbra", Context.MODE_PRIVATE);
    }

    /** Die gerade verwendete Playlist, 0 wenn noch keine eingerichtet ist. */
    public int source() {
        return p.getInt("source", 0);
    }

    public void source(int id) {
        p.edit().putInt("source", id).apply();
    }

    public boolean adult() {
        return p.getBoolean("adult", false);
    }

    public void adult(boolean v) {
        p.edit().putBoolean("adult", v).apply();
    }

    /** Sender als HLS statt MPEG-TS abrufen. */
    public boolean hls() {
        return p.getBoolean("hls", false);
    }

    public void hls(boolean v) {
        p.edit().putBoolean("hls", v).apply();
    }

    public boolean autoRefresh() {
        return p.getBoolean("autoRefresh", true);
    }

    public void autoRefresh(boolean v) {
        p.edit().putBoolean("autoRefresh", v).apply();
    }

    public int resize() {
        return p.getInt("resize", 0);
    }

    public void resize(int v) {
        p.edit().putInt("resize", v).apply();
    }

    public String lastCategory(int kind) {
        return p.getString("cat" + kind, "");
    }

    public void lastCategory(int kind, String ext) {
        p.edit().putString("cat" + kind, ext).apply();
    }
}

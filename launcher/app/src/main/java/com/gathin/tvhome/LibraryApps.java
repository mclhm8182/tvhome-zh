package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The apps the user put on the desk by hand.
 *
 * <p>Most apps on a TV declare no TV (leanback) entry point, so they never show up as tiles on
 * their own: a video app installed from a sideloaded APK is simply invisible on the desk. This
 * keeps the ones the user picked, so the desk can show them next to the apps that do declare one.
 *
 * <p>Stored as component names ("package/activity"), not package names: an app may declare more
 * than one entry point, and the tile has to open the one that was picked.
 */
final class LibraryApps {

    /** A desk full of tiles is nobody's idea of a desk; the list stops here. */
    static final int MAX_ADDED = 128;

    private static final String PREFS = "library";
    private static final String KEY_APPS = "apps";

    private final SharedPreferences prefs;

    LibraryApps(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The components the user added, in the order they were added. Never null. */
    List<String> added() {
        String raw = prefs.getString(KEY_APPS, "");
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) {
            s = s.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    boolean isAdded(String component) {
        return added().contains(component);
    }

    /** Puts the app on the desk. Does nothing when the list is already full. */
    boolean add(String component) {
        List<String> list = added();
        if (list.contains(component)) return true;
        if (list.size() >= MAX_ADDED) return false;
        list.add(component);
        write(list);
        return true;
    }

    /** Takes the app off the desk. */
    void remove(String component) {
        List<String> list = added();
        list.remove(component);
        write(list);
    }

    private void write(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (s == null || s.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(s.trim());
        }
        prefs.edit().putString(KEY_APPS, sb.toString()).apply();
    }

    /** The apps on the desk, without duplicates and in the order they were added. */
    static Set<String> asSet(List<String> list) {
        return new LinkedHashSet<>(list);
    }
}

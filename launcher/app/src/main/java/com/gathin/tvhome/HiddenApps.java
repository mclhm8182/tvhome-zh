package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The apps the user took off the desk.
 *
 * <p>A TV with a few dozen sideloaded video apps has a desk where nothing can be found. The
 * compiled-in {@code hidden_apps} list in config.xml settles that once, at build time; this settles
 * it from the sofa, on the box the user actually owns. Nothing is uninstalled and nothing is
 * disabled — the app stays in the system's own list and comes back the moment it is restored here.
 *
 * <p>Stored as package names, not components: an app that declares several entry points is hidden
 * as a whole, and an app that is later updated keeps its place in the list.
 */
final class HiddenApps {

    private static final String PREFS = "hidden";
    private static final String KEY = "packages";

    private final SharedPreferences prefs;

    HiddenApps(Context ctx) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The hidden packages, in alphabetical order. Never null. */
    List<String> all() {
        return new ArrayList<>(new TreeSet<>(prefs.getStringSet(KEY, new TreeSet<>())));
    }

    boolean isHidden(String pkg) {
        return prefs.getStringSet(KEY, new TreeSet<>()).contains(pkg);
    }

    void hide(String pkg) {
        TreeSet<String> set = new TreeSet<>(prefs.getStringSet(KEY, new TreeSet<>()));
        if (set.add(pkg)) write(set);
    }

    void show(String pkg) {
        TreeSet<String> set = new TreeSet<>(prefs.getStringSet(KEY, new TreeSet<>()));
        if (set.remove(pkg)) write(set);
    }

    private void write(TreeSet<String> set) {
        prefs.edit().putStringSet(KEY, set).apply();
    }
}

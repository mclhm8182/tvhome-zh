package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The user's own dock (the pinned apps along the bottom), editable from the sofa.
 *
 * Three layers, later wins: the compiled-in config.xml defaults, an optional config.json overlay pushed
 * by {@code tools/theme.sh}, and whatever the user picked here. An empty edit list means "the user
 * cleared the dock", so it must not fall back — that is why the layers are tracked separately instead
 * of collapsing into one nullable list.
 */
final class DockStore {

    private static final String PREFS = "dock";
    private static final String KEY_APPS = "apps";

    private final SharedPreferences prefs;
    /** config.xml defaults, optionally already overridden by config.json. */
    private final List<String> base;

    DockStore(Context ctx, List<String> configuredDefaults) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.base = new ArrayList<>(configuredDefaults);
    }

    /** Pinned package names in dock order. Never null. */
    List<String> apps() {
        if (!prefs.contains(KEY_APPS)) return new ArrayList<>(base);
        String raw = prefs.getString(KEY_APPS, "");
        List<String> out = new ArrayList<>();
        for (String p : raw.split("\n")) {
            p = p.trim();
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    /** True once the user has edited the dock themselves. */
    boolean isCustomised() {
        return prefs.contains(KEY_APPS);
    }

    /** Overwrite the dock. An empty list pins nothing, leaving the grid fully alphabetical. */
    void set(List<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) {
            if (p == null || p.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(p.trim());
        }
        prefs.edit().putString(KEY_APPS, sb.toString()).apply();
    }

    /** Pin at the end of the dock, replacing whatever was in that slot. */
    void pin(String pkg, int slot) {
        List<String> list = apps();
        list.remove(pkg);                       // already pinned: move it rather than duplicate
        while (list.size() <= slot) list.add(null);
        list.set(slot, pkg);
        compact(list);
        set(list);
    }

    /** Replace the dock slot the app occupies, leaving it in the same position. */
    void pinOver(String pkg, int slot) {
        List<String> list = apps();
        list.remove(pkg);
        if (slot < 0 || slot >= list.size()) {
            pin(pkg, list.size());
            return;
        }
        list.set(slot, pkg);
        set(list);
    }

    /** Drop the app from the dock, letting the following entries close the gap. */
    void unpin(String pkg) {
        List<String> list = apps();
        list.remove(pkg);
        set(list);
    }

    boolean isPinned(String pkg) {
        return apps().contains(pkg);
    }

    private void compact(List<String> list) {
        list.removeIf(s -> s == null || s.trim().isEmpty());
    }
}
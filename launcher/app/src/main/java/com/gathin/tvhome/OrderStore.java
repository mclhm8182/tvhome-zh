package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The order the user put the apps in, from the tile menu's Move.
 *
 * <p>The dock keeps its own order (DockStore, because the user can rebuild it from the sofa), so
 * this only covers the grid below it. An app that is not in here keeps its alphabetical place,
 * which is what a freshly installed app gets.
 *
 * <p>Written as "component=rank" lines. A component is "package/activity", so it carries neither a
 * newline nor an equals sign, and the file stays readable.
 */
final class OrderStore {

    private static final String PREFS = "order";
    private static final String KEY_RANKS = "ranks";

    private OrderStore() {
    }

    /** component -> rank, for the apps the user has ordered. Never null. */
    static Map<String, Integer> load(Context ctx) {
        SharedPreferences prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Map<String, Integer> out = new HashMap<>();
        for (String line : prefs.getString(KEY_RANKS, "").split("\n")) {
            int eq = line.lastIndexOf('=');
            if (eq <= 0) continue;
            try {
                out.put(line.substring(0, eq), Integer.valueOf(line.substring(eq + 1).trim()));
            } catch (NumberFormatException e) {
                // A line we cannot read is a line we do not honour.
            }
        }
        return out;
    }

    /**
     * Writes the whole grid order at once, so every app on the desk gets a rank. Moving one app
     * otherwise leaves all the others unranked, and they would all fall behind the moved one.
     */
    static void save(Context ctx, List<String> keysInOrder) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keysInOrder.size(); i++) {
            String key = keysInOrder.get(i);
            if (key == null || key.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(key).append('=').append(i);
        }
        ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_RANKS, sb.toString()).apply();
    }
}

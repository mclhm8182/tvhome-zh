package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The inputs on Coocaa OS (Skyworth / 酷开) TVs, which register nothing with TvInputManager: our
 * own source menu would come up empty there.
 *
 * <p>{@code com.skyworth.tv} lists the sources its own source panel shows (HDMI 1, HDMI 2, AV, the
 * tuner) through its own provider, and switches to one — and brings up its player — the way picking
 * it in that panel does.
 *
 * <p>The provider is not part of any contract, so every call here is wrapped: a TV without it, or
 * with a different one, simply yields an empty list and the ordinary TvInputManager path is used.
 */
final class CoocaaSources {

    private static final Uri LIST = Uri.parse("content://com.skyworth.tv.provider/system/source_list");
    private static final Uri METHOD = Uri.parse("content://com.skyworth.tv.sdk.provider/method");
    private static final String[] COLUMNS = {"sourceName", "sourceIndex", "displayName"};

    /** One source, as the vendor's own panel lists it. */
    static final class Source {
        /** HDMI, AV, VOD (the RF tuner), ... */
        final String name;
        final int index;
        final String label;

        Source(String name, int index, String label) {
            this.name = name;
            this.index = index;
            this.label = label;
        }
    }

    private CoocaaSources() {
    }

    /**
     * Whether this TV has the Coocaa app at all. The check is a package lookup, which is cheap;
     * the provider query below it is not, so it only happens on a set that has the app.
     */
    static boolean present(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo("com.skyworth.tv", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** HDMI ports in port order, then AV, then the tuner; empty where the provider is missing. */
    static List<Source> list(Context ctx) {
        List<Source> out = new ArrayList<>();
        if (!present(ctx)) return out;
        try (Cursor c = ctx.getContentResolver().query(LIST, COLUMNS, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String name = c.getString(0);
                String label = c.getString(2);
                if (name == null) continue;
                out.add(new Source(name, c.getInt(1), label == null || label.isEmpty() ? name : label));
            }
        } catch (RuntimeException e) {
            // No such provider on this set: the ordinary path takes over.
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
        Collections.sort(out, (a, b) -> a.name.equals(b.name)
                ? Integer.compare(a.index, b.index)
                : Integer.compare(rank(a.name), rank(b.name)));
        return out;
    }

    /** HDMI first, then AV, then the tuner, then anything else. */
    private static int rank(String name) {
        switch (name) {
            case "HDMI": return 0;
            case "AV": return 1;
            case "VOD": return 3;
            default: return 2;
        }
    }

    /** Switches to this source and brings up the vendor's player, as its own panel does. */
    static boolean open(Context ctx, Source source) {
        Bundle extras = new Bundle();
        extras.putString("name", source.name);
        extras.putInt("index", source.index);
        try {
            Bundle ret = ctx.getContentResolver().call(METHOD, "METHOD_GOTO_SOURCE", null, extras);
            return ret != null && ret.getBoolean("ret");
        } catch (RuntimeException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}

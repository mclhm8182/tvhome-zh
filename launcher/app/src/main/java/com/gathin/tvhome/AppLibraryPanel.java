package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The panel that puts apps on the desk by hand.
 *
 * <p>Most apps on a TV declare no TV (leanback) entry point, so they never become tiles on their
 * own: a video app from a sideloaded APK is simply not on the desk, and there is no way to get it
 * there. This lists every app the TV can start and lets the user put the missing ones on the desk
 * next to the ones that do declare a TV entry.
 *
 * <p>The memory here is what the whole feature turns on. A TV box has 2 GB, and a launcher that
 * holds a hundred app icons to show a list is a launcher that gets killed. So:
 *
 * <ul>
 * <li>the catalogue and the labels load on a background thread — opening the panel on a weak box
 *     froze the desk for 60-140 ms when this was done on the UI thread;</li>
 * <li>only the labels are kept between openings (a string per app);</li>
 * <li>the icons live in a small cache and are loaded one visible row at a time.</li>
 * </ul>
 */
final class AppLibraryPanel {

    /** One thread for all of it: the catalogue, the labels and the icons. */
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    /**
     * Icons kept in memory, counted in bytes and not in entries: an app icon decoded from a
     * 192 px bitmap is three times the size of a 48 dp one, so a count of entries would make the
     * cache's real size depend on what the TV happens to have installed. Two megabytes is a
     * screenful of icons and nothing more.
     */
    private static final LruCache<String, Drawable> ICONS = new LruCache<String, Drawable>(2 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Drawable value) {
            if (value instanceof android.graphics.drawable.BitmapDrawable) {
                android.graphics.Bitmap b = ((android.graphics.drawable.BitmapDrawable) value).getBitmap();
                if (b != null) return b.getByteCount();
            }
            return 0;
        }
    };
    /** Labels kept between openings, until the app is updated. Loader thread only. */
    private static final Map<String, String> KNOWN = new HashMap<>();

    /** One app the TV can start. */
    private static final class App {
        final String component;
        final String pkg;
        final String label;
        /** It declares a TV entry point, so it is on the desk already. */
        final boolean onDesk;

        App(String component, String pkg, String label, boolean onDesk) {
            this.component = component;
            this.pkg = pkg;
            this.label = label;
            this.onDesk = onDesk;
        }
    }

    private final MainActivity activity;
    private final LibraryApps store;
    /** Called when the desk changed, so it can be rebuilt. */
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<App> apps = new ArrayList<>();
    private final Collator collator = Collator.getInstance();

    private RecyclerView list;
    private TextView status;
    private PopupWindow popup;
    private boolean closed;

    AppLibraryPanel(MainActivity activity, LibraryApps store, Runnable changed) {
        this.activity = activity;
        this.store = store;
        this.changed = changed;
    }

    /**
     * Opens the panel under the given button. The apps arrive a moment later; the panel says so
     * until they do.
     */
    void show(View anchor) {
        View panel = activity.getLayoutInflater().inflate(R.layout.popup_library, activity.root, false);
        Typefaces.apply(panel);
        list = panel.findViewById(R.id.rows);
        status = panel.findViewById(R.id.status);
        list.setLayoutManager(new LinearLayoutManager(activity));
        // A fixed height: a RecyclerView that measures its own content inflates every row at once,
        // which on a 2 GB box is both slow and heavy. Seven rows scroll, and the panel stays on
        // the screen whatever the TV has installed.
        float density = activity.getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams lp = list.getLayoutParams();
        lp.height = Math.round(7 * 42 * density);
        list.setLayoutParams(lp);
        list.setAdapter(new Adapter());

        popup = activity.glassPanel(panel, anchor, 300);
        // Its right edge lines up with the button that opened it: the button is at the far end of
        // the top bar, and a panel hung off its left edge would run off the screen.
        activity.dropIn(popup, panel, anchor, Gravity.END);

        status.setText(R.string.app_library_loading);
        // What is on the desk right now, read here on the UI thread: the catalogue loads in the
        // background, and it must not read the adapter while it does.
        load(activity.deskPackages());
    }

    /** Reads the catalogue off the UI thread: the query and every label open the app's resources. */
    private void load(final Set<String> onDesk) {
        LOADER.execute(() -> {
            PackageManager pm = activity.getPackageManager();
            // Both kinds of entry point: an app that only offers a TV entry point is on the desk
            // like any other, and it has to be in this list too — this is where an app that was
            // taken off the desk is put back.
            Map<String, ComponentName> component = new LinkedHashMap<>();
            Map<String, ResolveInfo> info = new LinkedHashMap<>();
            for (ResolveInfo ri : query(pm, Intent.CATEGORY_LAUNCHER)) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(activity.getPackageName())) continue;
                if (!component.containsKey(pkg)) {
                    component.put(pkg, new ComponentName(pkg, ri.activityInfo.name));
                    info.put(pkg, ri);
                }
            }
            for (ResolveInfo ri : query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(activity.getPackageName())) continue;
                // The TV entry point wins where an app has both: it is the one the desk runs.
                component.put(pkg, new ComponentName(pkg, ri.activityInfo.name));
                info.put(pkg, ri);
            }
            List<App> catalogue = new ArrayList<>();
            for (Map.Entry<String, ComponentName> e : component.entrySet()) {
                String pkg = e.getKey();
                String label = labelOf(pm, pkg, info.get(pkg));
                catalogue.add(new App(e.getValue().flattenToString(), pkg, label, onDesk.contains(pkg)));
            }
            Collections.sort(catalogue, (a, b) -> collator.compare(a.label, b.label));
            main.post(() -> {
                if (closed) return;
                apps.clear();
                apps.addAll(catalogue);
                status.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
                if (apps.isEmpty()) status.setText(R.string.app_library_empty);
                list.getAdapter().notifyDataSetChanged();
            });
        });
    }

    /** The apps that answer to this kind of entry point. Never null. */
    private List<ResolveInfo> query(PackageManager pm, String category) {
        try {
            Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
            return pm.queryIntentActivities(probe, 0);
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    /** The app's own name, remembered between openings so the panel opens faster next time. */
    private static String labelOf(PackageManager pm, String pkg, ResolveInfo ri) {
        String known = KNOWN.get(pkg);
        if (known != null) return known;
        String label;
        try {
            label = AppAdapter.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
        } catch (RuntimeException e) {
            label = pkg;
        }
        KNOWN.put(pkg, label);
        return label;
    }

    /** The icon for this app, loaded in the background and kept in the small cache. */
    private void loadIcon(final App app, final RecyclerView.ViewHolder holder) {
        Drawable cached = ICONS.get(app.component);
        if (cached != null) {
            ((ImageView) holder.itemView.findViewById(R.id.icon)).setImageDrawable(cached);
            return;
        }
        final ImageView icon = holder.itemView.findViewById(R.id.icon);
        icon.setImageDrawable(null);
        LOADER.execute(() -> {
            Drawable d = null;
            try {
                d = activity.getPackageManager().getApplicationIcon(app.pkg);
            } catch (Exception e) {
                // No icon for this app: the row shows its name alone.
            }
            if (d != null) ICONS.put(app.component, d);
            final Drawable loaded = d;
            main.post(() -> {
                if (closed || loaded == null) return;
                // The row may have been reused for another app while the icon was loading.
                if (!app.component.equals(icon.getTag())) return;
                icon.setImageDrawable(loaded);
            });
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(activity).inflate(R.layout.item_dock, parent, false);
            Typefaces.apply(v);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            App app = apps.get(position);
            h.name.setText(app.label);
            h.item.setTag(app.component);
            boolean added = store.isAdded(app.component);
            h.tick.setVisibility(added || app.onDesk ? View.VISIBLE : View.INVISIBLE);
            loadIcon(app, h);
            h.item.setOnClickListener(v -> {
                if (app.onDesk && !store.isAdded(app.component)) {
                    // It has a TV entry point, so the desk already shows it.
                    toast(activity.getString(R.string.app_library_already, app.label));
                    return;
                }
                if (store.isAdded(app.component)) {
                    store.remove(app.component);
                    h.tick.setVisibility(View.INVISIBLE);
                    toast(activity.getString(R.string.app_library_removed, app.label));
                } else {
                    if (!store.add(app.component)) {
                        toast(activity.getString(R.string.app_library_full));
                        return;
                    }
                    // An app the user took off the desk comes back with it: this panel is where
                    // the apps that are not on the desk are put back, so an app hidden from the
                    // desk has to be let go of here as well.
                    new HiddenApps(activity).show(app.pkg);
                    h.tick.setVisibility(View.VISIBLE);
                    toast(activity.getString(R.string.app_library_added, app.label));
                }
                changed.run();
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final View item;
        final TextView name;
        final ImageView tick;

        Holder(View v) {
            super(v);
            item = v.findViewById(R.id.item);
            name = v.findViewById(R.id.name);
            tick = v.findViewById(R.id.tick);
        }
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    /** Lets go of everything: the panel is closed and the icons it held are not needed any more. */
    void dismiss() {
        closed = true;
        if (popup != null) popup.dismiss();
    }
}

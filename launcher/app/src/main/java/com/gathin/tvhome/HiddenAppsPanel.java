package com.gathin.tvhome;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The panel that puts hidden apps back on the desk.
 *
 * <p>Mirror of {@link AppLibraryPanel}: a glass sheet under the row that opened it, the hidden apps
 * listed with a tick, and a tap on a row taking it off the hidden list. The list is read once when
 * the panel opens rather than watched, because hiding and restoring only ever happens from here or
 * from a tile menu, and both close this panel before it can go stale.
 */
final class HiddenAppsPanel {

    private final MainActivity activity;
    private final HiddenApps store;
    private PopupWindow popup;
    private RecyclerView list;
    private TextView status;
    private final List<Row> rows = new ArrayList<>();

    /** One hidden app: the package, its label and its icon. */
    private static final class Row {
        final String pkg;
        final String label;
        final Drawable icon;

        Row(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    HiddenAppsPanel(MainActivity activity) {
        this.activity = activity;
        this.store = new HiddenApps(activity);
    }

    void show(View anchor) {
        View panel = activity.getLayoutInflater().inflate(R.layout.popup_hidden, activity.root, false);
        Typefaces.apply(panel);
        list = panel.findViewById(R.id.rows);
        status = panel.findViewById(R.id.status);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(new Adapter());

        read();
        popup = activity.glassPanel(panel, anchor, 300);
        activity.dropIn(popup, panel, anchor, Gravity.START);
        status.setText(rows.isEmpty()
                ? activity.getString(R.string.hidden_none)
                : activity.getString(R.string.hidden_count, rows.size()));
    }

    void dismiss() {
        if (popup != null) popup.dismiss();
    }

    /** Reads the hidden list off the package manager: labels and icons come from the apps themselves. */
    private void read() {
        rows.clear();
        PackageManager pm = activity.getPackageManager();
        for (String pkg : store.all()) {
            try {
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                rows.add(new Row(pkg, String.valueOf(ai.loadLabel(pm)), ai.loadIcon(pm)));
            } catch (Exception e) {
                // Uninstalled since it was hidden: drop it rather than leave a dead row.
                store.show(pkg);
            }
        }
        Collections.sort(rows, (a, b) -> a.label.compareToIgnoreCase(b.label));
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
            Row row = rows.get(position);
            h.name.setText(row.label);
            h.icon.setImageDrawable(row.icon);
            h.tick.setVisibility(View.VISIBLE);
            h.item.setOnClickListener(v -> {
                store.show(row.pkg);
                Toast.makeText(activity, activity.getString(R.string.app_restored, row.label),
                        Toast.LENGTH_SHORT).show();
                dismiss();
                activity.onHiddenAppsChanged();
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View item;
        final ImageView icon;
        final TextView name;
        final ImageView tick;

        Holder(View v) {
            super(v);
            item = v.findViewById(R.id.item);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);
            tick = v.findViewById(R.id.tick);
        }
    }
}

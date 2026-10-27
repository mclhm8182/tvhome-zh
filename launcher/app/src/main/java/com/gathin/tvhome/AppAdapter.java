package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for D-pad Up/Down on a tile. Return true when handled. */
        boolean onTileVerticalKey(int position, int keyCode);

        /** Long-press on a tile: pin to the dock, or unpin when already pinned. */
        void onTileLongPressed(Entry entry, boolean alreadyPinned);

        /**
         * Long-press on a tile by position, which is how a remote's held OK button arrives: a
         * stream of repeated key events, not a touch, so the long-click listener never hears it.
         */
        void onTileLongPressedAt(int position);
    }

    /** How long the OK button has to be held for the tile to be picked up. */
    private static final long LONG_PRESS_MS = 500;

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /**
         * Whether the app declares a TV (leanback) entry point. The apps the user added by hand do
         * not, and they are started as ordinary apps.
         */
        final boolean tv;
        int ambient;
        /** The face of a tile with no banner: the icon, scaled up, with the sides filled in. */
        Drawable tile;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(label, component, banner, icon, true);
        }

        Entry(String label, ComponentName component, Drawable banner, Drawable icon, boolean tv) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.tv = tv;
        }
    }

    /**
     * How much a focused tile grows. This is a state, not a motion: it stays on even with reduced
     * motion, because the glass is fitted around it and a tile that does not grow would sit under a
     * white slab instead of inside a halo.
     */
    static final float FOCUS_SCALE = 1.10f;

    /** Weak boxes get the same look, applied instantly instead of tweened. */
    static boolean reducedMotion;

    /** Whether a tile is being carried to another place, which lifts it off the wall. */
    private static boolean carrying;

    /** How much bigger a carried tile is: it is off the desk and in the hand. */
    static final float CARRY_SCALE = 1.18f;

    /** Called when a tile is picked up or put down. */
    static void setCarrying(boolean on) {
        if (carrying == on) return;
        carrying = on;
    }

    /** Whether a tile is in the hand at the moment. */
    static boolean carrying() {
        return carrying;
    }

    /**
     * Forgets the OK button a tile was picked up with.
     *
     * <p>The button that lifts a tile off the desk is held down for half a second, and the press
     * that ends the move must not open the app behind it. The tile's own state is cleared here
     * so that the next press on it opens the app as usual.
     */
    static void resetLongPress(View card) {
        if (card == null) return;
        Object tag = card.getTag(R.id.card);
        if (tag instanceof long[]) ((long[]) tag)[2] = 1;   // the next release is swallowed
    }

    /** Called once at startup from MainActivity, before the first layout pass. */
    static void setReducedMotion(boolean reduced) {
        reducedMotion = reduced;
    }
    /** Design size of a tile (dp). Tiles shrink from here when the chosen column count cannot fit them. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell, minus room for the focus zoom and the glass
     * highlight, capped at the design size so four columns on a 1080p TV look exactly as they always did.
     *
     * <p>The cap is what the XML cannot do. item_app.xml ships one fixed 180dp card, so from five
     * columns on a 960dp-wide screen the card no longer fits its cell: the neighbours touch, and at six
     * they overlap. Deriving the width from the cell instead means any column count the user picks lays
     * out with the same breathing room, just smaller tiles.
     */
    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return design;
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.min(design, fit));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    /** 16dp at the design width, proportionally less on a smaller tile, so the shape stays the same. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    /**
     * Some apps declare a small square image as their banner (Rockchip's Media Center ships a 64 px one).
     * Cropped over a 16:9 tile it is a blur. A drawable with no intrinsic size (a colour, a shape) still
     * fills the tile, so it counts as a banner.
     */
    static boolean isBannerShaped(int width, int height) {
        return width <= 0 || height <= 0 || width >= height * 1.4f;
    }

    /**
     * An app's own label as the tile shows it: control characters, bidi overrides and line separators
     * become spaces, runs of spaces collapse, at most 40 characters (never cutting a surrogate pair), and
     * the package name when nothing is left. Without this a label can redraw the tile name as something
     * else entirely.
     */
    static String cleanLabel(String s, String fallback) {
        if (s == null) return fallback;
        StringBuilder b = new StringBuilder(Math.min(s.length(), 48));
        boolean space = true;   // leading spaces are dropped too
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int type = Character.getType(c);
            boolean blank = c == ' ' || Character.isISOControl(c) || Character.isWhitespace(c)
                    || type == Character.FORMAT || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR || type == Character.SPACE_SEPARATOR;
            if (blank) {
                if (!space) b.append(' ');
                space = true;
                continue;
            }
            boolean pair = Character.isHighSurrogate(c) && i + 1 < s.length()
                    && Character.isLowSurrogate(s.charAt(i + 1));
            if (b.length() + (pair ? 2 : 1) > 40) break;
            b.append(c);
            if (pair) b.append(s.charAt(++i));
            space = false;
        }
        String out = b.toString().trim();
        return out.isEmpty() ? fallback : out;
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** The user's dock, from config.xml defaults or their own edits (DockStore). */
    private final DockStore dock;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    /** The apps the user put on the desk by hand (AppLibraryPanel). */
    private final LibraryApps library;
    /** The apps the user took off the desk from the sofa (HiddenApps). */
    private final HiddenApps hiddenAtRuntime;
    /** The order the user put the grid apps in, from the tile menu's Move. */
    private Map<String, Integer> savedRanks = java.util.Collections.emptyMap();
    private final List<Entry> items = new ArrayList<>();
    /**
     * How visible an unfocused tile's name is. 0 hides it (only the focused tile shows its name),
     * 0.75 keeps every name on screen but dimmer — on a TV where nobody recognises the app icons,
     * the names are the only way to tell them apart.
     *
     * <p>Static, like {@link #reducedMotion}: the focus animation lives in the ViewHolder and reads
     * this too. As an instance field the animation kept its own hard-coded 0 and faded every name
     * back out as focus moved away, so only tiles focus had never visited kept their name.
     */
    private static float restNameAlpha;

    AppAdapter(Context ctx, TileListener listener, int dockSize, List<String> dockDefaults) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.dock = new DockStore(ctx, dockDefaults);
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        this.library = new LibraryApps(ctx);
        this.hiddenAtRuntime = new HiddenApps(ctx);
        this.savedRanks = OrderStore.load(ctx);
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
    }

    /** Whether tiles with no artwork of their own are drawn as one set. */
    private static boolean unifiedTiles = true;

    static void setUnifiedTiles(boolean on) {
        unifiedTiles = on;
    }

    /**
     * Drops every tile face built so far, so they come back in the other style. The banners are
     * untouched: only the tiles drawn from an icon are rebuilt.
     */
    void dropTileArt() {
        for (int i = 0; i < items.size(); i++) items.get(i).tile = null;
    }

    /** Turns every tile's name on, not just the focused one's. */
    static void setAlwaysShowNames(boolean on) {
        float alpha = on ? 0.75f : 0f;
        if (alpha == restNameAlpha) return;
        restNameAlpha = alpha;
    }

    /** Whether the names of unfocused tiles are shown. */
    static boolean alwaysShowNames() {
        return restNameAlpha > 0f;
    }

    DockStore dock() {
        return dock;
    }

    /**
     * Carries a tile to another place on the desk.
     *
     * <p>The desk is one list: the favourites are its bottom row and the grid is what lies above
     * it, so a tile moves between the two the way it moves inside either. The tiles in between
     * step aside and the carried one slides over, instead of the two of them jumping into each
     * other's places.
     *
     * <p>The favourites are the front of the bottom row, and they follow the move: an app carried
     * into the row joins them, one carried out of it lets them go, and one the move pushed out of
     * the row has no place in it any more.
     *
     * @return true when the tile moved.
     */
    boolean move(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= items.size() || to >= items.size()) return false;
        Entry carried = items.remove(from);
        items.add(to, carried);
        // A move notification, not a refresh: the grid can then slide the tile over and let the
        // ones in between step aside. A refresh made every tile jump at once, and the focus —
        // which follows a tile by its id — landed on a different app on the way.
        notifyItemMoved(from, to);
        syncFavourites(from, to);
        return true;
    }

    /**
     * The favourites after a move: the front of the bottom row.
     *
     * <p>The row holds a fixed number of apps and the favourites are the ones the user marked at
     * the front of it, so the row is read again after every move: the app in the hand joins them
     * when it came in from the grid, lets them go when it left the row, and one the move pushed
     * out of the row is let go as well — the loop below simply stops when the row is full.
     */
    private void syncFavourites(int from, int to) {
        int row = Math.min(dockSize, items.size());
        int count = dock.apps().size();
        if (to < row && from >= row) count++;              // carried in from the grid
        else if (from < row && to >= row) count--;         // carried out of the row
        List<String> now = new ArrayList<>();
        for (int i = 0; i < row && now.size() < count; i++) now.add(items.get(i).pkg);
        dock.set(now);
    }

    /**
     * Every tile's key, in the order they stand in now: what the desk looks like after the user
     * has moved the tiles around.
     */
    List<String> orderKeys() {
        List<String> out = new ArrayList<>();
        for (Entry e : items) out.add(e.component.flattenToShortString());
        return out;
    }

    /** The package of the tile at this position, or null. */
    String packageAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : null;
    }

    /**
     * Re-applies the focus look to a tile. The carrying state changed under it — a tile that was
     * picked up grows and lifts — and a focus change does not come to tell it.
     */
    void refreshLook(View card) {
        boolean focused = card.isFocused();
        float scale = focused ? (carrying ? CARRY_SCALE : FOCUS_SCALE) : 1f;
        card.setScaleX(scale);
        card.setScaleY(scale);
        card.setTranslationY(focused && carrying
                ? -10 * card.getResources().getDisplayMetrics().density : 0f);
    }

    /**
     * Whether this package already has a tile on the desk. The app library asks, so it can tick the
     * apps that are there and leave the rest to be added.
     *
     * <p>Asked of the adapter rather than worked out from the app's own declarations: an intent with a
     * component set resolves as long as that component exists, whatever its intent filters say, so
     * probing one would call every app a TV app.
     */
    boolean containsPackage(String pkg) {
        for (Entry entry : items) if (entry.pkg.equals(pkg)) return true;
        return false;
    }

    /** How many dock slots this launcher lays out. */
    int dockSize() {
        return dockSize;
    }

    /** The tiles as they are now, for {@link #containsPackage} and the app library. */
    List<Entry> items() {
        return items;
    }

    /**
     * Lets go of every tile and its artwork. Called when the system says memory is short while Home is
     * in the background: the banners are the biggest thing we hold, and they can be loaded again when
     * the user comes back.
     */
    void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    /** True when the tiles were let go of (memory was short) and have to be loaded again. */
    boolean isEmpty() {
        return items.isEmpty();
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        // The packages that answer to a TV entry point. An app the user put on the desk by hand
        // is started the way its own entry point says it should be, and this is where that is
        // known: an intent with a component set resolves as long as the component exists, so
        // probing one would call every app a TV app.
        Set<String> tvPackages = new HashSet<>();
        // Some system apps declare two leanback entry points under one name, which would show as two
        // identical tiles: keep the first per package and label.
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            tvPackages.add(pkg);
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            if (hiddenAtRuntime.isHidden(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            // A square "banner" counts as none: cropped over a 16:9 tile it is a blur, so the tile shows
            // the app's icon and name instead.
            if (banner != null && !isBannerShaped(banner.getIntrinsicWidth(), banner.getIntrinsicHeight())) {
                banner = null;
            }
            Drawable icon = ri.loadIcon(pm);
            String label = cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg);
            if (!seen.add(pkg + '\n' + label)) continue;
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }
        // The apps the user put on the desk by hand: they declare no TV entry point, so nothing else
        // would ever bring them onto the desk.
        for (String component : library.added()) {
            addLibraryApp(pm, fresh, seen, component, tvPackages);
        }
        Collections.sort(fresh, (a, b) -> {
            List<String> dockApps = dock.apps();
            int fa = dockApps.indexOf(a.pkg), fb = dockApps.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            // The dock is the user's own; so is the order of everything below it.
            Integer ra = savedRanks.get(a.component.flattenToShortString());
            Integer rb = savedRanks.get(b.component.flattenToShortString());
            if (ra != null || rb != null) {
                if (ra == null) return 1;    // a newly installed app goes to the end
                if (rb == null) return -1;
                return Integer.compare(ra, rb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    /**
     * Adds one app the user put on the desk by hand. It has no banner of its own (no TV entry
     * point means no TV artwork), so the tile shows its icon and name.
     *
     * <p>An app that does have a TV entry point is started as one: it was put on the desk by
     * hand only because it had been hidden from it, and a TV app has to be started the way its
     * own entry point says it should be.
     */
    private void addLibraryApp(PackageManager pm, List<Entry> fresh, Set<String> seen, String component,
                                Set<String> tvPackages) {
        ComponentName cn = ComponentName.unflattenFromString(component);
        if (cn == null) return;
        String pkg = cn.getPackageName();
        if (hidden.contains(pkg) || hiddenAtRuntime.isHidden(pkg)) return;
        try {
            android.content.pm.ActivityInfo ai = pm.getActivityInfo(cn, 0);
            String label = cleanLabel(String.valueOf(ai.loadLabel(pm)), pkg);
            if (!seen.add(pkg + '\n' + label)) return;   // it has a TV entry point after all
            boolean tv = tvPackages.contains(pkg);
            fresh.add(new Entry(label, cn, null, ai.loadIcon(pm), tv));
        } catch (Exception e) {
            // The app was uninstalled since it was added; nothing to put on the desk.
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        Typefaces.apply(v);
        // Size the tile from the cell it has to live in; the XML's 180dp is only the design size.
        // Falling back to the screen width rather than the design size matters: a holder created
        // before the grid has been measured would otherwise keep an oversized tile for its lifetime.
        float density = parent.getResources().getDisplayMetrics().density;
        int parentWidth = parent.getWidth() > 0
                ? parent.getWidth() : parent.getResources().getDisplayMetrics().widthPixels;
        int inner = parentWidth - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density);
        int h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener, dockSize);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        // The dock's own names only show with always_show_names: the band has to grow to hold them.
        h.name.setVisibility((position < dockSize && restNameAlpha == 0f) ? View.INVISIBLE : View.VISIBLE);
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : restNameAlpha);
        // Every name sits on the same line, whatever the tile has been through: the focus used to
        // push one name down by a few pixels, which left a row of them looking ragged.
        h.name.setTranslationY(0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.card.setBackground(h.cardBackground);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            // An app with no TV artwork used to leave a black slab with one small icon floating on
            // it. The icon now fills the tile and the space beside it is filled from the icon's
            // own edges, so the tile reads as one picture.
            if (e.tile == null) e.tile = IconTileDrawable.of(e.icon, unifiedTiles);
            h.icon.setImageDrawable(e.tile);
            h.icon.setVisibility(View.VISIBLE);
            h.card.setBackground(h.cardBackground);
        }
        h.card.setOnClickListener(v -> launch(e));
        // Long-press edits the dock: pin into the first free slot, or unpin if it is already there.
        // Upstream long-press opened app info; dock editing is the thing users actually asked for.
        h.card.setOnLongClickListener(v -> {
            if (listener != null) listener.onTileLongPressed(e, dock.isPinned(e.pkg));
            return true;
        });
    }

    private void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(e.tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView name;
        /** The card's own background from the XML, put back for tiles that carry a banner. */
        final Drawable cardBackground;

        Holder(View v, TileListener listener, int columns) {
            super(v);
            card = v.findViewById(R.id.card);
            cardBackground = card.getBackground();
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            // A remote's long press is a stream of repeated key events, not a touch, so the
            // long-click listener never hears of it: the first press opened the app and there was
            // no way to pick a tile up. Holding the OK button for half a second opens the menu,
            // and the press that follows is swallowed so the app does not open behind it.
            // The state lives on the card itself, so the launcher can clear it when it takes the
            // press over (it does, while a tile is being carried).
            final long[] state = new long[3];      // down time, long press fired, swallow the release
            card.setTag(R.id.card, state);
            final Runnable[] fire = new Runnable[1];
            final Runnable[] hold = new Runnable[1];
            fire[0] = () -> {
                int p = getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION && listener != null) listener.onTileLongPressedAt(p);
            };
            hold[0] = () -> {
                if (state[0] != 0 && state[1] == 0) {
                    state[1] = 1;
                    fire[0].run();
                }
            };
            card.setOnKeyListener((view, keyCode, event) -> {
                int action = event.getAction();
                boolean enter = keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
                if (enter) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        if (state[0] == 0) {
                            state[0] = event.getEventTime();
                            card.removeCallbacks(hold[0]);
                            card.postDelayed(hold[0], LONG_PRESS_MS);
                        }
                        // A remote's long press is a stream of repeated key events, and the repeats
                        // are marked as such; the time the button has been down is the fallback for
                        // a remote that does not mark them.
                        long held = event.getEventTime() - event.getDownTime();
                        if (state[1] == 0
                                && (event.getRepeatCount() > 0 || held >= LONG_PRESS_MS)) {
                            state[1] = 1;
                            fire[0].run();
                        }
                    } else if (action == KeyEvent.ACTION_UP) {
                        card.removeCallbacks(hold[0]);
                        // The release of a press the launcher took over must not open the app.
                        if (state[1] != 0 || state[2] != 0) {
                            state[1] = 0;
                            state[2] = 0;
                            state[0] = 0;
                            return true;    // the app must not open behind the menu
                        }
                        state[0] = 0;
                    }
                    return false;
                }
                if (action != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return listener != null && listener.onTileVerticalKey(pos, keyCode);
                }
                // Left from a row's first tile and right from its last used to fall through to the
                // system's focus search, whose beam swept across the screen and landed on whatever
                // shares the column — the weather line, a top-bar button. Stay put instead.
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && pos % columns == 0) return true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && pos % columns == columns - 1) return true;
                return false;
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? (carrying ? CARRY_SCALE : FOCUS_SCALE) : 1.0f;
                // A carried tile is lifted off the wall it sits on, so it reads as being held.
                view.setTranslationY(focused && carrying ? -10 * density : 0f);
                // The unfocused name follows the setting: hidden, or kept on screen but dimmer. Its
                // place does not change: a name that moved down when the tile took the focus left
                // the rest of its row on a different line.
                if (reducedMotion) {
                    view.setScaleX(s);
                    view.setScaleY(s);
                    name.setAlpha(focused ? 1f : restNameAlpha);
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                            .start();
                    name.animate().alpha(focused ? 1f : restNameAlpha)
                            .setDuration(220).start();
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}

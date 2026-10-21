package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    /** config.xml defaults with an optional config.json theme overlay on top. */
    private Config cfg;
    /** The wallpaper file on the TV; null until something asks for it. */
    private Wallpaper wall;
    /** The "Desktop settings" panel, so Back can close it. */
    private PopupWindow wallpaperPanel;
    /** The picture behind the desk, when there is one; removed and re-added as the wallpaper changes. */
    private ImageView wallpaperView;
    /** Draws the band behind the dock row; kept so its colour can follow the wallpaper. */
    private DockDecoration dockDecoration;
    private static final int REQ_PICK_WALLPAPER = 41;
    /** Weak boxes cannot keep up with the tweens; this turns the desk's motion down to a minimum. */
    private boolean reducedMotion;
    /** User's icon-per-row choice; 0 means "whatever this screen is happy with". */
    private static final String PREFS_LAYOUT = "layout";
    private static final String KEY_COLUMNS = "columns";
    private static final String KEY_REDUCED_MOTION = "reduced_motion";
    /** The range the panel offers, in the order the row cycles through them. */
    private static final int[] COLUMN_CHOICES = {3, 4, 5, 6, 7, 8};
    /**
     * Launch the desk straight into its own settings panel. The quick settings overlay
     * ({@link QuickSettingsActivity}) sends this, so the remote's Settings button can reach the wallpaper
     * and icon-per-row settings without a second settings screen.
     */
    public static final String ACTION_DESKTOP_SETTINGS = "com.gathin.tvhome.action.DESKTOP_SETTINGS";
    /** Same route for the Favourites (dock) panel, so the quick settings overlay can offer it too. */
    public static final String ACTION_DOCK_PICKER = "com.gathin.tvhome.action.DOCK_PICKER";
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private TextView btnWifi;
    /** The "Favourites" panel, so Back can close it. */
    private PopupWindow dockPicker;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    /** Width of the tile the glass was last fitted to, so a new column count refits it. */
    private int glassCardW;
    /** Scratch rects: onTileFocused runs on every focus change, so it allocates nothing. */
    private final Rect tileRect = new Rect();
    private final Rect revealRect = new Rect();
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        // Quick settings asking for one of the desk's own panels (the desk is singleTask, so it lands
        // here whenever it is already running).
        openPanelFromQuickSettings(intent.getAction());
        if (intent.getAction() != null) return;
        // Only the Home key (re-issued by HomeKeyService, or the system on remotes that send
        // KEYCODE_HOME) sends a HOME intent to an already-running desktop; treat it as "go to top".
        if (!intent.hasCategory(Intent.CATEGORY_HOME)) return;
        scrollToTop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        live = new WeakReference<>(this);
        density = getResources().getDisplayMetrics().density;
        // config.xml defaults, then a config.json overlay pushed by tools/theme.sh if one is there.
        cfg = Config.load(this);
        // The user's own choice wins; otherwise fall back to what this screen can carry comfortably.
        int wanted = getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE).getInt(KEY_COLUMNS, 0);
        COLUMNS = isColumnChoice(wanted) ? wanted : recommendColumns();
        liveTvPkg = cfg.liveTvPackage.trim();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        // A theme may paint the whole background: a colour, a wallpaper, or nothing beyond both.
        root.setBackgroundColor(cfg.backgroundColor);
        if (cfg.wallpaper != null) showWallpaper(cfg.wallpaper);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            /**
             * Every "bring this tile on screen" scroll lands here, both RecyclerView's own on focus and
             * onTileFocused's. The stock version treats the grid's top padding as the visible top, but that
             * padding only pushes the dock down the screen: grid rows scroll up into it. With the stock rule,
             * Up into a row above the padding dragged that row down to the padding line, cut it off at the
             * bottom of the screen and could scroll the focused tile out entirely, dropping focus into the
             * top bar. Grid tiles now scroll just enough to show the rect between the grid's top edge and its
             * bottom; dock tiles are left to onTileFocused, which parks the dock row on the padding line.
             */
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                if (getPosition(child) < COLUMNS) return false;
                int top = child.getTop() + rect.top;
                int bottom = top + rect.height();
                int limitBottom = getHeight() - getPaddingBottom();
                int dy;
                if (top < 0) dy = top;
                else if (bottom > limitBottom) dy = Math.min(top, bottom - limitBottom);
                else dy = 0;
                if (dy == 0) return false;
                if (immediate) parent.scrollBy(0, dy);
                else parent.smoothScrollBy(0, dy);
                return true;
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        dockDecoration = new DockDecoration(COLUMNS, density, wallpaperPresent());
        grid.addItemDecoration(dockDecoration);
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        reducedMotion = getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE)
                .getBoolean(KEY_REDUCED_MOTION, false);
        AppAdapter.setReducedMotion(reducedMotion);
        adapter = new AppAdapter(this, this, COLUMNS, cfg.dockApps);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            // The item height follows the tile, and the tile follows the column count, so this cannot be
            // a constant: at six columns the tiles are smaller and the dock row sits lower.
            int inner = (r - l) - grid.getPaddingLeft() - grid.getPaddingRight();
            int tile = AppAdapter.tileWidthPx(inner, COLUMNS, density);
            int dockRow = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(tile), density)
                    + (int) ((6 + 12) * density);   // item height plus DockDecoration offsets
            int want = Math.max(0, h - dockRow - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        findViewById(R.id.btnDock).setOnClickListener(v -> showDockPicker());
        findViewById(R.id.btnWallpaper).setOnClickListener(v -> showWallpaperPicker());
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> open(systemIntent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> open(systemIntent(Settings.ACTION_SETTINGS)));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the dock tile Up came from, not wherever the focus finder happens to land.
                focusTile(lastFocused % COLUMNS);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnWallpaper) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        findViewById(R.id.btnDock).setOnKeyListener(pillKeys);
        findViewById(R.id.btnWallpaper).setOnKeyListener(pillKeys);
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);

        // If the user left the Bing wallpaper on, make sure today's picture is there before they see
        // the desk. Runs in the background and re-applies only when something actually changed.
        wall = new Wallpaper(this);
        if (wall.autoDaily() && !wall.hasWallpaper()) {
            new Thread(() -> {
                final boolean done = wall.refreshBingIfDue();
                if (done) handler.post(this::applyWallpaper);
            }).start();
        }

        // Started by the quick settings overlay when the desk was not running yet (the usual case,
        // since the panel also floats over apps, not just over the desktop).
        openPanelFromQuickSettings(getIntent().getAction());
    }

    /** Opens one of the desk's own panels, whichever route asked for it (quick settings or a cold start). */
    private void openPanelFromQuickSettings(String action) {
        if (ACTION_DESKTOP_SETTINGS.equals(action)) grid.post(this::showWallpaperPicker);
        else if (ACTION_DOCK_PICKER.equals(action)) grid.post(this::showDockPicker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (awayFromDesk) {
            awayFromDesk = false;
            grid.post(this::scrollToTop);
        }
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        adapter.reload();
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        handler.postDelayed(minuteTick, MINUTE_MS);

        // Return to the app we left from, or the same slot if that app is gone. Always refocus: after
        // reload() the view that holds focus may no longer show the app it had.
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
    }

    @Override
    protected void onStop() {
        // Another app took the screen. A translucent overlay (the panels, the quick settings)
        // only pauses us, so this really does mean the desk was left behind.
        awayFromDesk = true;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        live = null;
        super.onDestroy();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        if (dockPicker != null && dockPicker.isShowing()) {
            dockPicker.dismiss();
            return;
        }
        // Home screen: Back goes nowhere.
    }

    /** Whether the Menu button should open quick settings from other apps too (see HomeKeyService). */
    boolean menuKeyEverywhere() {
        return getSharedPreferences(HomeKeyService.PREFS, MODE_PRIVATE)
                .getBoolean(HomeKeyService.KEY_MENU_EVERYWHERE, false);
    }

    /** Whether the tweens are turned down for a box that cannot keep up with them. */
    boolean reducedMotion() {
        return getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE)
                .getBoolean(KEY_REDUCED_MOTION, false);
    }

    /** True when config.xml names the TV's own source panel. */
    private boolean panelConfigured() {
        String service = cfg.sourcePanelService == null ? "" : cfg.sourcePanelService.trim();
        String action = cfg.sourcePanelAction == null ? "" : cfg.sourcePanelAction.trim();
        return !service.isEmpty() || !action.isEmpty();
    }

    /**
     * Open the TV's own source panel. The panel is sometimes an activity (a dialog on some sets) and
     * sometimes a service, so try it as the activity it resolves to first and fall back to a service.
     */
    private void openSourcePanel() {
        Intent panel = sourcePanelIntent();
        if (panel == null) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (panel.getComponent() != null && getPackageManager().resolveService(panel, 0) != null) {
                startService(panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                open(panel);
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The TV's own source-selection panel, for sets that do not report their inputs to the launcher.
     *
     * <p>Tried in order: the component named in config.xml (per vendor), then the action named there,
     * then AOSP's own input-source page — the platform has no constant for it, but TV ROMs answer this
     * action string. There is deliberately no "plain system settings" last resort: on a box with no
     * source panel at all (a Google TV stick, say) that just opens the settings the user was already
     *looking at, which reads like a bug. Null means we have nothing better to offer, and the caller
     * says so instead.
     */
    private Intent sourcePanelIntent() {
        String service = cfg.sourcePanelService == null ? "" : cfg.sourcePanelService.trim();
        String action = cfg.sourcePanelAction == null ? "" : cfg.sourcePanelAction.trim();
        if (!service.isEmpty()) {
            Intent byComponent = new Intent().setComponent(ComponentName.unflattenFromString(service));
            if (byComponent.resolveActivity(getPackageManager()) != null) return byComponent;
        }
        if (!action.isEmpty()) {
            Intent byAction = new Intent(action);
            if (byAction.resolveActivity(getPackageManager()) != null) return byAction;
        }
        Intent tvInputs = new Intent("android.settings.INPUT_SOURCE_SETTINGS");
        return tvInputs.resolveActivity(getPackageManager()) != null ? tvInputs : null;
    }

    /**
     * True while another app is in front. Coming back then means the user asked for the desk, and the
     * desk opens at the top — the same thing the Home key does everywhere else. Inside the desk (a panel
     * closing, the volume dialog, the quick settings overlay) onStop does not fire, so nothing moves.
     */
    private boolean awayFromDesk;
    /** HomeKeyService cannot reach an activity directly; this is the live one, weakly held. */
    private static WeakReference<MainActivity> live;

    /** Called by {@link HomeKeyService} when Home is pressed while the desk is already in front. */
    static void onHomeKeyFromService() {
        MainActivity activity = live == null ? null : live.get();
        if (activity != null) activity.runOnUiThread(activity::scrollToTop);
    }

    /** Back to the first tile, the way the Home button behaves on a phone launcher. */
    private void scrollToTop() {
        if (adapter == null || adapter.getItemCount() == 0) return;
        lastFocused = 0;
        lastFocusedKey = adapter.keyAt(0);
        // Take the glass off first. The tile it is glued to is about to be moved to another row, and
        // measuring it in between left the highlight sitting in the top-left corner of the screen.
        hideGlass();
        grid.scrollToPosition(0);
        // Focus after the layout pass that moves the rows. Straight after scrollToPosition the holder
        // found for position 0 is still the old one, and asking a view that is about to be recycled for
        // focus is how the highlight ended up nowhere at all.
        grid.post(() -> {
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(0);
            if (vh == null) {
                focusTile(0);           // not laid out yet: focusTile waits for the scroll to settle
                return;
            }
            View card = vh.itemView.findViewById(R.id.card);
            onTileFocused(0, card);    // already focused: no focus event will fire, so place it by hand
            card.requestFocus();
        });
    }

    /**
     * The remote's Menu (or Settings) button opens the quick settings panel, the way TV boxes with a
     * built-in launcher do it: one press for the handful of places you actually go, including this
     * launcher's own two panels. Dispatched here rather than through a key listener so it works from
     * anywhere on the desk, including when nothing is focused.
     *
     * <p>This used to open the dock editor on its own. The panel carries a Favourites row for it, so
     * nothing is lost, and the Favourites pill on the top bar still opens it in one press. A remote
     * that sends a real KEYCODE_SETTINGS or KEYCODE_HELP gets the same panel, and HomeKeyService
     * opens it from other apps; swallowing it globally would break it inside other apps.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        // Home while the desk is already in front means "take me back to the top", not "relaunch me".
        // Handling it here rather than in onNewIntent because plenty of paths (a remote that sends a
        // plain KEYCODE_HOME, a shell-injected key) never re-deliver the HOME intent at all.
        if (code == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) scrollToTop();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU
                || code == KeyEvent.KEYCODE_SETTINGS
                || code == KeyEvent.KEYCODE_HELP) {
            // Both actions are consumed: an unhandled remote key gets the system's "this app does not
            // support that button" toast on some boxes.
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                startActivity(new Intent(this, QuickSettingsActivity.class));
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && (code == KeyEvent.KEYCODE_APP_SWITCH
                || code == KeyEvent.KEYCODE_GUIDE)) {
            if (dockPicker != null && dockPicker.isShowing()) {
                dockPicker.dismiss();
            } else {
                showDockPicker();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ---- editing the dock -----------------------------------------------------------------------

    /**
     * Long-press a tile: pin it to the dock, or take it out again. Pinning replaces the slot the app
     * would otherwise occupy, so with a full dock the user still ends up with exactly one copy of it.
     */
    @Override
    public void onTileLongPressed(AppAdapter.Entry entry, boolean alreadyPinned) {
        DockStore dock = adapter.dock();
        if (alreadyPinned) {
            dock.unpin(entry.pkg);
            toast(getString(R.string.dock_removed, entry.label));
        } else {
            List<String> apps = dock.apps();
            int slot = apps.size();
            if (slot >= adapter.dockSize()) {
                // Dock is full: put the app in the slot the user is most likely to want to swap, the
                // first one, so long-pressing never silently does nothing.
                slot = 0;
            }
            dock.pinOver(entry.pkg, slot);
            toast(getString(R.string.dock_added, entry.label));
        }
        refreshDock();
    }

    /** Reload the grid so the new dock order takes effect, then put focus back where the user was. */
    private void refreshDock() {
        final String key = lastFocusedKey;
        adapter.reload();
        grid.scheduleLayoutAnimation();
        int pos = adapter.positionOf(key);
        if (pos >= 0) {
            View v = grid.getLayoutManager().findViewByPosition(pos);
            if (v != null) v.requestFocus();
        }
    }

    /**
     * "Favourites" button: shows every installed app, with the pinned ones ticked, so the dock can be
     * rebuilt from the sofa instead of by editing config.xml and recompiling.
     */
    private void showDockPicker() {
        if (dockPicker != null && dockPicker.isShowing()) {
            dockPicker.dismiss();
            return;
        }
        View panel = getLayoutInflater().inflate(R.layout.popup_dock, root, false);
        RecyclerView list = panel.findViewById(R.id.rows);
        list.setLayoutManager(new LinearLayoutManager(this));

        final List<ResolveInfo> found = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            if (ri.activityInfo == null) continue;
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            found.add(ri);
        }
        Collections.sort(found, (a, b) -> String.valueOf(a.loadLabel(pm))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));

        final DockStore dock = adapter.dock();
        list.setAdapter(new RecyclerView.Adapter<DockHolder>() {
            @Override
            public DockHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_dock, parent, false);
                return new DockHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull DockHolder h, int position) {
                ResolveInfo ri = found.get(position);
                String label = String.valueOf(ri.loadLabel(pm));
                String pkg = ri.activityInfo.packageName;
                h.name.setText(label);
                h.icon.setImageDrawable(ri.loadIcon(pm));
                boolean pinned = dock.isPinned(pkg);
                h.tick.setVisibility(pinned ? View.VISIBLE : View.INVISIBLE);
                h.item.setOnClickListener(v -> {
                    if (pinned) {
                        dock.unpin(pkg);
                        h.tick.setVisibility(View.INVISIBLE);
                        toast(getString(R.string.dock_removed, label));
                    } else if (dock.apps().size() >= adapter.dockSize()) {
                        // The dock has a fixed number of slots; make that clear instead of silently
                        // appending a tile the launcher would never draw.
                        toast(getString(R.string.dock_full, adapter.dockSize()));
                        return;
                    } else {
                        dock.pin(pkg, dock.apps().size());
                        h.tick.setVisibility(View.VISIBLE);
                        toast(getString(R.string.dock_added, label));
                    }
                    refreshDock();
                });
            }

            @Override
            public int getItemCount() {
                return found.size();
            }
        });

        View anchor = findViewById(R.id.btnDock);
        // Sized from its content and hung under the button with the left edges aligned: the same
        // glass dropdown as the Inputs menu, not a settings sheet floating mid-screen.
        dockPicker = glassPanel(panel, anchor, 260);
        dropIn(dockPicker, panel, anchor, Gravity.START);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // ---- wallpaper ------------------------------------------------------------------------------

    /**
     * "Wallpaper" panel: turn the Bing daily picture on or off, pick a picture already on the TV, or
     * drop the wallpaper entirely. The panel is a popup because it is a one-off setting, not a place
     * the user browses.
     */
    private void showWallpaperPicker() {
        if (wallpaperPanel != null && wallpaperPanel.isShowing()) {
            wallpaperPanel.dismiss();
            return;
        }
        wall = new Wallpaper(this);

        View panel = getLayoutInflater().inflate(R.layout.popup_wallpaper, root, false);
        ImageView bingTick = panel.findViewById(R.id.bingTick);
        TextView status = panel.findViewById(R.id.bingStatus);
        TextView columnsStatus = panel.findViewById(R.id.columnsStatus);
        View rowBing = panel.findViewById(R.id.rowBing);
        View rowColumns = panel.findViewById(R.id.rowColumns);
        View rowPick = panel.findViewById(R.id.rowPick);
        View rowOff = panel.findViewById(R.id.rowOff);

        bingTick.setVisibility(wall.autoDaily() ? View.VISIBLE : View.INVISIBLE);
        status.setText(wallpaperStatus());
        columnsStatus.setText(columnsStatus());

        rowBing.setOnClickListener(v -> {
            boolean on = !wall.autoDaily();
            wall.setAutoDaily(on);
            bingTick.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            if (on) {
                status.setText(getString(R.string.bing_fetching));
                toast(getString(R.string.bing_fetching));
                // Off the main thread: this hits the network and must never stall the home screen.
                new Thread(() -> {
                    boolean done = wall.refreshBingIfDue();
                    handler.post(() -> {
                        status.setText(wallpaperStatus());
                        if (done) {
                            toast(getString(R.string.bing_updated));
                            applyWallpaper();
                        } else {
                            toast(getString(R.string.bing_failed));
                        }
                    });
                }).start();
            } else {
                // Only stop the daily change; whatever is on screen now stays.
                status.setText(wallpaperStatus());
                toast(getString(R.string.bing_stopped));
            }
        });

        rowColumns.setOnClickListener(v -> cycleColumns(columnsStatus));

        final TextView menuKeyStatus = panel.findViewById(R.id.menuKeyStatus);
        final View rowMenuKey = panel.findViewById(R.id.rowMenuKey);
        menuKeyStatus.setText(menuKeyEverywhere()
                ? getString(R.string.menu_key_scope_on) : getString(R.string.menu_key_scope_off));
        rowMenuKey.setOnClickListener(v -> {
            boolean on = !menuKeyEverywhere();
            getSharedPreferences(HomeKeyService.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(HomeKeyService.KEY_MENU_EVERYWHERE, on).apply();
            menuKeyStatus.setText(on
                    ? getString(R.string.menu_key_scope_on) : getString(R.string.menu_key_scope_off));
            toast(getString(on ? R.string.menu_key_on : R.string.menu_key_off));
        });

        final TextView motionStatus = panel.findViewById(R.id.motionStatus);
        final View rowMotion = panel.findViewById(R.id.rowMotion);
        motionStatus.setText(reducedMotion()
                ? getString(R.string.reduced_motion_on) : getString(R.string.reduced_motion_off));
        rowMotion.setOnClickListener(v -> {
            boolean on = !reducedMotion();
            getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE).edit().putBoolean(KEY_REDUCED_MOTION, on).apply();
            motionStatus.setText(on ? getString(R.string.reduced_motion_on) : getString(R.string.reduced_motion_off));
            toast(getString(on ? R.string.reduced_motion_turned_on : R.string.reduced_motion_turned_off));
            // The tile size maths reads the zoom factor, so the desk has to be rebuilt for it to apply.
            recreate();
        });

        rowPick.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*");
            try {
                wallpaperPanel.dismiss();
                startActivityForResult(pick, REQ_PICK_WALLPAPER);
            } catch (ActivityNotFoundException e) {
                toast(getString(R.string.not_available));
            }
        });

        rowOff.setOnClickListener(v -> {
            boolean removed = wall.clear();
            bingTick.setVisibility(View.INVISIBLE);
            wall.setAutoDaily(false);
            status.setText(wallpaperStatus());
            // Drop the picture from the desk straight away; a wallpaper that stays on screen after
            // the user asked to remove it looks like the button did nothing.
            removeWallpaper();
            if (dockDecoration != null) {
                dockDecoration.setOverWallpaper(wallpaperPresent());
                grid.invalidateItemDecorations();
            }
            toast(getString(removed ? R.string.wallpaper_removed : R.string.wallpaper_none_toast));
        });

        View anchor = findViewById(R.id.btnWallpaper);
        // Sized from its content and hung under the button with the left edges aligned, exactly like
        // the Inputs menu. No row scaling: a scaled row is what used to need clipping turned off, and
        // the focus pill alone is enough to see where you are.
        wallpaperPanel = glassPanel(panel, anchor, 300);
        dropIn(wallpaperPanel, panel, anchor, Gravity.START);
    }

    /** One line telling the user what the wallpaper is right now. */
    private String wallpaperStatus() {
        if (wall == null) wall = new Wallpaper(this);
        if (wall.hasWallpaper()) {
            long kb = Math.max(1, wall.sizeBytes() / 1024);
            return getString(R.string.wallpaper_on, kb);
        }
        return getString(R.string.wallpaper_none);
    }

    /** The user picked a file in the system picker. Copy it in as the one and only wallpaper. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_WALLPAPER || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (wall == null) wall = new Wallpaper(this);
        if (wall.importFrom(uri)) {
            toast(getString(R.string.wallpaper_set));
            applyWallpaper();
        } else {
            toast(getString(R.string.wallpaper_bad));
        }
    }

    /**
     * A theme's full-screen picture behind everything. Decoded once, downsampled to the screen so a 4K
     * JPEG costs a 1080p bitmap at most (about 8 MB), which is the price of a wallpaper on a 1 GB TV.
     * Kept as a field so a wallpaper can be added or dropped in place, without rebuilding the activity.
     */
    private void showWallpaper(java.io.File file) {
        removeWallpaper();
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            int w = opts.outWidth, h = opts.outHeight;
            if (w <= 0 || h <= 0) return;
            int sample = WallpaperSize.sampleSize(w, h, dm.widthPixels, dm.heightPixels);
            // The limit applies to what is decoded, so a 12 MP photo still works once it is sampled down.
            if (!WallpaperSize.accepted(w / sample, h / sample)) return;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(w / sample, h / sample, dm.widthPixels, dm.heightPixels);
            if (scale != null) {
                opts.inScaled = true;
                opts.inDensity = scale[0];
                opts.inTargetDensity = scale[1];
            }
            opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;   // no alpha needed, half the memory
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            if (bmp == null) return;
            ImageView view = new ImageView(this);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(bmp);
            root.addView(view, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            wallpaperView = view;
        } catch (Exception | OutOfMemoryError e) {
            // A bad or huge picture must never take the home screen down; the plain colour stays.
        }
    }

    private void removeWallpaper() {
        if (wallpaperView != null) {
            root.removeView(wallpaperView);
            wallpaperView = null;
        }
    }

/** Whether a stored column count is one the panel offers (anything else means "automatic"). */
    private boolean isColumnChoice(int n) {
        for (int c : COLUMN_CHOICES) if (c == n) return true;
        return false;
    }

    /**
 * How many icons fit on this screen without them becoming postage stamps: the most columns that still
     * leave each tile at (near) its design size. It reuses the tile sizer's own arithmetic instead of a
     * table of width thresholds, so "automatic" and "what the user sees" can never disagree — and a screen
     * the table had never heard of gets a sensible answer rather than the 3-column fallback. Automatic
     * stops at six: past that the tiles get small enough that asking for them should be deliberate.
 */
private int recommendColumns() {
        int innerDp = getResources().getConfiguration().screenWidthDp;   // the grid spans the window
        int design = Math.round(AppAdapter.DESIGN_W_DP * 0.95f);        // a shade under, so 5 stays 5
        int maxAuto = Math.min(COLUMN_CHOICES[COLUMN_CHOICES.length - 1], 6);
        for (int c = maxAuto; c >= COLUMN_CHOICES[0]; c--) {
            if (AppAdapter.tileWidthPx(innerDp, c, 1f) >= design) return c;
        }
        return COLUMN_CHOICES[0];
    }

    /** Whether the current column count came from the user's choice or from {@link #recommendColumns()}. */
    private boolean columnsAreAutomatic() {
        return !isColumnChoice(getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE).getInt(KEY_COLUMNS, 0));
    }

    /** One line saying how many icons per row the desk is using, and where that number came from. */
    private String columnsStatus() {
        return columnsAreAutomatic()
                ? getString(R.string.columns_auto, COLUMNS)
                : getString(R.string.columns_manual, COLUMNS);
    }

    /**
     * Tapping the row steps through auto → 3 … 8 → auto. A submenu on a remote would cost more presses
     * than it saves, and the current value is printed under the row either way.
     */
    private void cycleColumns(TextView status) {
        int current = getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE).getInt(KEY_COLUMNS, 0);
        int at = -1;
        for (int i = 0; i < COLUMN_CHOICES.length; i++) {
            if (COLUMN_CHOICES[i] == current) { at = i; break; }
        }
        int next = (at >= 0 && at + 1 < COLUMN_CHOICES.length) ? COLUMN_CHOICES[at + 1] : 0;
        getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE).edit().putInt(KEY_COLUMNS, next).apply();
        COLUMNS = next > 0 ? next : recommendColumns();
        if (status != null) status.setText(columnsStatus());
        toast(next > 0 ? getString(R.string.columns_set, COLUMNS)
                : getString(R.string.columns_auto_toast, COLUMNS));
        // The row count changes the grid layout and the dock split, so the desk has to be rebuilt.
        // The panel is closed first: rebuilding underneath an open popup is what left a stuck
        // invisible window on this TV a moment ago.
        if (wallpaperPanel != null) wallpaperPanel.dismiss();
        handler.postDelayed(this::recreate, 150);
    }

    /** Re-apply whatever wallpaper is on disk now, adding or dropping it as needed. */
    private void applyWallpaper() {
        if (wall == null) wall = new Wallpaper(this);
        java.io.File f = wall.file();
        if (f != null && f.isFile() && f.length() > 0) showWallpaper(f);
        else removeWallpaper();
        // The dock band has to follow: opaque over black, translucent over a picture.
        if (dockDecoration != null) {
            dockDecoration.setOverWallpaper(wallpaperPresent());
            grid.invalidateItemDecorations();
        }
    }

    /** Whether a picture is currently behind the desk, either from the theme or the wallpaper file. */
    private boolean wallpaperPresent() {
        return wallpaperView != null || (cfg != null && cfg.wallpaper != null);
    }

    static final class DockHolder extends RecyclerView.ViewHolder {
        final View item;
        final ImageView icon;
        final TextView name;
        final ImageView tick;

        DockHolder(View v) {
            super(v);
            item = v.findViewById(R.id.item);
            icon = v.findViewById(R.id.icon);
            name = v.findViewById(R.id.name);
            tick = v.findViewById(R.id.tick);
        }
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        // Cold start, or a column count the grid has not laid out yet: the tile has no size, and fitting
        // the glass to it would leave it at whatever the XML says until the next key press. Run again as
        // soon as the tile has been measured.
        if (card.getWidth() == 0 || card.getHeight() == 0) {
            card.post(() -> {
                if (card.isFocused() && card.getWidth() > 0) onTileFocused(position, card);
            });
            return;
        }
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        // The tile width changes with the column count and with the screen; refitting on the width alone
        // catches both without measuring anything twice per frame.
        if (lp.width != (int) w || lp.height != (int) h || card.getWidth() != glassCardW) {
            lp.width = (int) w;
            lp.height = (int) h;
            glassCardW = card.getWidth();
            glass.setLayoutParams(lp);
            // GlassView draws its slab inset by pad, i.e. exactly over the zoomed tile: same corners as
            // the tile, which is itself proportionally rounder when a narrow grid made it smaller.
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            if (reducedMotion) glass.setAlpha(1f);
            else glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else if (reducedMotion) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
            showHero(position, r.top - dy);
        } else {
            hideHero();
            // Scroll just enough so the whole glass (not only the tile) is visible.
            Rect want = revealRect;
            want.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (!heroShown) {
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            setAmbient(color);
            if (reducedMotion) {
                heroName.setAlpha(1f);
                ambient.setAlpha(1f);
            } else {
                heroName.animate().alpha(1f).setDuration(260).start();
                ambient.animate().alpha(1f).setDuration(600).start();
            }
            heroShown = true;
        } else if (reducedMotion) {
            // No cross-fade: swap the label and the glow where they stand.
            if (changed) {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(0f);
            }
            if (color != ambientColor) setAmbient(color);
            heroName.setAlpha(1f);
            ambient.setAlpha(1f);
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        } else {
            heroName.setY(y);
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c});
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        if (reducedMotion) {
            heroName.setAlpha(0f);
            ambient.setAlpha(0f);
            return;
        }
        heroName.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        if (reducedMotion) {
            glass.setAlpha(0f);
            return;
        }
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) return false;          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                text = getString(R.string.offline);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = getString(R.string.ethernet);
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * China-market MIUI TV firmware answers no standard Settings intent at all — both
     * android.settings.SETTINGS and WIFI_SETTINGS resolve to nothing on those sets — so the Settings
     * and Wi-Fi pills dead-end there. Open Xiaomi's own settings app (com.xiaomi.mitv.settings) instead.
     * On firmware that has a standard handler this returns the standard intent unchanged.
     */
    private Intent systemIntent(String standardAction) {
        return systemIntent(this, standardAction);
    }

    /**
     * A system settings screen that is not always there. China-market MIUI TV firmware ignores the
     * standard action, so the Xiaomi settings app is tried as well — see {@code <queries>} in the
     * manifest, without which resolveActivity cannot even see it on Android 11+.
     */
    static Intent systemIntent(android.content.Context ctx, String standardAction) {
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(ctx.getPackageManager()) != null) return i;
        i = new Intent().setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        if (i.resolveActivity(ctx.getPackageManager()) != null) return i;
        return new Intent(standardAction);   // nothing found: let open() show the can't-open toast
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (tunerId == null || info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        // A vendor panel configured in config.xml, with no Live TV row to sit beside it: the panel is
        // the whole menu. Sets like the BYINTEK U4 report HDMI ports they do not have, and their own
        // picker is the honest one.
        if (!panelConfigured() && liveTv == null) {
            if (rows.getChildCount() == 0) {
                // Nothing to list either (Xiaomi and some Chinese ROMs do not report their ports).
                Intent own = sourcePanelIntent();
                if (own == null) {
                    Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
                    return;
                }
                final Intent target = own;
                addInputRow(rows, popup, R.drawable.ic_av, getString(R.string.input_source_settings), -1,
                        () -> openSourcePanel());
            }
        } else if (panelConfigured() && liveTv == null) {
            openSourcePanel();
            return;
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        dropIn(popup, panel, anchor, Gravity.START);
        rows.getChildAt(0).requestFocus();
    }

    /**
     * A glass dropdown built the way the Inputs menu is: as wide as its widest row but never most of
     * the screen, and no taller than the room under the button, so a long list scrolls inside the
     * panel instead of running off the bottom of the TV.
     */
    private PopupWindow glassPanel(View panel, View anchor, int minWidthDp) {
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max(Math.max(anchor.getWidth(), (int) (minWidthDp * density)),
                panel.getMeasuredWidth());
        width = Math.min(width, (int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        int height = Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density)));
        PopupWindow popup = new PopupWindow(panel, width, height, true);
        popup.setElevation(12 * density);
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        if (reducedMotion) {
            // Weak boxes: the unfold costs frames the user notices as lag, so just put it on screen.
            popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
            return;
        }
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}

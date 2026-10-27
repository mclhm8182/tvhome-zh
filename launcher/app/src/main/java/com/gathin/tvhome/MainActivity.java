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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    /** Every tile shows its name, not only the focused one. */
    private static final String KEY_ALWAYS_SHOW_NAMES = "always_show_names";
    /**
     * Whether this launcher answers the system's input-switcher request
     * (com.android.tv.action.VIEW_INPUTS), which is what the remote's source button becomes once the
     * stock launcher is gone. Off by default: a TV with its own source picker should keep it.
     */
    private static final String KEY_HANDLE_VIEW_INPUTS = "handle_view_inputs";
    /** Whether tiles with no artwork of their own are drawn as one set: mark on a colour wash. */
    private static final String KEY_UNIFIED_TILES = "unified_tiles";
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
    /**
     * Open the Inputs menu. The system's input-switcher request is forwarded here by
     * {@link InputsRequestActivity}, and the desk's own source button asks for it too.
     */
    public static final String ACTION_SHOW_INPUTS = "com.gathin.tvhome.action.SHOW_INPUTS";
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    /** The lunar date beside the date; stays gone unless the TV speaks Chinese. */
    private TextView lunar;
    private TextView btnWifi;
    /** The "Favourites" panel, so Back can close it. */
    private PopupWindow dockPicker;
    /** The app library panel, open while the user picks apps for the desk. */
    private AppLibraryPanel libraryPanel;
    /**
     * A tile is picked up and the arrow keys carry it. {@link #carried} says which one; Back puts it
     * down and keeps the new order.
     */
    private boolean reordering;
    private int carried = -1;
    /** Whether the OK button is down right now: a held button is one press, not many. */
    private boolean okDown;
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
            updateLunar();
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
        if (intent.getAction() != null) {
            // The system's input switcher asked for the Inputs menu.
            if (MainActivity.ACTION_SHOW_INPUTS.equals(intent.getAction())) {
                grid.post(this::showInputs);
            }
            return;
        }
        // Only the Home key (re-issued by HomeKeyService, or the system on remotes that send
        // KEYCODE_HOME) sends a HOME intent to an already-running desktop; treat it as "go to top".
        if (!intent.hasCategory(Intent.CATEGORY_HOME)) return;
        scrollToTop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Typefaces.apply(findViewById(android.R.id.content));
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
        weather = new Weather(this);
        setupWeather();
        if (cfg.wallpaper != null) showWallpaper(cfg.wallpaper);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        lunar = findViewById(R.id.lunar);
        if (Lunar.wanted()) {
            lunar.setVisibility(View.VISIBLE);
            updateLunar();
        }
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
                // The grid's bottom edge, not its padding: paddingBottom only lets the last row scroll
                // clear of the edge, and treating it as off-screen nudged the grid up whenever the last
                // visible row got focus.
                int limitBottom = getHeight();
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
        // A carried tile slides to its new place and the ones in between step aside: the grid
        // animates the move itself. Only the move is animated — a change animation on a refresh
        // made the whole desk flash, and a long jump scrolled instead of sliding.
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setMoveDuration(240);
        animator.setAddDuration(0);
        animator.setRemoveDuration(0);
        animator.setChangeDuration(0);
        animator.setSupportsChangeAnimations(false);
        grid.setItemAnimator(animator);
        // The glass highlight was placed when the tile took focus, which can be before the scroll
        // that brings it on screen has finished: measured on the old coordinates, it ended up
        // sitting over whatever row had since scrolled into its place. Re-place it — the spring
        // then glides it over — once the grid stops moving.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                View card = rv.findFocus();
                if (card == null || card.getId() != R.id.card) return;
                int pos = rv.getChildAdapterPosition((View) card.getParent());
                if (pos != RecyclerView.NO_POSITION) onTileFocused(pos, card);
            }
        });
        dockDecoration = new DockDecoration(COLUMNS, density, wallpaperPresent());
        dockDecoration.setNames(alwaysShowNames());
        grid.addItemDecoration(dockDecoration);
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        reducedMotion = getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE)
                .getBoolean(KEY_REDUCED_MOTION, false);
        AppAdapter.setReducedMotion(reducedMotion);
        adapter = new AppAdapter(this, this, COLUMNS, cfg.dockApps);
        // The names of the tiles the user cannot recognise are no help, so they can all stay on screen.
        AppAdapter.setAlwaysShowNames(alwaysShowNames());
        AppAdapter.setUnifiedTiles(unifiedTiles());
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
        findViewById(R.id.btnLibrary).setOnClickListener(v -> showAppLibrary());

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
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnWallpaper) {
                // The weather line sits under the clock, to the left of the bar: left from the first
                // pill goes there, and the bar keeps the height it had before the weather existed.
                if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) {
                    weatherLine.requestFocus();
                }
                return true;
            }
            // The last pill swallows RIGHT: there is nothing to the right of the bar, and the
            // focus finder would otherwise jump to a tile far below. It has to be the last one
            // that is really last — the app library button was added after this was written, and
            // while the bar ended at Settings the key was swallowed there and the library button
            // could never be reached with the remote.
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnLibrary;
        };
        // The top of the desk reads as one row: the buttons sit on the clock's middle line. The
        // clock's own box is taller than its digits — numbers do not descend — so the line is found
        // from the font: the middle of the digits themselves. The date is a row of its own above the
        // clock now, so it is not part of this alignment.
        final View clockRow = findViewById(R.id.clockRow);
        final View topButtons = findViewById(R.id.topButtons);
        final android.widget.TextClock clock = findViewById(R.id.clock);
        clockRow.post(() -> {
            int rowH = clockRow.getHeight();
            int barH = topButtons.getHeight();
            if (rowH <= 0 || barH <= 0) return;
            // The ink of a digit reaches from about 0.68 of the em above the baseline down to the
            // baseline itself — numbers do not descend. Measured against the rendered clock, that is
            // where the eye reads the middle of the digits.
            float baseline = clock.getBaseline();
            float digitMiddle = baseline - clock.getTextSize() * 0.34f;
            float line = clock.getTop() + digitMiddle;          // where that is in the row
            // The buttons hang from the row's bottom: lift them so their middle lands on the line.
            android.widget.RelativeLayout.LayoutParams lp =
                    (android.widget.RelativeLayout.LayoutParams) topButtons.getLayoutParams();
            lp.bottomMargin = Math.round(rowH - (line + barH / 2f));
            topButtons.setLayoutParams(lp);
        });

        findViewById(R.id.btnDock).setOnKeyListener(pillKeys);
        findViewById(R.id.btnWallpaper).setOnKeyListener(pillKeys);
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);
        findViewById(R.id.btnLibrary).setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // The default-network callback is Android 7 (24). Older releases still get the label
        // refreshed by the broadcasts below; only the live "ethernet/wifi" switch is missing.
        if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(networkCallback);
        }

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
        // Whether this launcher answers the system's input-switcher request.
        applyInputKeyHandling();
    }

    /** Opens one of the desk's own panels, whichever route asked for it (quick settings or a cold start). */
    private void openPanelFromQuickSettings(String action) {
        if (ACTION_DESKTOP_SETTINGS.equals(action)) grid.post(this::showWallpaperPicker);
        else if (ACTION_DOCK_PICKER.equals(action)) grid.post(this::showDockPicker);
        else if (ACTION_SHOW_INPUTS.equals(action)) grid.post(this::showInputs);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The tiles are rebuilt only when Home was really left behind (another app took the screen)
        // or when the system took them away for memory. A panel over the desk — quick settings, the
        // weather sheet — only pauses us, and rebuilding there replayed the entrance animation for
        // nothing.
        boolean rebuild = awayFromDesk;
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
        // The adapter was let go of when memory ran short: put it back before loading the tiles again.
        if (grid.getAdapter() == null) grid.setAdapter(adapter);
        if (wallpaperReleased) {
            wallpaperReleased = false;
            applyWallpaper();
        }
        if (rebuild || adapter.isEmpty()) {
            adapter.reload();
            grid.scheduleLayoutAnimation();
        }
        if (weather != null && weather.enabled()) refreshWeather();
        updateLunar();
        updateWifiLabel();
        handler.postDelayed(minuteTick, MINUTE_MS);
        if (weather != null && weather.enabled() && weather.hasPlace()) {
            handler.postDelayed(weatherTick, Weather.REFRESH_MS);
        }

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
        handler.removeCallbacks(weatherTick);   // nothing is fetched while the desk is off screen
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
    }

    @Override
    protected void onStop() {
        // Another app took the screen. A translucent overlay (the panels, the quick settings)
        // only pauses us, so this really does mean the desk was left behind.
        awayFromDesk = true;
        started = false;
        super.onStop();
    }

    /**
     * The system wants memory back. The banners behind the tiles are the largest thing this launcher
     * holds, so when Home is in the background and the box is short on memory, they go: the tiles, the
     * adapter itself and the wallpaper bitmap are let go, and onResume puts them back.
     *
     * <p>Not while the desk is on screen. RUNNING_LOW and CRITICAL also arrive then, because an
     * accessibility service keeps the process in the foreground, and blanking the screen the owner is
     * looking at would be worse than the memory.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        boolean due = level >= TRIM_MEMORY_BACKGROUND
                || level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL;
        if (!due || started || adapter == null) return;
        adapter.clear();
        // Detach the adapter as well: with Home stopped no layout pass runs, so the tile views (and the
        // bitmaps they hold) would stay attached until Home comes back.
        grid.setAdapter(null);
        grid.getRecycledViewPool().clear();
        if (wallpaperView != null) {
            removeWallpaper();
            wallpaperReleased = true;
        }
    }

    @Override
    protected void onDestroy() {
        live = null;
        super.onDestroy();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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

    /** Whether every tile keeps its name on screen, not only the focused one. */
    /**
     * Whether tiles with no artwork of their own are drawn as one set — the app's mark, sized by
     * area and whitened, on a diagonal wash of the app's own colour — instead of the app's icon
     * scaled up with its own edges stretched out to the tile's sides.
     */
    boolean unifiedTiles() {
        return getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE)
                .getBoolean(KEY_UNIFIED_TILES, true);
    }

    boolean alwaysShowNames() {
        return getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE)
                .getBoolean(KEY_ALWAYS_SHOW_NAMES, false);
    }

    /**
     * Whether this launcher answers the system's input-switcher request. The helper activity is
     * switched on and off with this, so a TV with its own source picker never sees a second
     * candidate and a chooser nobody asked for.
     */
    boolean handleViewInputs() {
        return getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE)
                .getBoolean(KEY_HANDLE_VIEW_INPUTS, false);
    }

    /**
     * Turns the input-switcher helper on or off. The component stays declared but disabled, so the
     * system's request finds nothing unless the user asked for this launcher to answer it.
     */
    void applyInputKeyHandling() {
        try {
            PackageManager pm = getPackageManager();
            int state = handleViewInputs()
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            pm.setComponentEnabledSetting(
                    new ComponentName(this, InputsRequestActivity.class), state,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception e) {
            // Nothing to do: the helper stays as it was, and the menu still opens from the desk.
        }
    }

    /**
     * Open one input, the way that works on this TV. Three ways, in order:
     *
     * <p>1. the vendor's own picker with this port's number, when config.xml maps it ({@code input_action}
     *    + {@code input_sources}). MIUI TV is the case: its HDMI ports answer nothing to the standard
     *    channel URI, but {@code com.xiaomi.mitv.tvplayer} takes {@code --ei input 23} for HDMI 1.
     * <p>2. the standard way: ACTION_VIEW on the passthrough input's channel URI. What most TVs want.
     * <p>3. the vendor action with that URI, for sets whose player wants the action spelled out.
     */
    private void openInput(String inputId) {
        // A CEC device (an Apple TV, a soundbar) has an input of its own whose parent is the port it
        // sits on: switch the port, not the device.
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            try {
                TvInputInfo info = tim.getTvInputInfo(inputId);
                if (info != null && info.getParentId() != null) inputId = info.getParentId();
            } catch (Throwable ignored) {
                // no such input: use the id as it is
            }
        }
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        String action = cfg.inputAction == null ? "" : cfg.inputAction.trim();

        // 1. Xiaomi China's firmware: the standard URI is either refused or, worse, accepted and
        //    ignored, so the vendor's player is asked first — it takes the port number as an int
        //    extra and switches the port.
        Integer vendor = VendorInputs.sourceNumber(inputId, vendorSourceNumbers);
        if (vendor != null) {
            for (Intent i : VendorInputs.sourceIntents(vendor)) {
                try {
                    startActivity(i);
                    return;
                } catch (ActivityNotFoundException | SecurityException e) {
                    // try the next form
                }
            }
        }
        // 2. a port number the user mapped in config.xml, with the vendor's action
        Integer source = mappedSource(inputId);
        if (source != null && !action.isEmpty()) {
            try {
                startActivity(new Intent(action).putExtra("input", source)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                // fall through to the standard way
            }
        }
        // 3. the standard way: what most TVs want
        try {
            startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "vnd.android.cursor.item/channel")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            // no handler for the URI: try what is left below
        }
        // 4. the vendor action with the URI
        if (!action.isEmpty()) {
            open(new Intent(action).setDataAndType(uri, "vnd.android.cursor.item/channel"));
            return;
        }
        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
    }

    /** The vendor's own number for this input ("HDMI1=23" -> 23), or null when it is not mapped. */
    private Integer mappedSource(String inputId) {
        if (cfg.inputSources == null || cfg.inputSources.trim().isEmpty()) return null;
        // The input id ends in the port the TV itself names (".../.Hdmi1InputService/HW5"), so the
        // mapping is matched on that, case aside.
        String id = inputId.toLowerCase(Locale.ROOT);
        for (String pair : cfg.inputSources.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String port = pair.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!port.isEmpty() && id.contains(port)) {
                try {
                    return Integer.parseInt(pair.substring(eq + 1).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
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
    /** True between onStart and onStop: the desk is (at least partly) on screen. */
    private boolean started;
    /** Set when the wallpaper bitmap was handed back to the system; it comes back on resume. */
    private boolean wallpaperReleased;
    /** The weather line under the clock, and the loop that keeps it current. */
    private Weather weather;
    private View weatherLine;
    private ImageView weatherIcon;
    private TextView weatherText;
    private Weather.Report weatherReport;
    private PopupWindow weatherPanel;
    /** The vendor's own port numbers, when this set needs them (Xiaomi China firmware). */
    private java.util.Map<String, Integer> vendorSourceNumbers = java.util.Collections.emptyMap();
    private final Runnable weatherTick = this::refreshWeather;
    /** One thread for the network: the weather, the city search, the locate. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
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
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            // The button that picks a tile up is held down, and the remote keeps sending the
            // press while it is held. Those repeats are not a second press: treating them as one
            // put the tile straight back down again, and the move never got off the ground.
            boolean repeat = okDown && event.getAction() == KeyEvent.ACTION_DOWN;
            if (event.getAction() == KeyEvent.ACTION_DOWN) okDown = true;
            else if (event.getAction() == KeyEvent.ACTION_UP) okDown = false;
            if (reordering) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return true;  // letting go: the tile stays in the hand
                if (!repeat) endReorder();
                return true;
            }
        }
        // A picked-up tile: the arrow keys carry it, Back puts it down. This comes before the rest so
        // a direction never scrolls the grid out from under the tile being carried.
        if (reordering) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                moveCarried(code);
                return true;
            }
            if (code == KeyEvent.KEYCODE_BACK) {
                endReorder();
                return true;
            }
            return true;   // anything else is held until the tile is put down
        }
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
    /**
     * The remote's held OK button: the same menu, reached by position rather than by the entry a
     * long-click hands over. A remote's long press is a stream of repeated key events, not a
     * touch, so it never reaches the long-click listener at all.
     */
    @Override
    public void onTileLongPressedAt(int position) {
        if (reordering || adapter == null) return;
        if (position < 0 || position >= adapter.getItemCount()) return;
        // The remote picks the tile up straight away, the way the Apple TV does: the arrow keys
        // carry it and Back puts it down. A tile among the favourites is carried the same way —
        // the favourites are the top row of the same list.
        startMove(position);
    }

    @Override
    /**
     * Hold OK on a tile: a glass menu next to it. The dock edit is what the long press used to do on
     * its own; Move picks the tile up so the arrow keys carry it and Back puts it down; the rest take
     * the app off the desk (only for one the user put there by hand) or open its page.
     */
    public void onTileLongPressed(AppAdapter.Entry entry, boolean alreadyPinned) {
        if (reordering) return;
        final int position = adapter.positionOf(entry.component.flattenToShortString());
        if (position < 0) return;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        Typefaces.apply(panel);
        Glass.capture(root);
        applyGlass(panel);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = new PopupWindow(panel,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(12 * density);

        addTileMenuRow(rows, popup,
                alreadyPinned ? R.string.dock_remove_from : R.string.dock_add_to,
                () -> editDock(entry, alreadyPinned));
        // Every tile can be moved: the favourites are the bottom row of the same desk, not a
        // place of their own, so a tile moves between the two the way it moves inside either.
        addTileMenuRow(rows, popup, R.string.tile_move, () -> startMove(position));
        if (new LibraryApps(this).isAdded(entry.component.flattenToString())) {
            addTileMenuRow(rows, popup, R.string.app_library_remove, () -> {
                new LibraryApps(this).remove(entry.component.flattenToString());
                adapter.reload();
                toast(getString(R.string.app_library_removed, entry.label));
            });
        }
        addTileMenuRow(rows, popup, R.string.tile_hide, () -> {
            new HiddenApps(this).hide(entry.pkg);
            // A hidden app left pinned in the dock would leave a hole in the row, so it comes out
            // of the dock as well. Restoring it later puts it back in the grid, not the dock.
            DockStore dockStore = adapter.dock();
            if (dockStore.isPinned(entry.pkg)) dockStore.unpin(entry.pkg);
            adapter.reload();
            toast(getString(R.string.app_hidden, entry.label));
        });
        addTileMenuRow(rows, popup, R.string.tile_app_info, () -> open(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + entry.pkg))));

        // The tile itself, not the cell it sits in: the cell is as wide as the column and the tile
        // is centred in it, so hanging the menu off the cell put it off to the side of the tile.
        View item = grid.getLayoutManager().findViewByPosition(position);
        View card = item != null ? item.findViewById(R.id.card) : null;
        if (card == null) card = findViewById(R.id.btnWallpaper);
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max(panel.getMeasuredWidth(), (int) (150 * density));
        int height = panel.getMeasuredHeight();
        popup.setWidth(width);
        popup.setHeight(height);

        // A tile near the bottom of the screen opens its menu upwards, so the menu is never taller
        // than the room left under it.
        int[] at = new int[2];
        card.getLocationInWindow(at);
        int gap = (int) (10 * density);
        // The menu sits in the middle of the tile rather than against its left edge. Chasing the
        // edge meant reading the tile's scale, and the scale is a state that changes under the
        // menu: the long press takes the focus away, the tile shrinks back, and the edge the
        // menu was lined up with is no longer the edge on screen. The middle does not move.
        int centre = at[0] + card.getWidth() / 2;
        int x = centre - width / 2;
        if (x < 0) x = 0;
        if (x + width > root.getWidth()) x = Math.max(0, root.getWidth() - width);
        boolean below = at[1] + card.getHeight() + gap + height <= root.getHeight();
        int y = below ? at[1] + card.getHeight() + gap : Math.max(0, at[1] - gap - height);

        if (reducedMotion) {
            popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
        } else {
            panel.setAlpha(0f);
            panel.setPivotY(below ? 0f : height);
            panel.setScaleY(0.92f);
            panel.setTranslationY(below ? -8 * density : 8 * density);
            popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
            panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        }
        // Ask for the focus twice: the popup's window has not taken it yet when the panel is first
        // shown, and a request that comes too early is dropped — the remote would then walk over the
        // menu and open the app behind it.
        rows.getChildAt(0).requestFocus();
        panel.post(() -> rows.getChildAt(0).requestFocus());
    }

    /** One row of the tile menu: a plain label, no icon and no status. */
    private void addTileMenuRow(LinearLayout rows, PopupWindow popup, int title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        Typefaces.apply(row);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- moving tiles --------------------------------------------------------------------------

    /**
     * Picks the tile up: the arrow keys carry it, Back puts it down and keeps the new order.
     * Left and Right step across a row boundary, Up and Down move a whole row, which is how the
     * grid itself is laid out.
     */
    private boolean startMove(int position) {
        if (position < 0 || position >= adapter.getItemCount()) return false;
        reordering = true;
        carried = position;
        // The tile is in the hand now: it grows and lifts off the wall.
        AppAdapter.setCarrying(true);
        // The highlight chases the tile harder while it is being carried: it has to keep up with
        // a tile that slides over in a quarter of a second.
        setSpring(520f, 0.82f);
        View focused = grid.findFocus();
        if (focused != null) adapter.refreshLook(focused);
        toast(getString(R.string.reorder_hint));
        focusTile(carried);
        // The highlight is put on the tile on every frame while it is on the way: the tile moves
        // with the grid's own animation and the highlight is a separate view, so it has to be
        // told where the tile is now, not where it will end up.
        grid.removeCallbacks(followCarried);
        grid.post(followCarried);
        return true;
    }

    /**
     * Moves the carried tile.
     *
     * <p>The desk is one list: the favourites are its bottom row and the grid is what lies above
     * it, so up from the top grid row puts a tile among the favourites, down from the favourites
     * takes it back into the grid, and left and right change the order of the row the way they
     * change the order of the grid.
     */
    private void moveCarried(int keyCode) {
        int count = adapter.getItemCount();
        if (count == 0 || carried < 0) return;
        int to;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: to = carried - 1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: to = carried + 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: to = carried - COLUMNS; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: to = carried + COLUMNS; break;
            default: return;
        }
        if (to < 0) return;                       // the top of the desk: nowhere to go
        if (to >= count) {
            // The last row is not full: down puts the tile at the end of it.
            if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN || to <= carried) return;
            to = count - 1;
        }
        if (to == carried) return;
        int from = carried;
        if (!adapter.move(from, to)) return;
        carried = to;
        // The tile keeps the focus — it is the same view, only in another place — and the grid
        // scrolls just enough to keep it on screen.
        scrollCarriedIntoView();
    }

    /** Brings the tile in the hand on screen, without a jump: the grid scrolls just enough. */
    private void scrollCarriedIntoView() {
        grid.post(() -> {
            if (!reordering) return;
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(carried);
            if (vh == null) return;
            View cell = vh.itemView;
            Rect want = new Rect(0, 0, cell.getWidth(), cell.getHeight());
            grid.requestChildRectangleOnScreen(cell, want, false);
        });
    }

    /**
     * Keeps the highlight on the tile in the hand while the grid slides it over.
     *
     * <p>The tile moves with the grid's own animation and the highlight is a view of its own: it
     * has to be put where the tile is on every frame, or it stands still while the tile slides
     * away from under it and comes to rest over a different app.
     */
    private final Runnable followCarried = new Runnable() {
        @Override
        public void run() {
            if (!reordering) return;
            View card = carriedCard();
            if (card != null) {
                fitGlass(card);
                parkCarriedRow();
            }
            grid.postOnAnimation(this);
        }
    };

    /** The tile in the hand, if the grid has it on screen. */
    private View carriedCard() {
        if (adapter == null || carried < 0 || carried >= adapter.getItemCount()) return null;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(carried);
        if (vh == null) return null;
        return vh.itemView.findViewById(R.id.card);
    }

    /**
     * Keeps the bottom row of the desk where it belongs — at the foot of the screen — while a
     * tile is carried into it or out of it.
     */
    private void parkCarriedRow() {
        if (carried >= COLUMNS) return;
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(carried);
        if (vh == null) return;
        View cell = vh.itemView;
        int decoratedTop = cell.getTop() - (int) (6 * density);
        int dy = decoratedTop - grid.getPaddingTop();
        if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
    }

    /** Puts the carried tile down and keeps the order the user left it in. */
    private void endReorder() {
        if (!reordering) return;
        reordering = false;
        grid.removeCallbacks(followCarried);
        AppAdapter.setCarrying(false);
        setSpring(260f, 0.58f);
        View focused = grid.findFocus();
        if (focused != null) adapter.refreshLook(focused);
        // The press that ends the move was taken by the launcher: the app behind the tile must
        // not open when the button is let go.
        AppAdapter.resetLongPress(focused);
        // The order is kept as it stands: the tiles are already where the user put them, so
        // there is nothing to rebuild. A rebuild unbound every tile at once, which dropped the
        // focus and left the highlight over a different app.
        OrderStore.save(this, adapter.orderKeys());
        carried = -1;
        toast(getString(R.string.reorder_saved));
    }

    /**
     * The highlight's spring: softer on the desk, harder while a tile is in the hand.
     *
     * <p>The force is tuned in place rather than replaced: a spring that is already running holds
     * the place it is heading for in its force, and a fresh force has none — the animation would
     * come to a stop with an error the first time it drew a frame.
     */
    private void setSpring(float stiffness, float damping) {
        if (springX == null || springY == null) return;
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            SpringForce force = s.getSpring();
            if (force == null) continue;
            force.setStiffness(stiffness);
            force.setDampingRatio(damping);
        }
    }

    /** Puts the app in the dock, or takes it back out — what holding OK used to do on its own. */
    private void editDock(AppAdapter.Entry entry, boolean alreadyPinned) {
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
        Typefaces.apply(panel);
        RecyclerView list = panel.findViewById(R.id.rows);
        list.setLayoutManager(new LinearLayoutManager(this));

        final List<DockApp> found = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            if (ri.activityInfo == null) continue;
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            found.add(new DockApp(ri.activityInfo.packageName,
                    String.valueOf(ri.loadLabel(pm)), ri.loadIcon(pm)));
        }
        // The apps the user put on the desk by hand belong here too: they are on the desk, so they
        // can be pinned to the dock like any other.
        for (String component : new LibraryApps(this).added()) {
            ComponentName cn = ComponentName.unflattenFromString(component);
            if (cn == null) continue;
            try {
                android.content.pm.ActivityInfo ai = pm.getActivityInfo(cn, 0);
                String pkg = cn.getPackageName();
                boolean already = false;
                for (DockApp a : found) if (a.pkg.equals(pkg)) { already = true; break; }
                if (already) continue;
                found.add(new DockApp(pkg, String.valueOf(ai.loadLabel(pm)), ai.loadIcon(pm)));
            } catch (Exception e) {
                // Uninstalled since it was added.
            }
        }
        Collections.sort(found, (a, b) -> a.label.compareToIgnoreCase(b.label));

        final DockStore dock = adapter.dock();
        list.setAdapter(new RecyclerView.Adapter<DockHolder>() {
            @Override
            public DockHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_dock, parent, false);
                Typefaces.apply(v);
                return new DockHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull DockHolder h, int position) {
                DockApp app = found.get(position);
                String label = app.label;
                String pkg = app.pkg;
                h.name.setText(label);
                h.icon.setImageDrawable(app.icon);
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

    /** The packages that have a tile on the desk right now, for the app library to tick. */
    java.util.Set<String> deskPackages() {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (adapter == null) return out;
        for (AppAdapter.Entry e : adapter.items()) out.add(e.pkg);
        return out;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /**
     * The app library: every app the TV can start, so the ones that declare no TV entry point can
     * be put on the desk by hand. The panel loads its list in the background and keeps only the
     * labels between openings.
     */
    private void showAppLibrary() {
        if (libraryPanel != null) {
            libraryPanel.dismiss();
            libraryPanel = null;
        }
        // The panel hangs off the button that opened it, not off some other one: it used to appear
        // under the "Desktop" button at the far end of the bar, which read as the wrong panel.
        View anchor = findViewById(R.id.btnLibrary);
        libraryPanel = new AppLibraryPanel(this, new LibraryApps(this), () -> {
            // The tiles changed: rebuild them, without the entrance animation over the panel.
            adapter.reload();
        });
        libraryPanel.show(anchor);
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
        Typefaces.apply(panel);
        // The rows scroll: there are more of them than the room under the top bar holds, so the
        // scrolling area is given that room and the rest is reached with the remote. A ScrollView
        // left to wrap its content would measure all ten rows and put the last ones off the screen
        // with no way to scroll to them.
        View anchor = findViewById(R.id.btnWallpaper);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (30 * density);
        View scroll = panel.findViewById(R.id.scroll);
        ViewGroup.LayoutParams scrollLp = scroll.getLayoutParams();
        scrollLp.height = Math.max((int) (6 * 52 * density), room - (int) (12 * density));
        scroll.setLayoutParams(scrollLp);
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

        // Every tile keeps its name, not only the focused one.
        final TextView namesStatus = panel.findViewById(R.id.namesStatus);
        final View rowNames = panel.findViewById(R.id.rowNames);
        namesStatus.setText(alwaysShowNames()
                ? getString(R.string.always_show_names_on) : getString(R.string.always_show_names_off));
        rowNames.setOnClickListener(v -> {
            boolean on = !alwaysShowNames();
            getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ALWAYS_SHOW_NAMES, on).apply();
            namesStatus.setText(on
                    ? getString(R.string.always_show_names_on) : getString(R.string.always_show_names_off));
            AppAdapter.setAlwaysShowNames(on);
            adapter.notifyDataSetChanged();   // the tiles on screen take the new alpha at once
            // The dock's names sit on the band, so the band has to grow over them.
            dockDecoration.setNames(on);
            grid.invalidateItemDecorations();
        });

        // How tiles with no artwork of their own are drawn.
        final View rowUnified = panel.findViewById(R.id.rowUnified);
        final TextView unifiedStatus = panel.findViewById(R.id.unifiedStatus);
        final boolean unifiedNow = unifiedTiles();
        rowUnified.setOnClickListener(row -> {
            boolean unifiedOn = !unifiedTiles();
            getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE).edit()
                    .putBoolean(KEY_UNIFIED_TILES, unifiedOn).apply();
            AppAdapter.setUnifiedTiles(unifiedOn);
            unifiedStatus.setText(unifiedOn
                    ? getString(R.string.tile_style_set) : getString(R.string.tile_style_icon));
            // The tile faces are built once and kept, so they have to be thrown away for the new
            // style to take effect; the banners are not touched.
            adapter.dropTileArt();
            adapter.notifyDataSetChanged();
        });
        unifiedStatus.setText(unifiedNow
                ? getString(R.string.tile_style_set) : getString(R.string.tile_style_icon));

        // Whether the remote's source button opens this launcher's Inputs menu.
        final TextView inputKeyStatus = panel.findViewById(R.id.inputKeyStatus);
        final View rowInputKey = panel.findViewById(R.id.rowInputKey);
        inputKeyStatus.setText(handleViewInputs()
                ? getString(R.string.input_source_key_on) : getString(R.string.input_source_key_off));
        rowInputKey.setOnClickListener(v -> {
            boolean on = !handleViewInputs();
            getSharedPreferences(PREFS_LAYOUT, MODE_PRIVATE)
                    .edit().putBoolean(KEY_HANDLE_VIEW_INPUTS, on).apply();
            inputKeyStatus.setText(on
                    ? getString(R.string.input_source_key_on) : getString(R.string.input_source_key_off));
            applyInputKeyHandling();
        });

        // The apps the user took off the desk are put back from the app library at the far end of
        // the top bar: it lists every app the TV can start, so it is the one place where an app
        // that is not on the desk can be brought back. A second list for the same thing was one
        // menu too many.
        final TextView weatherStatus = panel.findViewById(R.id.weatherStatus);
        final View rowWeather = panel.findViewById(R.id.rowWeather);
        weatherStatus.setText(weather.enabled() && weather.hasPlace()
                ? weather.placeName() : getString(R.string.weather_state_off));
        rowWeather.setOnClickListener(v -> toggleWeather(weatherStatus));

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

        // Hung under the button with the left edges aligned, exactly like the Inputs menu. No row
        // scaling: a scaled row is what used to need clipping turned off, and the focus pill alone
        // is enough to see where you are.
        wallpaperPanel = glassPanel(panel, anchor, 300);
        dropIn(wallpaperPanel, panel, anchor, Gravity.START);
        // The rows live in a ScrollView now, and a ScrollView takes the focus for itself: the rows
        // have to be asked for it, or the remote walks over an empty panel.
        rowBing.requestFocus();
        panel.post(() -> rowBing.requestFocus());
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
            // The glass panels sample the wallpaper, so they need their own copy of it, softened.
            Glass.setWallpaper(bmp, dm.widthPixels, dm.heightPixels);
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
        Glass.setWallpaper(null, 0, 0);
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

    /** One app in the Favourites panel: the ones with a TV entry point, plus the ones added by hand. */
    private static final class DockApp {
        final String pkg;
        final String label;
        final android.graphics.drawable.Drawable icon;

        DockApp(String pkg, String label, android.graphics.drawable.Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        // While a tile is being carried the highlight belongs to it and to nothing else. The
        // focus can wander — a rebind, a scroll, a row being laid out again — and every time it
        // did the highlight jumped over to whatever app the focus had landed on.
        if (reordering && position != carried) {
            focusTile(carried);
            return;
        }
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
        fitGlass(card);

        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
            showHero(position, tileRect.top - dy);
        } else {
            hideHero();
            // Scroll just enough so the whole glass (not only the tile) is visible.
            float pad = 9f * density;        // matches GlassView's glow inset
            Rect want = revealRect;
            want.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    /**
     * Puts the highlight over a tile: it is fitted to the tile's size and glides to where the
     * tile is.
     *
     * <p>The tile is measured where it stands at this moment, slide included: the grid moves the
     * cell it is animating by translating it, and a highlight measured on the old coordinates
     * ends up sitting over whatever row has since slid into its place.
     */
    private void fitGlass(View card) {
        if (card.getWidth() == 0 || card.getHeight() == 0) return;
        Rect r = tileRect;
        tileRectInRoot(card, r);
        float pad = 9f * density;        // matches GlassView's glow inset
        float zoom = AppAdapter.carrying() ? AppAdapter.CARRY_SCALE : AppAdapter.FOCUS_SCALE;
        float w = card.getWidth() * zoom + 2 * pad;
        float h = card.getHeight() * zoom + 2 * pad;
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
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * zoom);
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
    }

    /**
     * Where the tile stands on the desk right now, slide included.
     *
     * <p>The platform's own helper does not follow the slide a move animation draws: the grid
     * moves the cell it is animating by translating it, so the tile has to be measured by hand.
     */
    private void tileRectInRoot(View card, Rect out) {
        int dx = 0, dy = 0;
        View v = card;
        while (v != null && v != root) {
            dx += v.getLeft() + Math.round(v.getTranslationX());
            dy += v.getTop() + Math.round(v.getTranslationY());
            ViewParent p = v.getParent();
            if (!(p instanceof View)) break;
            v = (View) p;
        }
        out.set(dx, dy, dx + card.getWidth(), dy + card.getHeight());
    }


    // ---- weather ----------------------------------------------------------------------------------

    /** Wire the line under the clock; it stays hidden until the user turns the weather on. */
    private void setupWeather() {
        weatherLine = findViewById(R.id.weatherLine);
        weatherIcon = findViewById(R.id.weatherIcon);
        weatherText = findViewById(R.id.weatherText);
        weatherLine.setOnClickListener(v -> showWeatherPanel());
        if (weather.firstRun()) {
            // First start on this TV: fill the desk in rather than leaving a row that says nothing.
            // The city comes from where the connection is — a guess, but a far better one than a
            // capital picked from the language — and the user can change it in two presses from the
            // weather line, or turn the weather off from the desktop settings.
            weather.markSeen();
            weather.enabled(true);
            locateCityQuietly();
        }
        if (weather.enabled() && weather.hasPlace()) weatherLine.setVisibility(View.VISIBLE);
    }

    /**
     * The city from the connection, without asking. Fails quietly: the TV may be offline on its
     * first start, and a toast about a guess the user never asked for is worse than no weather yet.
     */
    private void locateCityQuietly() {
        executor.execute(() -> {
            Weather.Place p;
            try {
                p = weather.locate();
            } catch (Exception e) {
                p = null;
            }
            if (p == null) p = guessCity();
            Weather.Place place = p;
            handler.post(() -> {
                weather.place(place.name, place.lat, place.lon);
                weatherLine.setVisibility(View.VISIBLE);
                refreshWeather();
                handler.postDelayed(weatherTick, Weather.REFRESH_MS);
            });
        });
    }

    /** Toggle from the desktop settings panel, and keep the row's subtitle honest. */
    private void toggleWeather(TextView status) {
        if (weather.enabled()) {
            weather.enabled(false);
            weatherLine.setVisibility(View.GONE);
            handler.removeCallbacks(weatherTick);
            if (weatherPanel != null) weatherPanel.dismiss();
            status.setText(getString(R.string.weather_state_off));
            toast(getString(R.string.weather_turned_off));
            return;
        }
        weather.enabled(true);
        if (weather.hasPlace()) {
            weatherLine.setVisibility(View.VISIBLE);
            refreshWeather();
            handler.postDelayed(weatherTick, Weather.REFRESH_MS);
            status.setText(weather.placeName());
            toast(getString(R.string.weather_turned_on));
        } else {
            // No place yet: start from the TV's own language, the user can change it in one press.
            Weather.Place guess = guessCity();
            weather.place(guess.name, guess.lat, guess.lon);
            weatherLine.setVisibility(View.VISIBLE);
            status.setText(weather.placeName());
            toast(getString(R.string.weather_turned_on));
            refreshWeather();
            handler.postDelayed(weatherTick, Weather.REFRESH_MS);
        }
    }

    /**
     * A starting city from the TV's language: nothing is sent, nothing is guessed from the network.
     * Chinese, Japanese and Korean get their own capital; everything else starts in London.
     */
    private Weather.Place guessCity() {
        String l = Locale.getDefault().getLanguage();
        if (l == null) l = "en";
        switch (l) {
            case "zh": return new Weather.Place("北京", "中国", 39.9075, 116.39723);
            case "ja": return new Weather.Place("東京", "日本", 35.6895, 139.6917);
            case "ko": return new Weather.Place("서울", "대한민국", 37.5665, 126.9780);
            default:   return new Weather.Place("London", "United Kingdom", 51.5072, -0.1276);
        }
    }

    private void refreshWeather() {
        if (weather == null || !weather.enabled() || !weather.hasPlace()) return;
        executor.execute(() -> {
            Weather.Report r;
            try {
                r = weather.fetch();
            } catch (Exception e) {
                r = null;
            }
            Weather.Report report = r;
            handler.post(() -> {
                if (report == null) return;   // offline: the line keeps the last reading it had
                weatherReport = report;
                showWeatherLine();
                if (weatherPanel != null && weatherPanel.isShowing()) fillWeatherPanel();
            });
        });
    }

    /** The line under the clock: icon, temperature, the words for the sky, then today's range. */
    private void showWeatherLine() {
        if (weatherReport == null) return;
        int sky = Weather.skyOf(weatherReport.code);
        weatherIcon.setImageResource(skyIcon(sky));
        String line = getString(R.string.weather_line, weather.placeName(),
                weatherReport.temp, getString(weatherSky(sky)));
        // The range is today's, which is the first of the days the report carries. Appended here
        // rather than folded into the string above: the words for the sky sit in the middle of that
        // sentence in some languages and at the end of it in others, and the range belongs after
        // all of it in every one of them.
        if (!weatherReport.days.isEmpty()) {
            Weather.Day today = weatherReport.days.get(0);
            line = line + "  " + today.min + "°~" + today.max + "°";
        }
        weatherText.setText(line);
    }

    private void showWeatherPanel() {
        if (weatherPanel != null && weatherPanel.isShowing()) {
            weatherPanel.dismiss();
            return;
        }
        View panel = getLayoutInflater().inflate(R.layout.popup_weather, root, false);
        Typefaces.apply(panel);
        // The same glass as every other panel: the desk behind, softened, under a tinted sheet.
        // This one used to keep the flat dark background of its own layout, which is what made it
        // read as a black hole in the middle of the wallpaper.
        Glass.capture(root);
        applyGlass(panel);
        panel.findViewById(R.id.btnChangeCity).setOnClickListener(v -> {
            weatherPanel.dismiss();
            pickCity();
        });
        panel.findViewById(R.id.btnLocate).setOnClickListener(v -> {
            weatherPanel.dismiss();
            locateCity();
        });
        weatherPanel = new PopupWindow(panel, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        weatherPanel.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        weatherPanel.setOutsideTouchable(true);
        weatherPanel.setElevation(12 * density);
        // The sheet opens from the top of the screen and reaches down to the bottom of it: the
        // page is tall, and the buttons at its bottom have to stay clear of the bottom edge as
        // well. What does not fit scrolls inside the sheet — nothing is folded away, because a
        // page that loses its hourly strip or its coming days reads as a page that lost its
        // memory.
        int[] at = new int[2];
        weatherLine.getLocationInWindow(at);
        int top = (int) (16 * density);
        int bottom = (int) (20 * density);
        int height = Math.max((int) (200 * density), root.getHeight() - top - bottom);
        weatherPanel.setHeight(height);
        weatherPanel.update();
        dropIn(weatherPanel, panel, weatherLine, Gravity.START, top - at[1]);
        fillWeatherPanel();
        // Focus has to move into the panel: left on the line behind it, the D-pad would steer the desk.
        panel.post(() -> panel.findViewById(R.id.btnChangeCity).requestFocus());
    }

    /** Fill the open panel from the last report, or say why there is nothing to show. */
    private void fillWeatherPanel() {
        if (weatherPanel == null || !weatherPanel.isShowing()) return;
        View panel = weatherPanel.getContentView();
        TextView place = panel.findViewById(R.id.place);
        TextView message = panel.findViewById(R.id.message);
        place.setText(weather.placeName());
        if (weatherReport == null) {
            message.setVisibility(View.VISIBLE);
            message.setText(getString(R.string.weather_offline));
            return;
        }
        message.setVisibility(View.GONE);
        int sky = Weather.skyOf(weatherReport.code);
        ((ImageView) panel.findViewById(R.id.icon)).setImageResource(skyIcon(sky));
        ((TextView) panel.findViewById(R.id.temp)).setText(getString(R.string.weather_degrees, weatherReport.temp));
        ((TextView) panel.findViewById(R.id.sky)).setText(getString(weatherSky(sky)));
        ((TextView) panel.findViewById(R.id.feels))
                .setText(getString(R.string.weather_feels, weatherReport.feels));

        TextView air = panel.findViewById(R.id.air);
        if (weatherReport.aqi >= 0) {
            air.setText(getString(R.string.weather_aqi, weatherReport.aqi, getString(aqiLabel(weatherReport.aqi))));
            air.setBackgroundColor(aqiColour(weatherReport.aqi));
            air.setVisibility(View.VISIBLE);
        } else {
            air.setVisibility(View.GONE);
        }

        fillHourly(panel);
        fillDays(panel);

        TextView updated = panel.findViewById(R.id.updated);
        updated.setVisibility(View.VISIBLE);
        updated.setText(getString(R.string.weather_updated,
                android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date())));
    }

    /** The strip of coming hours: the hour, the sky, the temperature, twelve of them side by side. */
    private void fillHourly(View panel) {
        LinearLayout row = panel.findViewById(R.id.hourlyRow);
        row.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (Weather.Hour h : weatherReport.hours) {
            View item = inflater.inflate(R.layout.item_hour, row, false);
            Typefaces.apply(item);
            ((TextView) item.findViewById(R.id.hourLabel)).setText(h.label);
            ((ImageView) item.findViewById(R.id.hourIcon)).setImageResource(skyIcon(Weather.skyOf(h.code)));
            ((TextView) item.findViewById(R.id.hourTemp)).setText(getString(R.string.weather_degrees, h.temp));
            row.addView(item);
        }
    }

    /**
     * The coming days, each with a bar spanning its slice of the whole range — the way Apple draws
     * the ten-day list. The bar is placed inside a fixed-width track: the left edge is where the
     * day's low sits in the range, and the width is the day's spread across it.
     */
    private void fillDays(View panel) {
        LinearLayout days = panel.findViewById(R.id.days);
        days.removeAllViews();
        List<Weather.Day> list = weatherReport.days;
        if (list.isEmpty()) return;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (Weather.Day d : list) {
            lo = Math.min(lo, d.min);
            hi = Math.max(hi, d.max);
        }
        int span = Math.max(1, hi - lo);
        LayoutInflater inflater = getLayoutInflater();
        int trackPx = (int) (150 * density);
        for (int i = 0; i < list.size() && i < 3; i++) {
            Weather.Day d = list.get(i);
            View row = inflater.inflate(R.layout.item_day, days, false);
            Typefaces.apply(row);
            ((TextView) row.findViewById(R.id.dayName)).setText(dayLabel(i));
            ((ImageView) row.findViewById(R.id.dayIcon)).setImageResource(skyIcon(Weather.skyOf(d.code)));
            ((TextView) row.findViewById(R.id.dayLow)).setText(getString(R.string.weather_degrees, d.min));
            ((TextView) row.findViewById(R.id.dayHigh)).setText(getString(R.string.weather_degrees, d.max));
            View bar = row.findViewById(R.id.dayBar);
            FrameLayout.LayoutParams blp = (FrameLayout.LayoutParams) bar.getLayoutParams();
            blp.leftMargin = (int) ((long) trackPx * (d.min - lo) / span);
            blp.width = Math.max((int) (4 * density), (int) ((long) trackPx * (d.max - d.min) / span));
            bar.setLayoutParams(blp);
            days.addView(row);
        }
    }

    private String dayLabel(int i) {
        if (i == 0) return getString(R.string.weather_today);
        if (i == 1) return getString(R.string.weather_tomorrow);
        if (i == 2) return getString(R.string.weather_day_after);
        return weatherReport.days.get(i).label;
    }

    // ---- picking a city ---------------------------------------------------------------------------

    /** Type a name with the TV's own keyboard, then pick from what Open-Meteo knows. */
    private void pickCity() {
        View panel = getLayoutInflater().inflate(R.layout.popup_city, root, false);
        Typefaces.apply(panel);
        // The same glass as every other panel: the desk behind, softened, under a tinted sheet.
        Glass.capture(root);
        applyGlass(panel);
        EditText input = panel.findViewById(R.id.cityInput);
        LinearLayout results = panel.findViewById(R.id.results);
        TextView message = panel.findViewById(R.id.message);
        input.setText(weather.placeName());
        // The list of places can be longer than the screen: it scrolls inside the panel, and the
        // panel itself opens from the top so that everything stays on the screen. The heading and
        // the field above the list need their room as well, so the list is cut to what is left.
        results.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int room = (int) (root.getHeight() - 200 * density);
        int height = Math.min(results.getMeasuredHeight(), room);
        final PopupWindow popup = new PopupWindow(panel, ViewGroup.LayoutParams.WRAP_CONTENT,
                height > 0 ? height : ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(12 * density);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        Runnable search = () -> {
            String typed = input.getText().toString();
            if (typed.trim().isEmpty()) return;
            message.setVisibility(View.VISIBLE);
            message.setText(getString(R.string.weather_searching));
            results.removeAllViews();
            executor.execute(() -> {
                List<Weather.Place> found;
                try {
                    // The geocoder answers in the TV's language, and it takes a name written in any
                    // script: "北京", "Beijing" and "Nanning" all find the same place. When the name
                    // is written in a script the geocoder does not have, it is asked again in English.
                    found = weather.search(typed);
                    if (found.isEmpty()) found = weather.searchIn(typed, "en");
                } catch (Exception e) {
                    found = null;
                }
                List<Weather.Place> places = found;
                handler.post(() -> {
                    message.setVisibility(View.GONE);
                    if (places == null) {
                        toast(getString(R.string.weather_offline));
                    } else if (places.isEmpty()) {
                        message.setVisibility(View.VISIBLE);
                        message.setText(getString(R.string.weather_pick_none, typed.trim()));
                    } else {
                        for (Weather.Place p : places) {
                            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_place, results, false);
                            Typefaces.apply(row);
                            row.setText(p.row());
                            pickPlace(row, p, popup);
                            results.addView(row);
                        }
                        results.getChildAt(0).requestFocus();
                    }
                });
            });
        };
        // The common places, ready to pick: on a TV, scrolling a list beats typing a name.
        LinearLayout commonRow = panel.findViewById(R.id.commonRow);
        commonRow.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (Weather.Place c : weather.common()) {
            TextView chip = (TextView) inflater.inflate(R.layout.item_city, commonRow, false);
            Typefaces.apply(chip);
            chip.setText(c.row());
            pickPlace(chip, c, popup);
            commonRow.addView(chip);
        }

        panel.findViewById(R.id.btnSearch).setOnClickListener(v -> search.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO) {
                search.run();
                return true;
            }
            // Some TV keyboards send the search key as a plain key event instead of an editor action.
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN
                    && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        || event.getKeyCode() == KeyEvent.KEYCODE_SEARCH
                        || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                search.run();
                return true;
            }
            return false;
        });
        int[] at = new int[2];
        weatherLine.getLocationInWindow(at);
        dropIn(popup, panel, weatherLine, Gravity.START, (int) (40 * density) - at[1]);
        input.requestFocus();
    }

    /**
     * A place in the list: picked with the OK key. The key-up is swallowed too, so the key-up of the
     * search press cannot land on the first result and pick it by accident.
     */
    private void pickPlace(View row, Weather.Place place, PopupWindow popup) {
        row.setOnClickListener(v -> {
            weather.place(place.name, place.lat, place.lon);
            popup.dismiss();
            refreshWeather();
            if (weatherPanel != null && weatherPanel.isShowing()) fillWeatherPanel();
            toast(place.name);
        });
        row.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                weather.place(place.name, place.lat, place.lon);
                popup.dismiss();
                refreshWeather();
                if (weatherPanel != null && weatherPanel.isShowing()) fillWeatherPanel();
                toast(place.name);
            }
            return true;
        });
    }

    /** One request to ipinfo.io, resolved through the geocoder so the name is the local one. */
    private void locateCity() {
        toast(getString(R.string.weather_locating));
        executor.execute(() -> {
            Weather.Place p;
            try {
                p = weather.locate();
            } catch (Exception e) {
                p = null;
            }
            Weather.Place place = p;
            handler.post(() -> {
                if (place == null) {
                    toast(getString(R.string.weather_locate_failed));
                    return;
                }
                weather.place(place.name, place.lat, place.lon);
                refreshWeather();
                if (weatherPanel != null && weatherPanel.isShowing()) fillWeatherPanel();
                toast(place.name);
            });
        });
    }

    // ---- weather bits -----------------------------------------------------------------------------

    private static int skyIcon(int sky) {
        switch (sky) {
            case 0: return R.drawable.ic_wx_clear;
            case 1: return R.drawable.ic_wx_cloudy;
            case 2: return R.drawable.ic_wx_fog;
            case 3: return R.drawable.ic_wx_drizzle;
            case 4: return R.drawable.ic_wx_rain;
            case 5: return R.drawable.ic_wx_snow;
            case 6: return R.drawable.ic_wx_showers;
            case 7: return R.drawable.ic_wx_snow;
            default: return R.drawable.ic_wx_thunder;
        }
    }

    private static int weatherSky(int sky) {
        switch (sky) {
            case 0: return R.string.wx_clear;
            case 1: return R.string.wx_cloudy;
            case 2: return R.string.wx_fog;
            case 3: return R.string.wx_drizzle;
            case 4: return R.string.wx_rain;
            case 5: return R.string.wx_snow;
            case 6: return R.string.wx_showers;
            case 7: return R.string.wx_snow_showers;
            default: return R.string.wx_thunder;
        }
    }

    /** The US AQI band, for the words under the number. */
    private static int aqiLabel(int aqi) {
        if (aqi <= 50) return R.string.wx_aqi_good;
        if (aqi <= 100) return R.string.wx_aqi_moderate;
        if (aqi <= 150) return R.string.wx_aqi_sensitive;
        if (aqi <= 200) return R.string.wx_aqi_unhealthy;
        if (aqi <= 300) return R.string.wx_aqi_very_unhealthy;
        return R.string.wx_aqi_hazardous;
    }

    /** The same band as a colour, so the pill reads at a glance. */
    private static int aqiColour(int aqi) {
        if (aqi <= 50) return 0xFF2E9E5B;      // green
        if (aqi <= 100) return 0xFFD9A62E;     // yellow
        if (aqi <= 150) return 0xFFE07A2E;     // orange
        if (aqi <= 200) return 0xFFD64545;     // red
        if (aqi <= 300) return 0xFF8E4B9E;     // purple
        return 0xFF8C2F2F;                     // maroon
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
                if (h != null) {
                    h.itemView.findViewById(R.id.card).requestFocus();
                } else {
                    // The row is still not bound (a very long jump): try once more from the top,
                    // rather than focusing whatever view happens to sit at the top of the screen.
                    rv.post(() -> focusTile(pos));
                }
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    /**
     * The lunar date beside the date. It changes at midnight rather than with the hour, but the
     * minute tick is already running and a string of this size costs nothing to set.
     */
    private void updateLunar() {
        if (lunar == null || lunar.getVisibility() != View.VISIBLE) return;
        lunar.setText(Lunar.text(System.currentTimeMillis()));
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            // getActiveNetwork is Android 6 (23); older releases fall back to the plain label.
            Network net = cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? cm.getActiveNetwork() : null;
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
        Typefaces.apply(panel);
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
            // Some sets (Xiaomi China firmware) need the vendor's own port numbers to switch.
            vendorSourceNumbers = VendorInputs.isChinaFirmware(this)
                    ? VendorInputs.sourceNumbers(inputs) : java.util.Collections.emptyMap();
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
            // An HDMI-CEC device (an Apple TV, a soundbar) gets an input of its own whose parent is the
            // port it sits on, so the port and the device would each get a row ("HDMI 1" and
            // "HDMI Apple TV"). The device takes its port's row instead, and a bare port is listed
            // only when nothing on it speaks CEC.
            java.util.Set<String> inputIds = new java.util.HashSet<>();
            for (TvInputInfo info : inputs) inputIds.add(info.getId());
            java.util.Map<String, List<TvInputInfo>> cecDevices = new java.util.HashMap<>();
            for (TvInputInfo info : inputs) {
                String parent = info.getParentId();
                if (parent == null || !inputIds.contains(parent)) continue;
                if (!info.isPassthroughInput()) continue;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.isHidden(this)) continue;
                List<TvInputInfo> onPort = cecDevices.get(parent);
                if (onPort == null) cecDevices.put(parent, onPort = new ArrayList<>());
                onPort.add(info);
            }
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (tunerId == null || info.getId().compareTo(tunerId) < 0)) {
                    tunerId = info.getId();
                }
                // Inputs hidden in the TV's own Settings > Inputs stay out of the menu too.
                if (!info.isPassthroughInput()) continue;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.isHidden(this)) continue;
                // A CEC device's row is added with its port, below.
                if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                // The name given to the port in Settings > Inputs ("MacBook Pro") goes before the port.
                CharSequence custom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        ? info.loadCustomLabel(this) : null;
                if (custom != null && custom.length() > 0 && !custom.toString().equals(label)) {
                    label = custom + " \u00B7 " + label;
                }
                final String id = info.getId();
                // Analog AV jacks have no hotplug detection, and some TVs report them as always
                // connected, so AV rows show no state.
                int state = !hdmi || hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                List<TvInputInfo> devices = cecDevices.get(id);
                if (devices != null) {
                    for (TvInputInfo device : devices) {
                        CharSequence name = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                ? device.loadCustomLabel(this) : null;
                        String deviceLabel = name == null || name.length() == 0
                                ? label : name + " \u00B7 " + label;
                        final String deviceId = device.getId();
                        int deviceState = !hdmi || hdmiStateBogus ? -1 : tim.getInputState(deviceId);
                        addInputRow(rows, popup, R.drawable.ic_input, deviceLabel, deviceState,
                                () -> openInput(deviceId));
                    }
                    continue;
                }
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }
        // Coocaa OS (Skyworth / 酷开) registers no inputs with TvInputManager, so the framework
        // lists none and the menu would come up empty. The vendor's own app knows its sources.
        if (rows.getChildCount() == 0 && CoocaaSources.present(this)) {
            for (CoocaaSources.Source s : CoocaaSources.list(this)) {
                int icon = s.name.startsWith("HDMI") ? R.drawable.ic_input
                        : "VOD".equals(s.name) ? R.drawable.ic_livetv : R.drawable.ic_av;
                final CoocaaSources.Source source = s;
                addInputRow(rows, popup, icon, s.label, -1, () -> {
                    if (!CoocaaSources.open(this, source)) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        // Xiaomi China firmware: the analogue TV is opened by the vendor's player, with its own action.
        if (liveTv == null && tunerId != null && VendorInputs.isChinaFirmware(this)) {
            liveTv = VendorInputs.firstResolvable(this, VendorInputs.analogueTvIntents());
        }
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
    PopupWindow glassPanel(View panel, View anchor, int minWidthDp) {
        Glass.capture(root);
        applyGlass(panel);
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
        // The same two lines the Inputs menu sets: a transparent drawable (which draws nothing) is
        // what a popup needs to take touches at all on firmware that is fussy about it, and outside
        // touches then close the panel, one more way out besides Back. On a TV the remote is what
        // opens and drives these panels; the touch path is for a mouse or a touchscreen.
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        return popup;
    }

    /**
     * Makes a panel out of glass: the wallpaper behind it, softened, under a dark sheet.
     *
     * <p>With no wallpaper there is nothing to soften, and the panel keeps the flat dark slab it
     * always was — the same picture on a box that never had a wallpaper.
     */
    void applyGlass(View panel) {
        // The panel is made of the desk behind it, blurred. It only works once the desk has been
        // taken: without a picture the panel keeps the flat sheet its layout gives it, which is what
        // a box with nothing behind it has always had.
        if (Glass.hasDesk()) {
            panel.setBackground(new GlassBackground(panel, 20, density, Glass.DIM, Glass.EDGE));
        }
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor. */
    void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        // Just clear of the anchor, not a gap: the top bar sits high on the screen and 10dp read as
        // a panel floating free of the button it belongs to.
        dropIn(popup, panel, anchor, gravity, (int) (4 * density));
    }

    /** The same, but hanging from a different height: a negative offset lifts the panel up. */
    void dropIn(PopupWindow popup, View panel, View anchor, int gravity, int offsetY) {
        if (reducedMotion) {
            // Weak boxes: the unfold costs frames the user notices as lag, so just put it on screen.
            popup.showAsDropDown(anchor, 0, offsetY, gravity);
            return;
        }
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, offsetY, gravity);
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
        Typefaces.apply(row);
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

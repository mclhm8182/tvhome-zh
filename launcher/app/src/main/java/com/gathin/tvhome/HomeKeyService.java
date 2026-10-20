package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Some remotes — Xiaomi boxes and TVs especially — send KEYCODE_ALL_APPS for the Home button instead
 * of KEYCODE_HOME. Android TV routes that key only to the system-image launcher, so once the stock
 * launcher is replaced by this one, the button does nothing. This service catches that key and
 * re-issues it as a HOME intent for this launcher: when the desktop is already in front it lands in
 * MainActivity.onNewIntent and scrolls back to the top, which is the "Home returns to top" behaviour.
 *
 * <p>The same service gives the remote's own Settings (选项) button somewhere to go. The stock launcher
 * used to handle it; with the stock launcher gone the button does nothing, so it opens
 * {@link QuickSettingsActivity} — a small glass panel over whatever is on screen. Boxes that report
 * that button under a raw scan code instead of KEYCODE_SETTINGS can list it in
 * {@code res/values/config.xml} (settings_key_scancodes); the default list is empty, so nothing is
 * hijacked until the user asks for it. Every other key passes straight through, untouched.
 *
 * Off by default. It only ever runs if the user turns it on in Settings > Accessibility (or over adb):
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 * Declaring it costs nothing until it is enabled — no permission is requested at install.
 */
public class HomeKeyService extends AccessibilityService {

    /** The desktop's own panel writes this; see {@link MainActivity#menuKeyEverywhere()}. */
    static final String PREFS = "keys";
    static final String KEY_MENU_EVERYWHERE = "menu_key_everywhere";

    /** Remote buttons that should open quick settings rather than fall through to the app behind. */
    private int[] settingsScancodes = new int[0];

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        return false;
    }

    /**
     * True when the user asked for the Menu button to open quick settings from other apps too.
     *
     * <p>This is the one trade-off in the whole feature: catching Menu everywhere means an app that
     * uses that button for its own menu loses it. Off by default, so nothing is taken away silently —
     * the desktop's settings panel turns it on explicitly.
     */
    private boolean menuOpensQuickSettings() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_MENU_EVERYWHERE, false);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (isSettingsKey(event)
                || (event.getKeyCode() == KeyEvent.KEYCODE_MENU && menuOpensQuickSettings())) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                startActivity(new Intent(this, QuickSettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(home);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}

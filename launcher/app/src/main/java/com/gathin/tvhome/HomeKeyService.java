package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.UserManager;
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

    /** The service is directBootAware, so Android binds it before the user unlocks. */
    private BroadcastReceiver unlockReceiver;

    /**
     * False while the box is still locked. The user's own settings live in credential-protected storage,
     * which cannot be read before the unlock, so the Menu key does nothing until then.
     */
    private boolean unlocked;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        // The service is directBootAware: Android 11 would otherwise skip a service that is not while the
        // user is still locked at boot, write accessibility_enabled=0 and, on some firmware, never bind it
        // after the unlock. Wait for the unlock before touching the user's settings.
        UserManager users = (UserManager) getSystemService(Context.USER_SERVICE);
        // Direct boot is an Android 7 (24) idea: before it a device that has booted is unlocked,
        // so there is nothing to wait for and isUserUnlocked() does not exist.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || (users != null && users.isUserUnlocked())) {
            unlocked = true;
            return;
        }
        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopWaitingForUnlock();
                unlocked = true;
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(unlockReceiver, filter);
        }
        // The unlock may have happened between the check and the registration.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || (users != null && users.isUserUnlocked())) {
            stopWaitingForUnlock();
            unlocked = true;
        }
    }

    private void stopWaitingForUnlock() {
        if (unlockReceiver == null) return;
        try {
            unregisterReceiver(unlockReceiver);
        } catch (IllegalArgumentException ignored) {
            // already gone
        }
        unlockReceiver = null;
    }

    @Override
    public void onDestroy() {
        stopWaitingForUnlock();
        super.onDestroy();
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
        // Credential-protected storage cannot be read before the unlock; the Menu key falls through then.
        if (!unlocked) return false;
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_MENU_EVERYWHERE, false);
    }

    /** The package of the window that came to front last; decides whether Home is "ours" to handle. */
    private String foreground = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null) {
            foreground = event.getPackageName().toString();
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            // The system swallows Home when the launcher is already in front — it re-sends the same
            // intent and the task manager drops it as a duplicate, so the desk never hears about it.
            // Here it does hear, and the desk can go back to the top. From another app the system has
            // to do its job, so the key is passed straight through.
            if (!getPackageName().equals(foreground)) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                MainActivity.onHomeKeyFromService();
            }
            return true;
        }
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
    public void onInterrupt() {
    }
}

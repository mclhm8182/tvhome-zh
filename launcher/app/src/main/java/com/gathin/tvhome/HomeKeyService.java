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
 * Every other key passes straight through, untouched.
 *
 * Off by default. It only ever runs if the user turns it on in Settings > Accessibility (or over adb):
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
 *   settings put secure accessibility_enabled 1
 * Declaring it costs nothing until it is enabled — no permission is requested at install.
 */
public class HomeKeyService extends AccessibilityService {

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
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

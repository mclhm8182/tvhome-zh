package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Answers the system's input-switcher request (com.android.tv.action.VIEW_INPUTS), which is what
 * the remote's source button turns into once the stock launcher is gone.
 *
 * <p>This is a separate, invisible activity rather than the desktop itself: a launcher that
 * answers the request is a candidate for it, and a TV that ships its own source picker would then
 * show a chooser every time the button is pressed. The component is declared but disabled, and
 * {@link MainActivity#applyInputKeyHandling()} switches it on only when the user asks for this
 * launcher to take the button over.
 *
 * <p>It shows nothing: it forwards to the desktop, which opens its Inputs menu.
 */
public final class InputsRequestActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // No display: the work happens in the desktop, which is already running behind us.
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_SHOW_INPUTS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (Exception e) {
            // The desktop could not be brought forward; there is nothing to show either way.
        }
        finish();
    }
}

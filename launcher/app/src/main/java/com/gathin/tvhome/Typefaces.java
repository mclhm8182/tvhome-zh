package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The launcher's one typeface, loaded from assets rather than res/font.
 *
 * <p>res/font only works from Android 8 (API 26) on: the {@code android:fontFamily} attribute
 * crashes the inflater on anything older, because the framework of those releases reads it as a
 * family name and a font resource is not one. Loading from assets and applying it by hand works on
 * every release the launcher supports, which is what lets minSdk stay at 21.
 *
 * <p>The weight the layout asked for is kept where the platform can do it: the XML still carries
 * {@code android:fontVariationSettings}, which older releases simply never look at. On 8 and up the
 * setting is read back off the view and re-applied once the typeface is in place, so a text that
 * asks for weight 500 gets weight 500 and not the file's default. The file is a variable font, so
 * those weights are real; it carries Latin only, and anything else falls back to the system font
 * the way it always did.
 */
final class Typefaces {

    private Typefaces() {
    }

    private static volatile Typeface cached;

    /** The launcher's typeface, loaded once. */
    static Typeface get(Context context) {
        Typeface local = cached;
        if (local == null) {
            synchronized (Typefaces.class) {
                local = cached;
                if (local == null) {
                    local = Typeface.createFromAsset(context.getAssets(), "arimo.ttf");
                    cached = local;
                }
            }
        }
        return local;
    }

    /**
     * Applies the typeface to every text view under {@code root}, weights included.
     *
     * <p>Call once after a layout is inflated. Walking the tree costs a few dozen calls on a screen
     * with a hundred views, and only ever runs at inflation time, never per frame.
     */
    static void apply(View root) {
        if (root instanceof TextView) {
            applyTo((TextView) root);
        } else if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
        }
    }

    private static void applyTo(TextView view) {
        String weight = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            weight = view.getFontVariationSettings();
        }
        view.setTypeface(get(view.getContext()));
        if (weight != null && !weight.isEmpty()
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.setFontVariationSettings(weight);
        }
    }
}

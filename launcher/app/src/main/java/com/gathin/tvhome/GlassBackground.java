package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * The background of a panel that is made of glass.
 *
 * <p>Same sheet as {@link Glass} itself, wrapped in a drawable so that a layout can carry it: the
 * panel is a view with this as its background, and the desk behind it shows through, softened past
 * reading. It samples the desk, not the wallpaper, because a panel floats over both.
 */
final class GlassBackground extends Drawable {

    private final View host;
    private final float radius;
    private final int dim;
    private final int edge;
    private final float edgeWidth;
    /** Whether the sheet samples the picture of the desk or the wallpaper behind it. */
    private final boolean fromDesk;
    private final int[] where = new int[2];

    GlassBackground(View host, float radiusDp, float density, int dim, int edge) {
        this(host, radiusDp, density, dim, edge, true);
    }

    /**
     * @param fromDesk sample the desk, or the wallpaper when the sheet floats in another window
     *                 and the desk behind it cannot be photographed
     */
    GlassBackground(View host, float radiusDp, float density, int dim, int edge, boolean fromDesk) {
        this.host = host;
        this.radius = radiusDp * density;
        this.dim = dim;
        this.edge = edge;
        this.edgeWidth = density;
        this.fromDesk = fromDesk;
    }

    @Override
    public void draw(Canvas canvas) {
        // Where this view sits on the screen: the glass samples the desk in screen coordinates, and
        // a panel in a popup window is not at the origin of the one it is drawn on.
        host.getLocationOnScreen(where);
        Glass.draw(canvas, new RectF(getBounds()), radius, dim, edge, edgeWidth,
                where[0], where[1], fromDesk);
    }

    @Override
    public void setAlpha(int alpha) {
        // The sheet is drawn with its own colours; the view's alpha is not used for it.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

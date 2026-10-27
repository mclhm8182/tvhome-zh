package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it.
 *
 * The band is a near-black rounded rectangle, which is what makes it read as a soft lift on the
 * launcher's usual black background. That only works while the background is black: over a picture an
 * opaque slab looks like a hole cut out of the wallpaper. So the band becomes translucent when there
 * is a wallpaper behind it — visually identical on black, and a gentle scrim over a picture, which
 * keeps the dock icons readable without the frame announcing itself.
 */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    /** Extra band below the tiles for their names (always_show_names), in px. */
    private float namesRoom;
    /** Whether a picture is behind the desk, which is the only case where the band can be glass. */
    private boolean overWallpaper;

    /** @param overWallpaper true when a wallpaper is behind the desk, so the band must not be opaque. */
    DockDecoration(int dockSize, float density, boolean overWallpaper) {
        this.dockSize = dockSize;
        this.density = density;
        band.setColor(0xFF141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(0x1FFFFFFF);
        setOverWallpaper(overWallpaper);
    }

    /**
     * Whether the dock's tiles show their names. The band then reaches down over them, so the names
     * sit on the band instead of on the background.
     */
    void setNames(boolean on) {
        float room = on ? 22 * density : 0;
        if (room == namesRoom) return;
        namesRoom = room;
    }

    /**
     * The wallpaper can be added or removed while the launcher is running, so the band has to be
     * switchable without rebuilding the grid. {@link #invalidate()} is a no-op on the decoration;
     * the caller invalidates the RecyclerView.
     */
    void setOverWallpaper(boolean overWallpaper) {
        this.overWallpaper = overWallpaper;
        // Opaque on black (unchanged from upstream); ~72% black over a picture, enough to keep the
        // icons legible on a bright wallpaper without turning the dock into a black brick.
        band.setColor(overWallpaper ? 0xB8141416 : 0xFF141416);
        // A white hairline disappears against a bright picture, so lift it a little there.
        edge.setColor(overWallpaper ? 0x33FFFFFF : 0x1FFFFFFF);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit a little in from
        // its edges: a full 14dp at the sides sliced the rounded ends flat. Keep the sides, with the 1dp
        // edge line, inside the grid; the top and bottom have room. 22dp where there is room for it, so a
        // focused first or last tile still keeps the band's top margin from its rounded end.
        float sidePad = 22 * density;
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        rect.inset(-side, -pad);
        float radius = 26 * density;
        // The dock's names hang below the tiles; the band reaches down over them.
        rect.bottom += namesRoom;
        // Over a picture the band is glass: the wallpaper behind it, softened, under a dark sheet.
        // On the plain black desk it stays the flat slab it always was — there is nothing behind it
        // to be glass about, and a slab drawn over nothing reads as a grey rectangle.
        if (overWallpaper && Glass.hasWallpaper()) {
            // The canvas is the grid's own, so the grid's place on the screen is what says which
            // part of the wallpaper belongs behind the band.
            int[] where = new int[2];
            parent.getLocationOnScreen(where);
            Glass.draw(c, rect, radius, Glass.DIM, Glass.EDGE, density,
                    where[0], where[1], false);
        } else {
            c.drawRoundRect(rect, radius, radius, band);
        }
        c.drawRoundRect(rect, radius, radius, edge);
    }
}

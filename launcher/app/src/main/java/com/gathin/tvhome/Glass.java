package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The glass the dock band and the menus are made of.
 *
 * <p>A blur of whatever is behind a panel would mean a render pass and a frame of latency on every
 * panel, on a box with a weak GPU. Instead the desk is drawn once into a small picture when a panel
 * opens, that picture is blurred properly, and the panel samples it at its own place on the screen.
 * What comes out is the desk, softened past reading, behind the panel.
 *
 * <p>Two pictures are kept, because they are not the same thing. The dock band is drawn by the grid
 * it decorates, so it cannot take a picture of the screen — it would be in its own picture. It
 * samples the wallpaper, which is the only thing really behind it. Panels float above everything and
 * sample the desk, wallpaper included.
 *
 * <p>With no wallpaper there is nothing behind the dock band, and the band keeps the flat slab it
 * always was, so a box that never had a wallpaper looks exactly as it did.
 */
final class Glass {

    /** How wide the small picture is. It is only ever seen through a panel, blurry. */
    private static final int SMALL_WIDTH = 240;
    /** How far the desk is reduced before it is sampled: enough that no letter survives. */
    private static final int DESK_REDUCE = 3;
    /** How much the wallpaper is reduced before it is blurred. */
    private static final int REDUCE = 8;
    /** How far the blur reaches, in the small picture. */
    private static final int BLUR_RADIUS = 5;
    /** How many times the blur runs: three box passes sit very close to a true Gaussian. */
    private static final int BLUR_PASSES = 3;

    private static Bitmap wall;
    private static Bitmap desk;
    private static BitmapShader wallShader;
    private static BitmapShader deskShader;
    private static int screenW;
    private static int screenH;
    private static final Matrix matrix = new Matrix();
    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Glass() {
    }

    /**
     * Builds the blurred picture of a wallpaper. Passing null removes it: the dock band goes back to
     * being a flat slab, and the panels go back to the flat sheet they were.
     */
    static void setWallpaper(Bitmap wallpaper, int screenWidth, int screenHeight) {
        if (wall != null) {
            wall.recycle();
            wall = null;
        }
        wallShader = null;
        screenW = screenWidth;
        screenH = screenHeight;
        if (wallpaper == null || wallpaper.getWidth() <= 0 || wallpaper.getHeight() <= 0) return;

        int w = Math.max(1, Math.min(SMALL_WIDTH, screenWidth / REDUCE));
        int h = Math.max(1, Math.round(w * (float) screenHeight / Math.max(1, screenWidth)));
        Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(small);
        // The same framing the wallpaper view uses: cover the screen, cropping the long side.
        float scale = Math.max(w / (float) wallpaper.getWidth(), h / (float) wallpaper.getHeight());
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate((w - wallpaper.getWidth() * scale) / 2f,
                (h - wallpaper.getHeight() * scale) / 2f);
        c.drawBitmap(wallpaper, m, paint);
        blur(small);
        wall = small;
        wallShader = new BitmapShader(wall, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    /** Whether the dock band has a picture behind it to be glass about. */
    static boolean hasWallpaper() {
        return wallShader != null;
    }

    /**
     * Takes a picture of the desk, for the panels that float over it.
     *
     * <p>Called just before a panel opens, so what is behind it is what the user is looking at. The
     * desk is drawn at a third of its size first and only then reduced further, or the letters of the
     * app names would be sampled rather than averaged and survive as speckle.
     */
    static void capture(View root) {
        if (desk != null) {
            desk.recycle();
            desk = null;
        }
        deskShader = null;
        int rw = root.getWidth();
        int rh = root.getHeight();
        if (rw <= 0 || rh <= 0) return;

        int mw = Math.max(1, Math.min(SMALL_WIDTH * DESK_REDUCE, rw));
        int mh = Math.max(1, Math.round(mw * (float) rh / rw));
        Bitmap mid = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(mid);
        c.scale(mw / (float) rw, mh / (float) rh);
        root.draw(c);

        int w = Math.max(1, Math.min(SMALL_WIDTH, mw));
        int h = Math.max(1, Math.round(w * (float) mh / mw));
        // Scaling with the filter on averages the pixels it drops, which is the first half of the
        // blur; the passes below finish it.
        Bitmap small = Bitmap.createScaledBitmap(mid, w, h, true);
        mid.recycle();
        blur(small);
        desk = small;
        deskShader = new BitmapShader(desk, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        screenW = rw;
        screenH = rh;
    }

    /** Whether there is a picture of the desk for the panels to sample. */
    static boolean hasDesk() {
        return deskShader != null;
    }

    /**
     * Draws a sheet of glass.
     *
     * <p>The picture is sampled in screen coordinates, so the canvas's own origin has to be said: a
     * panel in a popup window and the dock band in the grid are both drawn in coordinates of their
     * own, and the picture behind them has to line up with what is really on the screen.
     *
     * @param dim how much black goes over the picture, so the writing on top stays readable
     * @param edge the hairline round the sheet, or 0 for none
     * @param edgeWidth how thick that hairline is, in px
     * @param fromDesk sample the desk, not the wallpaper
     */
    static void draw(Canvas canvas, RectF rect, float radius, int dim, int edge, float edgeWidth,
                     float originX, float originY, boolean fromDesk) {
        BitmapShader use = fromDesk ? deskShader : wallShader;
        Bitmap source = fromDesk ? desk : wall;
        if (use != null && source != null) {
            matrix.setScale(screenW / (float) source.getWidth(), screenH / (float) source.getHeight());
            matrix.postTranslate(-originX, -originY);
            use.setLocalMatrix(matrix);
            paint.setShader(use);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
        }
        tint.setColor(dim);
        canvas.drawRoundRect(rect, radius, radius, tint);
        if (edge != 0) {
            tint.setStyle(Paint.Style.STROKE);
            tint.setStrokeWidth(edgeWidth);
            tint.setColor(edge);
            canvas.drawRoundRect(rect, radius, radius, tint);
            tint.setStyle(Paint.Style.FILL);
        }
    }

    /**
     * Blurs a small picture in place: a box blur, several times over, which is the usual way to get
     * a Gaussian without paying for one. Three passes at this radius leave straight edges soft and
     * letters gone.
     */
    private static void blur(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w < 3 || h < 3) return;
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        int[] tmp = new int[px.length];
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            blurRows(px, tmp, w, h);
            blurCols(tmp, px, w, h);
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void blurRows(int[] src, int[] dst, int w, int h) {
        for (int y = 0; y < h; y++) {
            int row = y * w;
            long r = 0, g = 0, b = 0;
            int n = 0;
            for (int x = -BLUR_RADIUS; x <= BLUR_RADIUS; x++) {
                int p = src[row + clamp(x, 0, w - 1)];
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
                n++;
            }
            for (int x = 0; x < w; x++) {
                dst[row + x] = 0xFF000000 | (int) ((r / n) << 16) | (int) ((g / n) << 8) | (int) (b / n);
                int out = src[row + clamp(x - BLUR_RADIUS, 0, w - 1)];
                int in = src[row + clamp(x + BLUR_RADIUS + 1, 0, w - 1)];
                r += (in & 0xFF) - (out & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                b += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
            }
        }
    }

    private static void blurCols(int[] src, int[] dst, int w, int h) {
        for (int x = 0; x < w; x++) {
            long r = 0, g = 0, b = 0;
            int n = 0;
            for (int y = -BLUR_RADIUS; y <= BLUR_RADIUS; y++) {
                int p = src[clamp(y, 0, h - 1) * w + x];
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
                n++;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = 0xFF000000 | (int) ((r / n) << 16) | (int) ((g / n) << 8) | (int) (b / n);
                int out = src[clamp(y - BLUR_RADIUS, 0, h - 1) * w + x];
                int in = src[clamp(y + BLUR_RADIUS + 1, 0, h - 1) * w + x];
                r += (in & 0xFF) - (out & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                b += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}

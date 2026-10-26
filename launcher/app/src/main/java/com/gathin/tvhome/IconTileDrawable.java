package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The face of a tile for an app that brings no TV artwork of its own.
 *
 * <p>Two styles, because they answer to different things:
 *
 * <p><b>Spread</b> keeps the app's own picture: the icon is scaled up until it fills the tile's
 * height, and the space left on either side is filled with the icon's own outermost column of
 * pixels, stretched sideways. Every row keeps its colour, so an icon whose edge runs from one
 * colour to another carries that run across the tile instead of one flat colour averaged over all
 * of it. The strip IS the edge, so there is nothing to match up and no seam to see.
 *
 * <p><b>Unified</b> answers to the fact that a desk full of app icons is a desk full of different
 * shapes and sizes: a small logo in the middle of a big slab, a wide one, a tall one, each in its
 * own colour. It takes the mark out of the icon, sizes it by area so every app's mark occupies the
 * same share of the tile, whitens it when it is one colour, and lays it on a diagonal wash of the
 * app's own colour. The result reads as a set — which is what a row of tiles on a TV is for — while
 * still telling the apps apart by colour and mark.
 */
final class IconTileDrawable extends Drawable {

    /** A neutral wash for an icon with no colour of its own. */
    private static final int PLAIN = 0xFF2B2B30;
    /** How tall the render the edge columns and the mark are cut from is. */
    private static final int SAMPLE = 96;
    /** How far a pixel may sit from the background colour and still count as background. */
    private static final int BG_TOLERANCE = 46;
    /** How far the mark is grown before its pieces are joined up. */
    private static final int DILATE = 2;
    /** How close to the mark's own average a pixel must be for it to count as one colour. */
    private static final int MONO_TOLERANCE = 64;

    /** The app's icon, on a copy of its own so that nothing else can move it. */
    private final Drawable icon;
    private final boolean unified;

    // ---- spread style ----
    private final Bitmap leftEdge;
    private final Bitmap rightEdge;
    private final int leftColour;
    private final int rightColour;

    // ---- unified style ----
    /** The mark, cut out of the icon, already whitened when it is one colour. */
    private final Bitmap mark;
    /** The mark's box inside {@link #mark}. */
    private final int markX;
    private final int markY;
    private final int markW;
    private final int markH;
    /** The app's own colour, the wash the mark sits on. */
    private final int washColour;

    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint markPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private Shader shader;
    private int shaderKey;

    private IconTileDrawable(Drawable icon, boolean unified,
                             Bitmap leftEdge, Bitmap rightEdge, int leftColour, int rightColour,
                             Bitmap mark, int markX, int markY, int markW, int markH, int washColour) {
        this.icon = icon;
        this.unified = unified;
        this.leftEdge = leftEdge;
        this.rightEdge = rightEdge;
        this.leftColour = leftColour;
        this.rightColour = rightColour;
        this.mark = mark;
        this.markX = markX;
        this.markY = markY;
        this.markW = markW;
        this.markH = markH;
        this.washColour = washColour;
    }

    /**
     * Builds the face for one app. The icon is copied first: the drawable the package manager
     * handed out may be shared with other views, and setting bounds on it would move those too.
     *
     * @param unified true for the mark-on-a-colour-wash style, false for the stretched edge
     */
    static IconTileDrawable of(Drawable source, boolean unified) {
        Drawable copy = source.getConstantState() != null
                ? source.getConstantState().newDrawable().mutate() : source;
        int iw = copy.getIntrinsicWidth();
        int ih = copy.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            return new IconTileDrawable(copy, unified, null, null, PLAIN, PLAIN,
                    null, 0, 0, 0, 0, PLAIN);
        }
        int n = SAMPLE;
        int w = iw >= ih ? n : Math.max(1, Math.round(n * (float) iw / ih));
        int h = ih >= iw ? n : Math.max(1, Math.round(n * (float) ih / iw));
        Bitmap render = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(render);
        Rect saved = new Rect(copy.getBounds());
        copy.setBounds(0, 0, w, h);
        copy.draw(c);
        copy.setBounds(saved);

        if (unified) {
            int bg = bodyColour(render);
            Mark cut = cutMark(render, bg);
            IconTileDrawable d = new IconTileDrawable(copy, true, null, null, 0, 0,
                    cut == null ? null : cut.bmp,
                    cut == null ? 0 : cut.x, cut == null ? 0 : cut.y,
                    cut == null ? 0 : cut.w, cut == null ? 0 : cut.h, bg);
            render.recycle();
            return d;
        }
        int leftX = -1;
        int rightX = -1;
        for (int x = 0; x < w; x++) {
            if (columnHasInk(render, x, h)) { leftX = x; break; }
        }
        for (int x = w - 1; x >= 0; x--) {
            if (columnHasInk(render, x, h)) { rightX = x; break; }
        }
        if (leftX < 0 || rightX < 0) {
            render.recycle();
            return new IconTileDrawable(copy, false, null, null, PLAIN, PLAIN,
                    null, 0, 0, 0, 0, PLAIN);
        }
        // The two edge columns, cut out and kept. They are one pixel wide: stretching one sideways
        // is what fills the tile, and it costs a few hundred bytes an app.
        Bitmap left = Bitmap.createBitmap(render, leftX, 0, 1, h);
        Bitmap right = Bitmap.createBitmap(render, rightX, 0, 1, h);
        render.recycle();
        return new IconTileDrawable(copy, false, left, right,
                columnColour(left), columnColour(right), null, 0, 0, 0, 0, 0);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        if (unified) {
            drawUnified(canvas, b);
            return;
        }
        int iw = icon.getIntrinsicWidth();
        int ih = icon.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) return;
        // As big as the tile allows, never stretched: the icon keeps its shape and touches the
        // top and bottom edges when it is the tall one.
        float s = Math.min(b.height() / (float) ih, b.width() / (float) iw);
        float dw = iw * s;
        float dh = ih * s;
        float iconLeft = b.left + (b.width() - dw) / 2f;
        float iconRight = iconLeft + dw;
        float top = b.top + (b.height() - dh) / 2f;

        // The wash runs behind everything: from the icon's left edge colour to its right edge
        // colour, so the band behind the icon — the parts of it that are see-through — is the
        // icon's own colours rather than the card's.
        float t0 = Math.max(0f, Math.min(1f, (iconLeft - b.left) / (float) b.width()));
        float t1 = Math.max(0f, Math.min(1f, (iconRight - b.left) / (float) b.width()));
        int key = b.width() * 31 + Math.round(t0 * 1000f);
        if (shader == null || shaderKey != key) {
            shader = new LinearGradient(b.left, 0, b.right, 0,
                    new int[]{leftColour, leftColour, rightColour, rightColour},
                    new float[]{0f, t0, t1, 1f}, Shader.TileMode.CLAMP);
            shaderKey = key;
        }
        wash.setShader(shader);
        canvas.drawRect(b, wash);

        // The two edges themselves: the icon's own outermost column of pixels, stretched sideways
        // until it reaches the tile's edge. Every row keeps its colour, so there is nothing to
        // match up and no seam to see.
        if (leftEdge != null && iconLeft - b.left > 0.5f) {
            canvas.drawBitmap(leftEdge, null,
                    new RectF(b.left, b.top, iconLeft, b.bottom), edge);
        }
        if (rightEdge != null && b.right - iconRight > 0.5f) {
            canvas.drawBitmap(rightEdge, null,
                    new RectF(iconRight, b.top, b.right, b.bottom), edge);
        }

        icon.setBounds(Math.round(iconLeft), Math.round(top),
                Math.round(iconRight), Math.round(top + dh));
        icon.draw(canvas);
    }

    /**
     * The unified style: a diagonal wash of the app's own colour with the mark laid on it, sized by
     * area so that every app's mark takes the same share of the tile.
     */
    private void drawUnified(Canvas canvas, Rect b) {
        // A diagonal wash, not a flat fill: the app's colour, a shade lighter towards one corner.
        int key = b.width() * 31 + b.height();
        if (shader == null || shaderKey != key) {
            shader = new LinearGradient(b.left, b.top, b.right, b.bottom,
                    new int[]{lift(washColour, 1.22f), shade(washColour, 0.82f)},
                    null, Shader.TileMode.CLAMP);
            shaderKey = key;
        }
        wash.setShader(shader);
        canvas.drawRect(b, wash);
        if (mark == null || markW <= 0 || markH <= 0) {
            // No mark of its own: the icon itself, scaled to fit and centred, over the wash.
            int iw = icon.getIntrinsicWidth();
            int ih = icon.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) return;
            float s = Math.min(0.86f * b.height() / ih, 0.86f * b.width() / iw);
            float dw = iw * s;
            float dh = ih * s;
            float left = b.left + (b.width() - dw) / 2f;
            float top = b.top + (b.height() - dh) / 2f;
            icon.setBounds(Math.round(left), Math.round(top),
                    Math.round(left + dw), Math.round(top + dh));
            icon.draw(canvas);
            return;
        }

        // Size by area: a square whose side is half the tile's height has the same area as the
        // mark's box, so a square mark lands at half the height and a wide one at less. The caps
        // keep a very wide or very tall mark from touching the tile's edges.
        float target = 0.5f * b.height();
        float scale = target / (float) Math.sqrt(markW * (double) markH);
        float dw = Math.min(markW * scale, 0.62f * b.width());
        float dh = Math.min(markH * scale, 0.60f * b.height());
        // A cap may have bitten: keep the shape rather than stretch to it.
        if (dw / dh > markW / (float) markH) dw = dh * markW / (float) markH;
        else dh = dw * markH / (float) markW;
        float left = b.left + (b.width() - dw) / 2f;
        float top = b.top + (b.height() - dh) / 2f;
        canvas.drawBitmap(mark, new Rect(markX, markY, markX + markW, markY + markH),
                new RectF(left, top, left + dw, top + dh), markPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        wash.setAlpha(alpha);
        edge.setAlpha(alpha);
        markPaint.setAlpha(alpha);
        icon.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        icon.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    // ---- sampling -------------------------------------------------------------------------------

    /** Whether any pixel in the middle of this column is not fully see-through. */
    private static boolean columnHasInk(Bitmap bmp, int x, int height) {
        for (int y = height / 4; y < height - height / 4; y++) {
            if (Color.alpha(bmp.getPixel(x, y)) > 0) return true;
        }
        return false;
    }

    /** The average colour of a one-pixel-wide column, ignoring the see-through rows. */
    private static int columnColour(Bitmap column) {
        long r = 0, g = 0, b = 0, a = 0;
        for (int y = 0; y < column.getHeight(); y++) {
            int p = column.getPixel(0, y);
            int alpha = Color.alpha(p);
            if (alpha == 0) continue;
            r += Color.red(p) * alpha;
            g += Color.green(p) * alpha;
            b += Color.blue(p) * alpha;
            a += alpha;
        }
        if (a == 0) return PLAIN;
        return Color.argb(255, (int) (r / a), (int) (g / a), (int) (b / a));
    }

    /** A mark cut out of an icon: the bitmap to draw and the box inside it that holds the mark. */
    private static final class Mark {
        final Bitmap bmp;
        final int x, y, w, h;

        Mark(Bitmap bmp, int x, int y, int w, int h) {
            this.bmp = bmp;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /**
     * The icon's own colour: the middle of it.
     *
     * <p>The middle, not the edge: an icon that fades towards its edges has a frame that is a
     * different colour from its body, and taking the frame as the background would make the whole
     * fading area count as the mark.
     */
    private static int bodyColour(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int n = 0;
        int[] rs = new int[w * h];
        int[] gs = new int[w * h];
        int[] bs = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) == 0) continue;
                rs[n] = Color.red(p);
                gs[n] = Color.green(p);
                bs[n] = Color.blue(p);
                n++;
            }
        }
        if (n == 0) return PLAIN;
        java.util.Arrays.sort(rs, 0, n);
        java.util.Arrays.sort(gs, 0, n);
        java.util.Arrays.sort(bs, 0, n);
        return Color.rgb(rs[n / 2], gs[n / 2], bs[n / 2]);
    }

    /**
     * Cuts the mark out of the icon: the part of it that stands out from the body.
     *
     * <p>The mark is the biggest connected patch of pixels lighter than the body, which is what an
     * app's logo is in practice: a pale shape on a coloured field. The patch is found on a dilated
     * copy, because a mark drawn in thin strokes — the antennae on a television-shaped logo — breaks
     * into pieces at this size, and the body of the mark is only the biggest of them.
     *
     * <p>Where there is no such patch the icon has no mark of its own and the wash alone is the tile.
     */
    private static Mark cutMark(Bitmap render, int bg) {
        int w = render.getWidth();
        int h = render.getHeight();
        int bgLuma = luma(bg);
        boolean[] mask = new boolean[w * h];
        int marked = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = render.getPixel(x, y);
                if (Color.alpha(p) == 0) continue;
                // Lighter than the body, not merely different from it: an icon that fades towards its
                // edges would otherwise have the whole fade counted as its mark.
                if (luma(p) < bgLuma + 55) continue;
                mask[y * w + x] = true;
                marked++;
            }
        }
        // A mark is a shape, not a speckle of noise: anything under a twentieth of the icon is
        // compression dust rather than a logo.
        if (marked * 20 < w * h) return null;

        // Dilate, so the pieces of a thin-stroked mark belong to one another.
        boolean[] grown = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[y * w + x]) continue;
                for (int dy = -DILATE; dy <= DILATE; dy++) {
                    for (int dx = -DILATE; dx <= DILATE; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) grown[ny * w + nx] = true;
                    }
                }
            }
        }
        int[] box = biggestCluster(grown, w, h);
        if (box == null) return null;

        // The mark is every bright pixel inside the patch, and nothing else: the background around
        // it is cut away, so the mark sits on the wash instead of on a square of the old tile.
        int x0 = box[0];
        int y0 = box[1];
        int x1 = box[2];
        int y1 = box[3];
        int bw = x1 - x0 + 1;
        int bh = y1 - y0 + 1;
        boolean oneColour = isOneColour(render, x0, y0, bw, bh, bgLuma);
        Bitmap out = render.copy(Bitmap.Config.ARGB_8888, true);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int p = out.getPixel(x, y);
                if (Color.alpha(p) == 0) continue;
                boolean isMark = oneColour ? luma(p) >= bgLuma + 55 : !near(p, bg);
                if (!isMark) {
                    out.setPixel(x, y, 0);
                } else if (oneColour) {
                    // Keep the pixel's own coverage so the mark keeps its soft edge; only the
                    // colour goes white.
                    out.setPixel(x, y, Color.argb(Color.alpha(p), 255, 255, 255));
                }
            }
        }
        return new Mark(out, x0, y0, bw, bh);
    }

    /** The box of the biggest connected patch of the mask, or null when there is none. */
    private static int[] biggestCluster(boolean[] mask, int w, int h) {
        int[] label = new int[w * h];
        int[] stack = new int[w * h];
        int best = 0;
        int bestSize = 0;
        int cur = 0;
        for (int i = 0; i < w * h; i++) {
            if (!mask[i] || label[i] != 0) continue;
            cur++;
            int top = 0;
            stack[top++] = i;
            label[i] = cur;
            int size = 0;
            while (top > 0) {
                int p = stack[--top];
                size++;
                int x = p % w;
                int y = p / w;
                if (x > 0 && mask[p - 1] && label[p - 1] == 0) { label[p - 1] = cur; stack[top++] = p - 1; }
                if (x < w - 1 && mask[p + 1] && label[p + 1] == 0) { label[p + 1] = cur; stack[top++] = p + 1; }
                if (y > 0 && mask[p - w] && label[p - w] == 0) { label[p - w] = cur; stack[top++] = p - w; }
                if (y < h - 1 && mask[p + w] && label[p + w] == 0) { label[p + w] = cur; stack[top++] = p + w; }
            }
            if (size > bestSize) { bestSize = size; best = cur; }
        }
        if (best == 0) return null;
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (label[y * w + x] != best) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    /** Whether the mark in this box is one colour, so that whitening it loses nothing. */
    private static boolean isOneColour(Bitmap bmp, int x0, int y0, int w, int h, int bgLuma) {
        long r = 0, g = 0, b = 0, a = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) == 0 || luma(p) < bgLuma + 55) continue;
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                a++;
            }
        }
        if (a == 0) return true;
        int ar = (int) (r / a), ag = (int) (g / a), ab = (int) (b / a);
        int near = 0;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int p = bmp.getPixel(x, y);
                if (Color.alpha(p) == 0 || luma(p) < bgLuma + 55) continue;
                if (Math.abs(Color.red(p) - ar) + Math.abs(Color.green(p) - ag)
                        + Math.abs(Color.blue(p) - ab) < MONO_TOLERANCE) near++;
            }
        }
        return near * 100 >= a * 82;
    }

    /** How bright a colour is, the way the eye weighs it. */
    private static int luma(int colour) {
        return (int) (0.299 * Color.red(colour) + 0.587 * Color.green(colour)
                + 0.114 * Color.blue(colour));
    }

    private static boolean near(int pixel, int colour) {
        return Math.abs(Color.red(pixel) - Color.red(colour))
                + Math.abs(Color.green(pixel) - Color.green(colour))
                + Math.abs(Color.blue(pixel) - Color.blue(colour)) < BG_TOLERANCE;
    }

    private static int average(long r, long g, long b, long a) {
        if (a == 0) return PLAIN;
        return Color.argb(255, (int) (r / a), (int) (g / a), (int) (b / a));
    }

    /** The same colour, multiplied up or down. */
    private static int lift(int colour, float factor) {
        return Color.rgb(
                Math.min(255, Math.round(Color.red(colour) * factor)),
                Math.min(255, Math.round(Color.green(colour) * factor)),
                Math.min(255, Math.round(Color.blue(colour) * factor)));
    }

    private static int shade(int colour, float factor) {
        return Color.rgb(
                Math.round(Color.red(colour) * factor),
                Math.round(Color.green(colour) * factor),
                Math.round(Color.blue(colour) * factor));
    }
}

package de.eclipseiptv.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Der Hintergrund der App: schwarz mit rotem Mondlicht aus der oberen rechten Ecke.
 *
 * Beide Verlaeufe werden einmal in ein kleines Bild gerechnet (halbe Aufloesung,
 * weichgezeichnet hochskaliert). Danach kostet der Hintergrund pro Bild nur noch einen
 * einzigen, einfachen Zeichenschritt – wichtig, weil die treibenden Wolken laufend neue
 * Bilder ausloesen und schwache TV-Boxen jeden Zeichenschritt spueren.
 */
public final class Backdrop extends Drawable {
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private Bitmap bitmap;
    private final Rect src = new Rect();

    @Override
    protected void onBoundsChange(Rect b) {
        int w = Math.max(1, b.width() / 2), h = Math.max(1, b.height() / 2);
        if (bitmap != null && bitmap.getWidth() == w && bitmap.getHeight() == h) return;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFF0C070A, Ui.BG, 0xFF040304}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        float r = Ui.px(540) / 2f;
        p.setShader(new RadialGradient(w * 0.92f, -h * 0.1f, r, new int[]{0x4DC20E26, 0x187E0A1B, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        bitmap = bmp;
        src.set(0, 0, w, h);
    }

    @Override
    public void draw(Canvas c) {
        if (bitmap != null) c.drawBitmap(bitmap, src, getBounds(), paint);
    }

    @Override
    public void setAlpha(int a) {
    }

    @Override
    public void setColorFilter(ColorFilter f) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}

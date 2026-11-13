package de.eclipseiptv.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Wolkenbaender als Hintergrundschmuck: gestapelte, abgerundete Streifen in dunklem Rot
 * mit duenner weisser Kontur, angelehnt an die Dunstbaender (Kasumi) japanischer Malerei.
 * Bewusst zurueckhaltend, damit Listen und Texte davor gut lesbar bleiben.
 */
public final class Clouds extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public Clouds() {
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
    }

    /** Streifen einer Wolke: x, y, Breite in Einheiten der Streifenhoehe. */
    private static final float[][] BIG = {{2.2f, 0f, 6.5f}, {0f, 0.82f, 10f}, {3.4f, 1.64f, 8.6f}, {1.4f, 2.46f, 5.2f}};
    private static final float[][] SMALL = {{1.2f, 0f, 4.2f}, {0f, 0.82f, 6.4f}, {2.1f, 1.64f, 3.6f}};

    private void cloud(float[][] bands, float x, float y, float unit) {
        Path part = new Path();
        RectF r = new RectF();
        for (float[] b : bands) {
            part.reset();
            r.set(x + b[0] * unit, y + b[1] * unit, x + (b[0] + b[2]) * unit, y + (b[1] + 1f) * unit);
            part.addRoundRect(r, unit / 2f, unit / 2f, Path.Direction.CW);
            path.op(part, Path.Op.UNION);
        }
    }

    @Override
    protected void onBoundsChange(Rect b) {
        path.reset();
        float w = b.width(), h = b.height(), u = h / 22f;
        cloud(BIG, b.left + w * 0.73f, b.top + h * 0.035f, u);
        cloud(SMALL, b.left + w * 0.915f, b.top + h * 0.235f, u * 0.78f);
        cloud(SMALL, b.left + w * 0.52f, b.top + h * 0.90f, u * 0.9f);
        cloud(BIG, b.left + w * 0.80f, b.top + h * 0.80f, u * 0.85f);
        line.setStrokeWidth(Math.max(1f, h / 540f * 0.9f));
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        fill.setColor(0x30A80C22);
        line.setColor(0x3DFFFFFF);
        c.drawPath(path, fill);
        c.drawPath(path, line);
    }

    @Override
    public void setAlpha(int a) {
    }

    @Override
    public void setColorFilter(ColorFilter f) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

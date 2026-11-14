package de.eclipseiptv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

import de.eclipseiptv.data.Bg;

/**
 * Hintergrund-Motive als Alternative zu den Wolken: Regenstadt, Neon-Horizont, Regen,
 * Linien-Wolken und Dunstbaender. Jedes Motiv wird einmal im Hintergrund als Bild
 * berechnet und liegt dann ruhig ueber dem Rauch.
 */
public final class Scenery extends View {
    public static final String CITY = "city", GRID = "grid", RAIN = "rain", LINES = "lines", HAZE = "haze";

    private final String kind;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;

    public Scenery(Context c, String kind) {
        super(c);
        this.kind = kind;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(final int w, final int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = render(kind, w, h);
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        bitmap = b;
                        invalidate();
                    }
                });
            }
        });
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bitmap != null) c.drawBitmap(bitmap, 0, 0, paint);
    }

    static Bitmap render(String kind, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float u = h / 540f;
        Random r = new Random(11);
        switch (kind) {
            case CITY:
                city(c, w, h, u, r);
                rain(c, w, h, u, r, 0.10f, 520);
                break;
            case GRID:
                grid(c, w, h, u, r);
                break;
            case RAIN:
                mist(c, w, h, u);
                rain(c, w, h, u, r, 0.16f, 900);
                break;
            case LINES:
                lineClouds(c, w, h, u);
                break;
            case HAZE:
                haze(c, w, h, u, r);
                break;
            default:
        }
        return b;
    }

    // ---- Regenstadt ------------------------------------------------------------

    private static void city(Canvas c, int w, int h, float u, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Roter Dunst hinter der Stadt
        p.setShader(new LinearGradient(0, h * 0.45f, 0, h, 0x00000000, 0x55400812, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.45f, w, h, p);
        p.setShader(null);
        skyline(c, w, h, u, r, h * 0.50f, h * 0.30f, 0xFF1C0A10, 0xFF2A0C16, 0.25f, 40);
        // Nebel zwischen den Ebenen
        p.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00070507, 0x99120609, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.55f, w, h, p);
        p.setShader(null);
        skyline(c, w, h, u, r, h * 0.62f, h * 0.24f, 0xFF0D0609, 0xFF3A0D18, 0.45f, 22);
    }

    /** Eine Reihe Tuerme: Grundlinie unten, Hoehe ab top. lit = Anteil beleuchteter Fenster. */
    private static void skyline(Canvas c, int w, int h, float u, Random r, float top, float var, int body, int edge, float lit, int count) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint win = new Paint();
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, 1.2f * u));
        float x = -20 * u;
        while (x < w) {
            float tw = (26 + r.nextFloat() * 54) * u;
            float th = top + r.nextFloat() * var;
            p.setShader(new LinearGradient(x, 0, x + tw, 0, edge, body, Shader.TileMode.CLAMP));
            c.drawRect(x, th, x + tw, h, p);
            p.setShader(null);
            // Antenne oder Spitze
            if (r.nextFloat() < 0.35f) {
                line.setColor(edge);
                float ax = x + tw * (0.3f + r.nextFloat() * 0.4f);
                c.drawLine(ax, th, ax, th - (18 + r.nextFloat() * 40) * u, line);
                win.setColor(0xFFDD1A34);
                c.drawCircle(ax, th - (18 + r.nextFloat() * 4) * u, 1.6f * u, win);
            }
            // Rohre zum Nachbarn
            if (r.nextFloat() < 0.3f) {
                p.setColor(edge);
                float py = th + (20 + r.nextFloat() * 60) * u;
                c.drawRect(x + tw, py, x + tw + 22 * u, py + 3 * u, p);
            }
            // Fenster
            float fy = th + 8 * u;
            while (fy < h - 6 * u) {
                for (float fx = x + 5 * u; fx < x + tw - 6 * u; fx += 7 * u) {
                    if (r.nextFloat() < lit * 0.25f) {
                        win.setColor(r.nextFloat() < 0.7f ? 0xB3FF2B42 : 0x66FFE3E7);
                        c.drawRect(fx, fy, fx + 2.5f * u, fy + 3.5f * u, win);
                    }
                }
                fy += 9 * u;
            }
            x += tw + (r.nextFloat() < 0.25f ? 10 * u : 0);
        }
    }

    // ---- Neon-Horizont ---------------------------------------------------------

    private static void grid(Canvas c, int w, int h, float u, Random r) {
        float horizon = h * 0.64f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Leuchten am Horizont
        p.setShader(new LinearGradient(0, horizon - 90 * u, 0, horizon + 4 * u, 0x00DD1A34, 0x55DD1A34, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon - 90 * u, w, horizon, p);
        p.setShader(null);
        // Ferne Skyline auf dem Horizont
        Random rr = new Random(5);
        float x = 0;
        p.setColor(0xFF140810);
        while (x < w) {
            float tw = (14 + rr.nextFloat() * 30) * u, th = (10 + rr.nextFloat() * 46) * u;
            c.drawRect(x, horizon - th, x + tw, horizon, p);
            x += tw;
        }
        // Boden
        p.setShader(new LinearGradient(0, horizon, 0, h, 0xFF0A0508, 0xFF050304, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, w, h, p);
        p.setShader(null);
        // Horizontlinie
        p.setColor(0xFFFF2B42);
        p.setMaskFilter(new BlurMaskFilter(3 * u, BlurMaskFilter.Blur.NORMAL));
        c.drawRect(0, horizon - 1.5f * u, w, horizon + 1.5f * u, p);
        p.setMaskFilter(null);
        p.setColor(0xFFFFB3BC);
        c.drawRect(0, horizon - 0.6f * u, w, horizon + 0.6f * u, p);
        // Gitter
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setStrokeWidth(Math.max(1f, 1.1f * u));
        float vx = w * 0.55f;
        for (int i = -24; i <= 24; i++) {
            float bx = vx + i * 70 * u;
            g.setShader(new LinearGradient(0, horizon, 0, h, 0x10DD1A34, 0x8CDD1A34, Shader.TileMode.CLAMP));
            c.drawLine(vx + i * 2 * u, horizon, bx * 1f + (bx - vx) * 2.5f, h, g);
        }
        g.setShader(null);
        for (int k = 1; k < 18; k++) {
            float t = k / 18f;
            float y = horizon + (h - horizon) * t * t;
            g.setColor(((int) (25 + 120 * t) << 24) | 0xDD1A34);
            c.drawLine(0, y, w, y, g);
        }
        // Feine Bildzeilen ueber allem
        Paint s = new Paint();
        s.setColor(0x0AFFFFFF);
        for (float y = 0; y < h; y += 3 * u) c.drawRect(0, y, w, y + Math.max(1f, 0.6f * u), s);
    }

    // ---- Regen -----------------------------------------------------------------

    private static void mist(Canvas c, int w, int h, float u) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, h * 0.6f, 0, h, 0x00000000, 0x66300810, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.6f, w, h, p);
    }

    private static void rain(Canvas c, int w, int h, float u, Random r, float alpha, int drops) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        float slant = 0.18f;
        for (int i = 0; i < drops; i++) {
            float x = r.nextFloat() * (w + h * slant) - h * slant, y = r.nextFloat() * h;
            float len = (10 + r.nextFloat() * 26) * u;
            float depth = r.nextFloat();
            p.setStrokeWidth(Math.max(1f, (0.6f + depth * 0.9f) * u));
            int a = (int) (255 * alpha * (0.35f + depth * 0.65f));
            p.setColor((a << 24) | (r.nextFloat() < 0.15f ? 0xFF5C6C : 0xFFFFFF));
            c.drawLine(x, y, x + len * slant, y + len, p);
        }
    }

    // ---- Linien-Wolken ---------------------------------------------------------

    private static void lineClouds(Canvas c, int w, int h, float u) {
        float[][] at = {{0.56f, 0.07f, 64}, {0.80f, 0.40f, 92}, {0.08f, 0.70f, 76}, {0.50f, 0.78f, 110}};
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeJoin(Paint.Join.ROUND);
        s.setStrokeCap(Paint.Cap.ROUND);
        for (float[] k : at) {
            float ch = k[2] * u, x = w * k[0], y = h * k[1];
            Path shape = Clouds.outlinePath(x, y, ch);
            s.setStrokeWidth(Math.max(1.2f, 1.6f * u));
            s.setColor(0x80FFFFFF);
            c.drawPath(shape, s);
            s.setColor(0x40FF5C6C);
            s.setStrokeWidth(Math.max(1f, 1.1f * u));
            c.save();
            c.translate(5 * u, 5 * u);
            c.drawPath(shape, s);
            c.restore();
        }
    }

    // ---- Dunstbaender ----------------------------------------------------------

    private static void haze(Canvas c, int w, int h, float u, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[][] bands = {{0.10f, 70, 0.75f, 0x33}, {0.34f, 46, 0.55f, 0x26}, {0.58f, 90, 0.90f, 0x40}, {0.84f, 60, 0.70f, 0x30}};
        for (float[] b : bands) {
            float y = h * b[0], th = b[1] * u, len = w * b[2];
            float x = r.nextFloat() * (w - len * 0.6f) - len * 0.2f;
            p.setMaskFilter(new BlurMaskFilter(th * 0.45f, BlurMaskFilter.Blur.NORMAL));
            p.setShader(new LinearGradient(x, 0, x + len, 0, new int[]{0x00B3112A, ((int) b[3] << 24) | 0xC8152F, 0x00B3112A}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(x, y, x + len, y + th), th / 2, th / 2, p);
            // Heller Saum oben
            p.setMaskFilter(new BlurMaskFilter(2 * u, BlurMaskFilter.Blur.NORMAL));
            p.setShader(new LinearGradient(x, 0, x + len, 0, new int[]{0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRect(x + len * 0.1f, y + th * 0.18f, x + len * 0.9f, y + th * 0.18f + 1.2f * u, p);
        }
        p.setMaskFilter(null);
        p.setShader(new RadialGradient(w * 0.5f, h * 1.1f, w * 0.6f, 0x33300810, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }
}

package de.eclipseiptv.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/**
 * Rote Wolken mit weisser Kontur. Die Form besteht aus mehreren Kreisen auf einem
 * abgerundeten Sockel; Innenlinien deuten die einzelnen Baeusche an. Dieselben Zahlen
 * stehen in tools/art/cloud.py fuer Logo und Banner.
 */
public final class Clouds {
    /** Kreise (x, y, r) in Einheiten der Wolkenhoehe. */
    private static final float[][] LOBES = {{0.34f, 0.64f, 0.30f}, {0.72f, 0.42f, 0.38f}, {1.16f, 0.32f, 0.32f},
            {1.55f, 0.50f, 0.32f}, {1.86f, 0.70f, 0.24f}, {1.0f, 0.62f, 0.30f}};
    private static final float[] BASE = {0.06f, 0.58f, 2.06f, 0.98f, 0.20f};
    /** Innenlinien: Kreis, Startwinkel, Winkel. */
    private static final float[][] ARCS = {{0.72f, 0.42f, 0.38f, 40, 105}, {1.16f, 0.32f, 0.32f, 50, 95}, {1.55f, 0.50f, 0.32f, 40, 100}};
    private static final float[] CURL = {0.40f, 0.76f, 0.11f};
    /** Breite einer Wolke im Verhaeltnis zu ihrer Hoehe. */
    public static final float RATIO = 2.12f;

    private Clouds() {
    }

    static Path outline(float x, float y, float h) {
        Path out = new Path(), part = new Path();
        for (float[] l : LOBES) {
            part.reset();
            part.addCircle(x + l[0] * h, y + l[1] * h, l[2] * h, Path.Direction.CW);
            out.op(part, Path.Op.UNION);
        }
        part.reset();
        part.addRoundRect(new RectF(x + BASE[0] * h, y + BASE[1] * h, x + BASE[2] * h, y + BASE[3] * h), BASE[4] * h, BASE[4] * h, Path.Direction.CW);
        out.op(part, Path.Op.UNION);
        return out;
    }

    static Path inner(float x, float y, float h) {
        Path p = new Path();
        for (float[] a : ARCS) {
            float r = a[2] * h;
            RectF oval = new RectF(x + a[0] * h - r, y + a[1] * h - r, x + a[0] * h + r, y + a[1] * h + r);
            p.addArc(oval, a[3], a[4]);
        }
        float cx = x + CURL[0] * h, cy = y + CURL[1] * h, r0 = CURL[2] * h;
        for (int i = 0; i <= 40; i++) {
            float t = i / 40f;
            double a = -t * 0.8 * 2 * Math.PI;
            float r = r0 * (1 - 0.72f * t);
            float px = cx + (float) (r * Math.cos(a)), py = cy + (float) (r * Math.sin(a));
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        return p;
    }

    /** Eine einzelne Wolke als Drawable; passt sich der Hoehe der Flaeche an. */
    public static final class Cloud extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lines = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Path shape = new Path(), detail = new Path();
        private final boolean mirror;

        public Cloud(boolean mirror) {
            this.mirror = mirror;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(Ui.WHITE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            lines.setStyle(Paint.Style.STROKE);
            lines.setColor(0xEBFFFFFF);
            lines.setStrokeCap(Paint.Cap.ROUND);
            lines.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onBoundsChange(Rect b) {
            float h = Math.min(b.height(), b.width() / RATIO);
            float x = b.left + (b.width() - h * RATIO) / 2f, y = b.top + (b.height() - h) / 2f;
            shape = outline(x, y, h);
            detail = inner(x, y, h);
            float sw = Math.max(1.5f, h * 0.04f);
            stroke.setStrokeWidth(sw * 2);
            lines.setStrokeWidth(sw * 0.8f);
            fill.setShader(new LinearGradient(0, y, 0, y + h, 0xFFD0142F, 0xFF86091C, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas c) {
            int save = c.save();
            if (mirror) c.scale(-1, 1, getBounds().exactCenterX(), getBounds().exactCenterY());
            c.drawPath(shape, stroke);
            c.drawPath(shape, fill);
            c.drawPath(detail, lines);
            c.restoreToCount(save);
        }

        @Override
        public void setAlpha(int a) {
            fill.setAlpha(a);
            stroke.setAlpha(a);
            lines.setAlpha(a * 0xEB / 255);
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Eine Wolke als View, die langsam hin und her treibt. */
    public static View view(Context c, boolean mirror, float alpha, long driftMs, float driftDp) {
        View v = new View(c);
        v.setBackground(new Cloud(mirror));
        v.setAlpha(alpha);
        v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (driftMs > 0) {
            ObjectAnimator a = ObjectAnimator.ofFloat(v, "translationX", -Ui.px(driftDp), Ui.px(driftDp));
            a.setDuration(driftMs);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            a.setCurrentPlayTime((long) (Math.random() * driftMs));
            v.setTag(a);
        }
        return v;
    }

    /**
     * Ebene mit mehreren treibenden Wolken fuer den Hintergrund. Positionen in Einheiten
     * der 960x540-Flaeche: x, y, Hoehe, Deckkraft, gespiegelt (1/0).
     */
    public static final class Layer extends FrameLayout {
        public Layer(Context c, float[][] clouds) {
            super(c);
            setClipChildren(true);
            setFocusable(false);
            for (int i = 0; i < clouds.length; i++) {
                float[] k = clouds[i];
                View v = view(c, k[4] > 0, k[3], 26000 + i * 7000L, 10 + i * 3);
                LayoutParams lp = new LayoutParams(Ui.dp(k[2] * RATIO), Ui.dp(k[2]));
                lp.leftMargin = Ui.dp(k[0]);
                lp.topMargin = Ui.dp(k[1]);
                addView(v, lp);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            for (int i = 0; i < getChildCount(); i++) {
                Object a = getChildAt(i).getTag();
                if (a instanceof ObjectAnimator && !((ObjectAnimator) a).isStarted()) ((ObjectAnimator) a).start();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            for (int i = 0; i < getChildCount(); i++) {
                Object a = getChildAt(i).getTag();
                if (a instanceof ObjectAnimator) ((ObjectAnimator) a).cancel();
            }
            super.onDetachedFromWindow();
        }
    }

    /** Standard-Anordnung fuer den Hintergrund der Hauptansicht. */
    public static Layer background(Context c) {
        return new Layer(c, new float[][]{
                {560, -34, 64, 0.20f, 0},
                {878, 196, 50, 0.22f, 1},
                {752, 446, 92, 0.24f, 0},
                {-40, 474, 62, 0.18f, 1},
        });
    }

    static LinearInterpolator linear() {
        return new LinearInterpolator();
    }
}

package de.eclipseiptv.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * Rote Wolken mit weisser Kontur. Die Form besteht aus Kreisen ("Baeuschen") auf einem
 * abgerundeten Sockel; kurze Boegen innen deuten hintereinanderliegende Baeusche an.
 * Dieselben Zahlen stehen in tools/art/cloud.py fuer Logo und Banner.
 *
 * Effizienz: Jede Wolke wird genau einmal gezeichnet und liegt dann als fertiges Bild
 * (Hardware-Layer) auf der Grafikkarte. Die Bewegung verschiebt nur dieses Bild; die
 * Wolke selbst wird nicht neu gezeichnet. Ist die Ansicht nicht sichtbar (Vollbild,
 * App im Hintergrund), steht die Animation still und kostet nichts.
 */
public final class Clouds {
    /** Breite im Verhaeltnis zur Hoehe. */
    public static final float RATIO = 2.2f;
    /** Kreise (x, y, r) in Einheiten der Wolkenhoehe. */
    private static final float[][] LOBES = {{0.40f, 0.66f, 0.30f}, {0.80f, 0.42f, 0.36f}, {1.24f, 0.34f, 0.30f},
            {1.62f, 0.52f, 0.30f}, {1.92f, 0.72f, 0.22f}, {1.06f, 0.62f, 0.30f}};
    private static final float[] BASE = {0.12f, 0.60f, 2.10f, 0.96f, 0.18f};
    /** Innenlinien: Kreis (x, y, r), Startwinkel, Winkel. */
    private static final float[][] ARCS = {{0.74f, 0.80f, 0.22f, 200, 120}, {1.22f, 0.74f, 0.24f, 205, 115}, {1.68f, 0.82f, 0.18f, 205, 115}};
    private static final float[] CURL = {0.36f, 0.79f, 0.11f};

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
            float r = a[2] * h, cx = x + a[0] * h, cy = y + a[1] * h;
            p.addArc(new RectF(cx - r, cy - r, cx + r, cy + r), a[3], a[4]);
        }
        float cx = x + CURL[0] * h, cy = y + CURL[1] * h, r0 = CURL[2] * h;
        for (int i = 0; i <= 40; i++) {
            float t = i / 40f;
            double ang = -t * 0.85 * 2 * Math.PI + Math.PI * 0.5;
            float r = r0 * (1 - 0.7f * t);
            float px = cx + (float) (r * Math.cos(ang)), py = cy + (float) (r * Math.sin(ang));
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        return p;
    }

    /**
     * Eine Wolke. Sie wird in ihre Flaeche eingepasst, mit Rand fuer die Kontur, damit
     * oben und an den Seiten nichts abgeschnitten wird.
     */
    public static final class CloudView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lines = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean mirror;
        private Path shape = new Path(), detail = new Path();
        private ObjectAnimator drift;

        public CloudView(Context c, boolean mirror) {
            super(c);
            this.mirror = mirror;
            setFocusable(false);
            setWillNotDraw(false);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(Ui.WHITE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            lines.setStyle(Paint.Style.STROKE);
            lines.setColor(0xE6FFFFFF);
            lines.setStrokeCap(Paint.Cap.ROUND);
            lines.setStrokeJoin(Paint.Join.ROUND);
            // Einmal zeichnen, danach nur noch als fertiges Bild verschieben
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        /** Langsame Bewegung hin und her (und ein wenig auf und ab). */
        public CloudView drift(long ms, float dx, float dy) {
            drift = ObjectAnimator.ofPropertyValuesHolder(this,
                    PropertyValuesHolder.ofFloat(TRANSLATION_X, -Ui.px(dx), Ui.px(dx)),
                    PropertyValuesHolder.ofFloat(TRANSLATION_Y, Ui.px(dy), -Ui.px(dy)));
            drift.setDuration(ms);
            drift.setRepeatCount(ValueAnimator.INFINITE);
            drift.setRepeatMode(ValueAnimator.REVERSE);
            drift.setInterpolator(new AccelerateDecelerateInterpolator());
            drift.setCurrentPlayTime((long) (Math.random() * ms));
            return this;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float pad = Math.max(2f, h * 0.05f);
            float ch = Math.min(h - 2 * pad, (w - 2 * pad) / RATIO);
            if (ch <= 0) return;
            float x = (w - ch * RATIO) / 2f, y = (h - ch) / 2f;
            shape = outline(x, y, ch);
            detail = inner(x, y, ch);
            float sw = Math.max(1.5f, ch * 0.035f);
            stroke.setStrokeWidth(sw * 2);
            lines.setStrokeWidth(sw * 0.75f);
            fill.setShader(new LinearGradient(0, y, 0, y + ch, 0xFFD0142F, 0xFF86091C, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            if (mirror) c.scale(-1, 1, getWidth() / 2f, 0);
            c.drawPath(shape, stroke);
            c.drawPath(shape, fill);
            c.drawPath(detail, lines);
        }

        private void animate(boolean run) {
            if (drift == null) return;
            if (run) {
                if (!drift.isStarted()) drift.start();
                else if (drift.isPaused()) drift.resume();
            } else if (drift.isRunning()) {
                drift.pause();
            }
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            animate(visibility == VISIBLE && isShown());
        }

        @Override
        public void onVisibilityAggregated(boolean visible) {
            super.onVisibilityAggregated(visible);
            animate(visible && getWindowVisibility() == VISIBLE);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (drift != null) drift.cancel();
            super.onDetachedFromWindow();
        }
    }

    /**
     * Ebene mit mehreren treibenden Wolken fuer den Hintergrund. Je Wolke in Einheiten der
     * 960x540-Flaeche: x, y, Hoehe, Deckkraft, gespiegelt (1/0).
     */
    public static final class Layer extends FrameLayout {
        public Layer(Context c, float[][] clouds) {
            super(c);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            for (int i = 0; i < clouds.length; i++) {
                float[] k = clouds[i];
                CloudView v = new CloudView(c, k[4] > 0).drift(13000 + i * 3100L, 18 + i * 4, 2.5f);
                v.setAlpha(k[3]);
                LayoutParams lp = new LayoutParams(Ui.dp(k[2] * RATIO), Ui.dp(k[2]));
                lp.leftMargin = Ui.dp(k[0]);
                lp.topMargin = Ui.dp(k[1]);
                addView(v, lp);
            }
        }
    }

    /** Anordnung fuer den Hintergrund: alle Wolken ganz im Bild, keine hinter der Uhr. */
    public static Layer background(Context c) {
        return new Layer(c, new float[][]{
                {520, 8, 46, 0.32f, 0},
                {846, 168, 46, 0.36f, 1},
                {722, 432, 84, 0.34f, 0},
                {96, 470, 52, 0.24f, 1},
        });
    }
}

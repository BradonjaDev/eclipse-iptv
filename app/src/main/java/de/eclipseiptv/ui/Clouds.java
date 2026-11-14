package de.eclipseiptv.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.eclipseiptv.data.Bg;

/**
 * Rote Wolken mit Tiefe: Baeusche als beleuchtete, leicht flache Kugeln (Licht von oben
 * links), weiche Schatten zwischen den Baeuschen, ein Schlagschatten darunter und die
 * weisse Kontur. Dieselbe Form steht in tools/art/cloud3d.py fuer Logo und Banner.
 *
 * Effizienz: Jede Wolke wird einmal in einem Hintergrund-Thread als Bild berechnet (je
 * Groesse nur einmal, fuer alle Ansichten geteilt). Danach wird nur noch dieses Bild
 * verschoben. Sind die Wolken nicht zu sehen, steht ihre Bewegung still.
 */
public final class Clouds {
    /** Breite der Wolke im Verhaeltnis zu ihrer Hoehe. */
    public static final float RATIO = 2.2f;
    /** Hoehe der Ansicht im Verhaeltnis zur Wolke (Platz fuer den Schatten darunter). */
    static final float HEIGHT = 1.32f;
    /** Baeusche (x, y, r), von hinten nach vorn. */
    private static final float[][] LOBES = {{1.18f, 0.30f, 0.25f}, {0.86f, 0.36f, 0.26f}, {1.52f, 0.44f, 0.24f}, {0.55f, 0.52f, 0.24f},
            {1.20f, 0.56f, 0.26f}, {1.84f, 0.62f, 0.20f}, {0.30f, 0.70f, 0.20f}, {0.88f, 0.68f, 0.24f}, {1.56f, 0.70f, 0.22f}};
    private static final float[] BASE = {0.12f, 0.64f, 2.06f, 0.96f, 0.16f};
    private static final float SQUASH = 0.86f, GROW = 1.18f;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    private Clouds() {
    }

    /**
     * Sind Animationen auf dem Geraet abgeschaltet (Entwickleroptionen, Energiesparen),
     * springen Animationen sofort ans Ende. Dann bleiben die Wolken stattdessen verteilt stehen.
     */
    static boolean animationsOn() {
        // Schalter fuer die Screenshot-Tests (Robolectric spielt Endlos-Animationen nur einmal ab)
        if (Boolean.getBoolean("eclipse.staticSky")) return false;
        return android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    private static RectF lobe(float x, float y, float h, float[] l, float dx, float dy, float scale) {
        float rx = l[2] * GROW * scale * h, ry = l[2] * GROW * SQUASH * scale * h;
        float cx = x + (l[0] + dx) * h, cy = y + (l[1] + dy) * h;
        return new RectF(cx - rx, cy - ry, cx + rx, cy + ry);
    }

    private static Path outline(float x, float y, float h) {
        Path out = new Path(), part = new Path();
        for (float[] l : LOBES) {
            part.reset();
            part.addOval(lobe(x, y, h, l, 0, 0, 1), Path.Direction.CW);
            out.op(part, Path.Op.UNION);
        }
        part.reset();
        part.addRoundRect(new RectF(x + BASE[0] * h, y + BASE[1] * h, x + BASE[2] * h, y + BASE[3] * h), BASE[4] * h, BASE[4] * h, Path.Direction.CW);
        out.op(part, Path.Op.UNION);
        return out;
    }

    /** Zeichnet eine Wolke in ein Bild der Groesse w x h (Wolke oben, Schatten darunter). */
    static Bitmap render(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float pad = Math.max(2f, h * 0.04f);
        float ch = Math.min((h - pad) / HEIGHT, (w - 2 * pad) / RATIO);
        if (ch <= 2) return bmp;
        float x = (w - ch * RATIO) / 2f, y = pad;
        float sw = Math.max(1.5f, ch * 0.032f);
        Path shape = outline(x, y, ch);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Schlagschatten unter der Wolke
        p.setColor(0x99000000);
        p.setMaskFilter(new BlurMaskFilter(Math.max(1f, ch * 0.13f), BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(0, ch * 0.10f);
        c.drawPath(shape, p);
        c.restore();
        p.setMaskFilter(null);

        // Weisse Kontur (die innere Haelfte deckt die Fuellung gleich wieder zu)
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw * 2);
        p.setColor(Ui.WHITE);
        c.drawPath(shape, p);
        p.setStyle(Paint.Style.FILL);

        c.save();
        c.clipPath(shape);
        p.setShader(new LinearGradient(0, y + ch * 0.5f, 0, y + ch, 0xFFA50F26, 0xFF4F0511, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + ch * RATIO, y + ch, p);
        p.setShader(null);
        Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        shade.setColor(0x732A0008);
        shade.setMaskFilter(new BlurMaskFilter(Math.max(1f, ch * 0.09f), BlurMaskFilter.Blur.NORMAL));
        for (int i = 0; i < LOBES.length; i++) {
            float[] l = LOBES[i];
            // Weicher Schatten, den dieser Bausch auf die dahinterliegenden wirft
            if (i > 0) c.drawOval(lobe(x, y, ch, l, 0.02f, 0.05f, 1.017f), shade);
            RectF r = lobe(x, y, ch, l, 0, 0, 1);
            RadialGradient g = new RadialGradient(r.left + r.width() * 0.40f, r.top + r.height() * 0.32f, r.width() * 0.80f,
                    new int[]{0xFFEE3049, 0xFFCC1730, 0xFFA00F25, 0xFF8C0C20}, new float[]{0f, 0.55f, 0.9f, 1f}, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(1f, r.height() / r.width(), r.left + r.width() * 0.40f, r.top + r.height() * 0.32f);
            g.setLocalMatrix(m);
            p.setShader(g);
            c.drawOval(r, p);
        }
        // Unten dunkler (Umgebungsschatten), oben ein Hauch Glanz
        p.setShader(new LinearGradient(0, y + ch * 0.5f, 0, y + ch, 0x00140004, 0x99140004, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + ch * RATIO, y + ch, p);
        p.setShader(new LinearGradient(0, y, 0, y + ch * 0.55f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + ch * RATIO, y + ch, p);
        c.restore();
        return bmp;
    }

    /** Eine Wolke. Das Bild wird im Hintergrund berechnet und fuer gleiche Groessen geteilt. */
    public static final class CloudView extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final boolean mirror;
        private Bitmap bitmap;
        private ObjectAnimator drift;
        private long phase;
        private int gen;

        public CloudView(Context c, boolean mirror) {
            super(c);
            this.mirror = mirror;
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        /** Hoehe der Ansicht fuer eine Wolke der Hoehe h (beides in Pixeln). */
        public static int viewHeight(int h) {
            return Math.round(h * HEIGHT + Math.max(2f, h * HEIGHT * 0.04f));
        }

        /** Langsame Bewegung hin und her (fuer einzelne Wolken, z. B. am Logo). */
        public CloudView drift(long ms, float dx, float dy) {
            drift = ObjectAnimator.ofPropertyValuesHolder(this,
                    PropertyValuesHolder.ofFloat(TRANSLATION_X, -Ui.px(dx), Ui.px(dx)),
                    PropertyValuesHolder.ofFloat(TRANSLATION_Y, Ui.px(dy), -Ui.px(dy)));
            drift.setDuration(ms);
            drift.setRepeatCount(ValueAnimator.INFINITE);
            drift.setRepeatMode(ValueAnimator.REVERSE);
            drift.setInterpolator(new AccelerateDecelerateInterpolator());
            phase = (long) (Math.random() * ms);
            return this;
        }

        @Override
        protected void onSizeChanged(final int w, final int h, int ow, int oh) {
            if (w <= 0 || h <= 0) return;
            final String key = w + "x" + h;
            Bitmap cached = CACHE.get(key);
            if (cached != null) {
                bitmap = cached;
                invalidate();
                return;
            }
            final int g = ++gen;
            Bg.run(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = render(w, h);
                    Bg.ui(new Runnable() {
                        @Override
                        public void run() {
                            CACHE.put(key, b);
                            if (g != gen) return;
                            bitmap = b;
                            invalidate();
                        }
                    });
                }
            });
        }

        @Override
        protected void onDraw(Canvas c) {
            if (bitmap == null) return;
            if (mirror) c.scale(-1, 1, getWidth() / 2f, 0);
            c.drawBitmap(bitmap, 0, 0, paint);
        }

        void animate(boolean run) {
            if (drift == null || !animationsOn()) return;
            if (run) {
                if (!drift.isStarted()) {
                    // Der Startpunkt muss nach start() gesetzt werden, sonst beginnt die Animation bei null
                    drift.start();
                    drift.setCurrentPlayTime(phase);
                } else if (drift.isPaused()) {
                    drift.resume();
                }
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
     * Himmel mit Wolken, die langsam von links nach rechts ziehen, rechts hinausfliegen und
     * links wieder hereinkommen. Kleine, blasse Wolken ziehen langsamer als grosse, kraeftige:
     * das gibt Tiefe (Parallaxe). Bei jedem neuen Durchgang aendert sich die Hoehe ein wenig.
     */
    public static final class Sky extends FrameLayout {
        /** Je Wolke: Bahn (y in 960x540-Einheiten), Hoehe, Geschwindigkeit (Einheiten/s), Deckkraft, Startpunkt 0..1. */
        private final float[][] spec;
        private final List<CloudView> views = new ArrayList<>();
        private final List<ObjectAnimator> flights = new ArrayList<>();
        private final List<Long> phases = new ArrayList<>();
        private final Random random = new Random();
        private boolean running;

        public Sky(Context c, float[][] spec) {
            super(c);
            this.spec = spec;
            setFocusable(false);
            setClipChildren(true);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            for (int i = 0; i < spec.length; i++) {
                float[] k = spec[i];
                CloudView v = new CloudView(c, i % 2 == 1);
                v.setAlpha(k[3]);
                int h = Ui.dp(k[1]);
                LayoutParams lp = new LayoutParams(Math.round(h * RATIO), CloudView.viewHeight(h));
                lp.topMargin = Ui.dp(k[0]);
                addView(v, lp);
                views.add(v);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            for (ObjectAnimator a : flights) a.cancel();
            flights.clear();
            phases.clear();
            if (w <= 0) return;
            for (int i = 0; i < views.size(); i++) {
                final CloudView v = views.get(i);
                float[] k = spec[i];
                int cw = v.getLayoutParams().width;
                float from = -cw, to = w;
                long ms = (long) ((to - from) / Ui.px(k[2]) * 1000);
                ObjectAnimator a = ObjectAnimator.ofFloat(v, TRANSLATION_X, from, to);
                a.setInterpolator(new LinearInterpolator());
                a.setDuration(ms);
                a.setRepeatCount(ValueAnimator.INFINITE);
                a.setRepeatMode(ValueAnimator.RESTART);
                a.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationRepeat(Animator an) {
                        // Neue Runde, etwas andere Hoehe
                        v.setTranslationY(Ui.px((random.nextFloat() - 0.5f) * 24f));
                    }
                });
                flights.add(a);
                phases.add((long) (k[4] * ms));
                v.setTranslationX(from + (to - from) * k[4]);
            }
            update();
        }

        private void update() {
            boolean run = getWindowVisibility() == VISIBLE && isShown();
            if (!animationsOn()) {
                // Ohne Animationen: Wolken an ihrem Startpunkt stehen lassen
                for (ObjectAnimator a : flights) a.cancel();
                for (int i = 0; i < views.size() && i < spec.length; i++) {
                    View v = views.get(i);
                    int cw = v.getLayoutParams().width;
                    v.setTranslationX(-cw + (getWidth() + cw) * spec[i][4]);
                }
                running = false;
                return;
            }
            for (int i = 0; i < flights.size(); i++) {
                ObjectAnimator a = flights.get(i);
                if (run) {
                    if (!a.isStarted()) {
                        // Startpunkt nach start() setzen: so sind schon beim Oeffnen Wolken ueber den ganzen Himmel verteilt
                        a.start();
                        a.setCurrentPlayTime(phases.get(i));
                    } else if (a.isPaused()) {
                        a.resume();
                    }
                } else if (a.isRunning()) {
                    a.pause();
                }
            }
            running = run;
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            update();
        }

        @Override
        public void onVisibilityAggregated(boolean visible) {
            super.onVisibilityAggregated(visible);
            update();
        }

        @Override
        protected void onDetachedFromWindow() {
            for (ObjectAnimator a : flights) a.cancel();
            super.onDetachedFromWindow();
        }

        boolean running() {
            return running;
        }
    }

    /** Himmel fuer den Hintergrund der Hauptansicht. */
    public static Sky background(Context c) {
        return new Sky(c, new float[][]{
                // Bahn, Hoehe, Tempo, Deckkraft, Startpunkt
                {10, 24, 7, 0.40f, 0.15f},
                {196, 22, 6, 0.34f, 0.70f},
                {112, 36, 11, 0.50f, 0.55f},
                {318, 40, 12, 0.50f, 0.05f},
                {262, 54, 16, 0.55f, 0.85f},
                {436, 66, 19, 0.62f, 0.35f},
        });
    }
}

package de.eclipseiptv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.util.Random;

/**
 * Der lebendige Hintergrund: roter Rauch, darueber drei grosse, weiche Lichtflaechen, die
 * langsam wandern und sich mischen, und einzelne Funken, die aufsteigen und verglimmen.
 *
 * Sparsam gebaut, damit die TV-Box Luft fuer das Video behaelt:
 * - Rauch und Licht werden gemeinsam in ein kleines Bild (640x360) gerechnet, 15-mal pro
 *   Sekunde. Bei so langsamer Bewegung sieht man keinen Unterschied zu mehr Bildern. Auf dem
 *   Bildschirm ist das eine einzige, weich hochskalierte Flaeche.
 * - Funken sind ein fertig gezeichneter Leuchtpunkt, der nur verschoben wird (30-mal pro Sekunde).
 * - Ist der Hintergrund nicht zu sehen (Vollbild, App im Hintergrund), steht alles still.
 * - Sind Animationen auf dem Geraet abgeschaltet, bleibt ein ruhiges Standbild.
 */
public final class Ambient extends View implements Choreographer.FrameCallback {
    /** Lichtflaechen: Mitte x, Mitte y, Radius (Anteile der Breite), Periode (s), Farbe. */
    private static final float[][] LIGHTS = {{0.78f, 0.18f, 0.55f, 48}, {0.30f, 0.78f, 0.62f, 61}, {0.62f, 0.62f, 0.40f, 39}};
    private static final int[] LIGHT_COLORS = {0x42DD1A34, 0x617E0A1B, 0x1FFF5C6C};
    private static final int EMBERS = 34;

    private final Bitmap frame = Bitmap.createBitmap(Backdrop.W, Backdrop.H, Bitmap.Config.ARGB_8888);
    private final Canvas frameCanvas = new Canvas(frame);
    private final Paint copy = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint sprite = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RadialGradient[] shaders = new RadialGradient[LIGHTS.length];
    private final Rect dst = new Rect();
    private final Bitmap ember;
    private final float[][] embers = new float[EMBERS][6];
    private final Random random = new Random();
    private Bitmap smoke;
    private boolean running;
    private long start, lastLight, lastFrame;

    public Ambient(Context c) {
        super(c);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Normal ueberblenden (wie in der Vorschau): Addieren wuerde den Rauch zu hell machen
        for (int i = 0; i < LIGHTS.length; i++) {
            shaders[i] = new RadialGradient(0, 0, LIGHTS[i][2] * Backdrop.W, LIGHT_COLORS[i], 0x00000000, Shader.TileMode.CLAMP);
        }
        ember = emberSprite(48);
        smoke = Backdrop.smoke(c, new Runnable() {
            @Override
            public void run() {
                smoke = Backdrop.smoke(getContext(), null);
                composeLight(SystemClock.uptimeMillis());
                invalidate();
            }
        });
        start = SystemClock.uptimeMillis() - (long) (random.nextFloat() * 60000);
        for (float[] e : embers) spawn(e, true);
        composeLight(SystemClock.uptimeMillis());
    }

    /** Leuchtpunkt: weisslich-heisse Mitte, nach aussen rot, am Rand durchsichtig. */
    private static Bitmap emberSprite(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = size / 2f;
        p.setShader(new RadialGradient(r, r, r, new int[]{0xE6FFD0D6, 0x73FF2B42, 0x00DD1A34}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r, p);
        return b;
    }

    /** Funke: x, y (0..1), Tempo (Hoehen/s), Groesse, Phase, Lebenskraft. */
    private void spawn(float[] e, boolean anywhere) {
        e[0] = random.nextFloat();
        e[1] = anywhere ? random.nextFloat() : 1.03f;
        e[2] = 0.015f + random.nextFloat() * 0.026f;
        e[3] = 0.8f + random.nextFloat() * 1.6f;
        e[4] = random.nextFloat() * 6.28f;
        e[5] = 0.4f + random.nextFloat() * 0.6f;
    }

    /** Rauch und wanderndes Licht in das kleine Bild rechnen. */
    private void composeLight(long now) {
        if (smoke != null) frameCanvas.drawBitmap(smoke, 0, 0, null);
        else frameCanvas.drawColor(Ui.BG);
        float t = (now - start) / 1000f;
        for (int i = 0; i < LIGHTS.length; i++) {
            float[] l = LIGHTS[i];
            double ph = t / l[3] * Math.PI * 2;
            float cx = (float) ((l[0] + Math.sin(ph) * 0.14) * Backdrop.W);
            float cy = (float) ((l[1] + Math.cos(ph * 0.8) * 0.12) * Backdrop.H);
            light.setShader(shaders[i]);
            frameCanvas.save();
            frameCanvas.translate(cx, cy);
            float r = l[2] * Backdrop.W;
            frameCanvas.drawRect(-r, -r, r, r, light);
            frameCanvas.restore();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        dst.set(0, 0, getWidth(), getHeight());
        c.drawBitmap(frame, null, dst, copy);
        float u = getHeight() / 540f, w = getWidth(), h = getHeight();
        for (float[] e : embers) {
            float fade = Math.min(1f, e[1] * 1.4f) * e[5];
            if (fade <= 0.01f) continue;
            float size = e[3] * 6.4f * u;
            float x = e[0] * w, y = e[1] * h;
            sprite.setAlpha((int) (255 * fade));
            dst.set((int) (x - size / 2), (int) (y - size / 2), (int) (x + size / 2), (int) (y + size / 2));
            c.drawBitmap(ember, null, dst, sprite);
        }
    }

    @Override
    public void doFrame(long nanos) {
        if (!running) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastFrame >= 32) {
            float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
            lastFrame = now;
            float t = (now - start) / 1000f;
            for (float[] e : embers) {
                e[1] -= e[2] * dt;
                e[0] += (float) Math.sin(t * 0.6 + e[4]) * 0.003f * dt;
                if (e[1] < -0.03f) spawn(e, false);
            }
            if (now - lastLight >= 66) {
                lastLight = now;
                composeLight(now);
            }
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    /** Bewegung erlaubt? Nicht, wenn Animationen auf dem Geraet aus sind, und nicht in den Screenshot-Tests. */
    static boolean moving() {
        if (Boolean.getBoolean("eclipse.stillBackground")) return false;
        return android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
    }

    private void update() {
        boolean on = getWindowVisibility() == VISIBLE && isShown() && isAttachedToWindow() && moving();
        if (on == running) return;
        running = on;
        if (on) {
            lastFrame = 0;
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
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
}

package de.eclipseiptv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Nebula;

/**
 * Der Hintergrund der App: Schwarz mit rotem Rauch und Mondlicht (siehe Nebula).
 *
 * Das Bild wird beim ersten Start einmal im Hintergrund berechnet und als Datei
 * zwischengespeichert; danach ist es sofort da. Gezeichnet wird es weich hochskaliert in
 * einem einzigen Schritt – der Hintergrund kostet pro Bild also so gut wie nichts.
 */
public final class Backdrop extends Drawable {
    private static final int W = 640, H = 360, SEED = 7, VERSION = 1;
    private static Bitmap shared;
    private static boolean loading;
    private static final List<WeakReference<Backdrop>> WAITING = new ArrayList<>();

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

    public Backdrop(Context c) {
        if (shared == null) load(c.getApplicationContext(), this);
    }

    private static void load(final Context app, Backdrop who) {
        WAITING.add(new WeakReference<>(who));
        if (loading) return;
        loading = true;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                File f = new File(app.getCacheDir(), "backdrop-" + VERSION + ".png");
                Bitmap b = null;
                if (f.exists()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (b == null) {
                    b = Bitmap.createBitmap(Nebula.render(W, H, SEED), W, H, Bitmap.Config.ARGB_8888);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        b.compress(Bitmap.CompressFormat.PNG, 100, out);
                    } catch (Exception ignored) {
                    }
                }
                final Bitmap done = b;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        shared = done;
                        loading = false;
                        for (WeakReference<Backdrop> r : WAITING) {
                            Backdrop d = r.get();
                            if (d != null) d.invalidateSelf();
                        }
                        WAITING.clear();
                    }
                });
            }
        });
    }

    @Override
    public void draw(Canvas c) {
        if (shared != null) c.drawBitmap(shared, null, getBounds(), paint);
        else c.drawColor(Ui.BG);
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

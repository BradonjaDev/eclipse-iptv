package de.eclipseiptv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Nebula;

/**
 * Laedt das Rauchbild des Hintergrunds (siehe Nebula). Es wird beim ersten Start einmal im
 * Hintergrund berechnet und als Datei zwischengespeichert; danach ist es sofort da.
 */
public final class Backdrop {
    public static final int W = 640, H = 360;
    private static final int SEED = 7, VERSION = 1;
    private static Bitmap shared;
    private static boolean loading;
    private static final List<Runnable> WAITING = new ArrayList<>();

    private Backdrop() {
    }

    /** Das Rauchbild, oder null, solange es noch berechnet wird (dann folgt onReady). */
    public static Bitmap smoke(Context c, Runnable onReady) {
        if (shared != null) return shared;
        if (onReady != null) WAITING.add(onReady);
        if (loading) return null;
        loading = true;
        final Context app = c.getApplicationContext();
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
                        for (Runnable r : new ArrayList<>(WAITING)) r.run();
                        WAITING.clear();
                    }
                });
            }
        });
        return null;
    }
}

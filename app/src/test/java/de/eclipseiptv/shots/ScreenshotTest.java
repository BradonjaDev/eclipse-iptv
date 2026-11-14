package de.eclipseiptv.shots;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import de.eclipseiptv.DetailActivity;
import de.eclipseiptv.MainActivity;
import de.eclipseiptv.PlayerActivity;
import de.eclipseiptv.SetupActivity;
import de.eclipseiptv.data.Db;
import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.PlayQueue;
import de.eclipseiptv.data.Playable;
import de.eclipseiptv.data.Prefs;
import de.eclipseiptv.data.Source;

/**
 * Faehrt die App mit erfundenen Daten durch und speichert Bildschirmfotos (1920x1080)
 * nach app/build/shots. Laeuft ohne Geraet; dient zur Sichtpruefung des Designs und
 * prueft nebenbei die Bedienung mit den Pfeiltasten.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScreenshotTest {
    private static final File OUT = new File(System.getProperty("shots.dir", "build/shots"));
    private final List<String> log = new ArrayList<>();

    private static void idle(int ms) {
        long end = System.currentTimeMillis() + ms;
        do {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(40));
            try {
                Thread.sleep(15);
            } catch (InterruptedException ignored) {
            }
        } while (System.currentTimeMillis() < end);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void shot(View v, String name) throws Exception {
        idle(300);
        Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        try (FileOutputStream f = new FileOutputStream(new File(OUT, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    private static void shot(Activity a, String name) throws Exception {
        shot(a.getWindow().getDecorView(), name);
    }

    /**
     * Drueckt eine Taste wie die Fernbedienung. Robolectric reicht Tasten nur an die
     * Ansichten weiter; das Weiterschieben des Fokus mit den Pfeiltasten macht sonst
     * Android selbst (ViewRootImpl). Das wird hier genauso nachgebildet: erst die Taste
     * anbieten, und wenn niemand sie nutzt, focusSearch + requestFocus.
     */
    private static void press(Activity a, int code) {
        View decor = a.getWindow().getDecorView();
        boolean handled = a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : code == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT
                : code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN : 0;
        if (!handled && dir != 0) {
            View f = decor.findFocus();
            if (f != null) {
                View n = f.focusSearch(dir);
                if (n != null) n.requestFocus(dir);
            }
        }
        a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        idle(250);
    }

    /** Beschreibt, wo der Fokus gerade steht: Klassen der Eltern und der erste Text darin. */
    private static String focus(Activity a) {
        View f = a.getWindow().getDecorView().findFocus();
        if (f == null) return "(kein Fokus)";
        StringBuilder b = new StringBuilder(text(f));
        ViewParent p = f.getParent();
        int depth = 0;
        while (p instanceof View && depth++ < 6) {
            b.append(" < ").append(p.getClass().getSimpleName());
            p = p.getParent();
        }
        return b.toString();
    }

    private static String text(View v) {
        if (v instanceof TextView) return "\"" + ((TextView) v).getText() + "\"";
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                String t = text(g.getChildAt(i));
                if (!t.isEmpty()) return t;
            }
        }
        return "";
    }

    private void skyLog(View v) {
        if (v instanceof de.eclipseiptv.ui.Clouds.Sky) {
            ViewGroup g = (ViewGroup) v;
            note("Himmel " + v.getWidth() + "x" + v.getHeight() + " sichtbar=" + v.isShown() + " Wolken=" + g.getChildCount());
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                note("  Wolke " + i + ": " + c.getWidth() + "x" + c.getHeight() + " x=" + c.getLeft() + "+" + c.getTranslationX() + " y=" + c.getTop() + " alpha=" + c.getAlpha());
            }
            return;
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) skyLog(((ViewGroup) v).getChildAt(i));
    }

    private void note(String s) {
        log.add(s);
        System.out.println("SHOT " + s);
    }

    @Test
    public void tour() throws Exception {
        OUT.mkdirs();
        FakeXtream server = new FakeXtream();
        try {
            run(server);
        } finally {
            server.stop();
            try (FileWriter w = new FileWriter(new File(OUT, "log.txt"))) {
                for (String s : log) w.write(s + "\n");
            }
        }
    }

    private void run(FakeXtream server) throws Exception {
        System.setProperty("eclipse.staticSky", "true");
        // Fernbedienung statt Touchscreen: sonst nimmt Android keinen Tastenfokus an
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false);
        android.content.Context app = ApplicationProvider.getApplicationContext();
        Source s = Source.xtream(server.base, "demo", "demo");
        s.name = "Wohnzimmer";
        int id = Db.get(app).importSource(s, null);
        Prefs prefs = new Prefs(app);
        prefs.source(id);
        s = Db.get(app).source(id);
        // Ein paar Merker, damit Start und "Weiterschauen" gefuellt sind
        Db db = Db.get(app);
        for (int n = 1; n <= 5; n++) db.setFav(db.itemByNum(id, Item.LIVE, n), true);
        db.setFav(db.itemByNum(id, Item.VOD, 3), true);
        db.saveProgress(id, Item.VOD, "5001", "Nachtfalter (2025)", server.base + "/img/poster-1.png", "", 2400000, 6000000);
        db.saveProgress(id, Item.LIVE, "1002", "ZDF HD", server.base + "/img/logo-2.png", "", 0, 0);
        idle(500);

        ActivityController<MainActivity> mc = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity main = mc.get();
        idle(1500);
        note("Touch-Modus: " + main.getWindow().getDecorView().isInTouchMode());
        skyLog(main.getWindow().getDecorView());
        shot(main, "01-start");
        note("Start: " + focus(main));

        press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(600);
        note("Leiste Sender: " + focus(main));
        press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(800);
        note("Kategorien: " + focus(main));
        shot(main, "02-sender-kategorien");
        press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(900);
        press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(600);
        for (int i = 0; i < 4; i++) press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(900);
        note("Senderliste: " + focus(main));
        shot(main, "03-senderliste");
        press(main, KeyEvent.KEYCODE_DPAD_LEFT);
        note("Senderliste, dann links: " + focus(main));
        shot(main, "04-senderliste-links");
        press(main, KeyEvent.KEYCODE_DPAD_LEFT);
        note("noch einmal links: " + focus(main));

        press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(600);
        press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(600);
        press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(1500);
        note("Filme: " + focus(main));
        shot(main, "05-filme");
        press(main, KeyEvent.KEYCODE_DPAD_LEFT);
        note("Filme, dann links: " + focus(main));
        press(main, KeyEvent.KEYCODE_DPAD_LEFT);
        press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(600);
        press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(1500);
        shot(main, "06-serien");
        press(main, KeyEvent.KEYCODE_DPAD_LEFT);
        press(main, KeyEvent.KEYCODE_DPAD_LEFT);
        press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(800);
        shot(main, "07-optionen");
        mc.pause().stop();

        Intent di = new Intent(app, DetailActivity.class).putExtra("source", id).putExtra("kind", Item.VOD).putExtra("ext", "5000");
        ActivityController<DetailActivity> dc = Robolectric.buildActivity(DetailActivity.class, di).setup();
        idle(1500);
        shot(dc.get(), "08-film");
        dc.pause().stop().destroy();

        Intent si = new Intent(app, DetailActivity.class).putExtra("source", id).putExtra("kind", Item.SERIES).putExtra("ext", "7000");
        ActivityController<DetailActivity> sc = Robolectric.buildActivity(DetailActivity.class, si).setup();
        idle(1500);
        shot(sc.get(), "09-serie");
        sc.pause().stop().destroy();

        ActivityController<SetupActivity> uc = Robolectric.buildActivity(SetupActivity.class).setup();
        idle(800);
        shot(uc.get(), "10-einrichtung");
        uc.pause().stop().destroy();

        // Senderliste -> Vollbild -> zurueck: der Sender laeuft in der Vorschau weiter
        mc = Robolectric.buildActivity(MainActivity.class).setup();
        main = mc.get();
        idle(1200);
        press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(600);
        press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(800);
        press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle(600);
        press(main, KeyEvent.KEYCODE_DPAD_DOWN);
        idle(600);
        note("Vor dem Vollbild: " + focus(main));
        try {
            List<Playable> q = new ArrayList<>();
            for (Item it : db.items(id, Item.LIVE, "1", false)) q.add(Playable.live(s, it, false));
            PlayQueue.set(s, q, 1, "DE • Deutschland");
            mc.pause();
            ActivityController<PlayerActivity> pc = Robolectric.buildActivity(PlayerActivity.class).setup();
            mc.stop();
            idle(800);
            shot(pc.get(), "11-player-einblendung");
            press(pc.get(), KeyEvent.KEYCODE_DPAD_UP);
            idle(400);
            note("Nach Umschalten laeuft: " + (de.eclipseiptv.ui.LivePlayer.current() == null ? "nichts" : de.eclipseiptv.ui.LivePlayer.current().title));
            press(pc.get(), KeyEvent.KEYCODE_DPAD_CENTER);
            idle(600);
            shot(pc.get(), "12-player-senderliste");
            press(pc.get(), KeyEvent.KEYCODE_BACK);
            pc.get().finish();
            pc.pause();
            mc.restart().start().resume();
            pc.stop().destroy();
            idle(1500);
            note("Vorschau nach Rueckkehr: " + (de.eclipseiptv.ui.LivePlayer.current() == null ? "nichts" : de.eclipseiptv.ui.LivePlayer.current().title));
            note("Fokus nach Rueckkehr: " + focus(main));
            shot(main, "13-sender-mit-vorschau");
        } catch (Throwable t) {
            note("Player: " + t);
            for (StackTraceElement e : t.getStackTrace()) if (e.getClassName().startsWith("de.eclipseiptv")) note("   at " + e);
        }

        // Suche
        try {
            for (int i = 0; i < 4; i++) press(main, KeyEvent.KEYCODE_DPAD_LEFT);
            for (int i = 0; i < 6; i++) press(main, KeyEvent.KEYCODE_DPAD_UP);
            idle(600);
            press(main, KeyEvent.KEYCODE_DPAD_RIGHT);
            View f = main.getWindow().getDecorView().findFocus();
            note("Suche: " + focus(main));
            if (f instanceof android.widget.EditText) ((android.widget.EditText) f).setText("de");
            idle(1500);
            shot(main, "14-suche");
        } catch (Throwable t) {
            note("Suche: " + t);
        }
        mc.pause().stop();

        // Film im Player mit Steuerleiste und Optionen
        try {
            Item movie = db.item(id, Item.VOD, "5000");
            List<Playable> q = new ArrayList<>();
            Playable p = Playable.movie(s, movie);
            p.startPos = 0;
            q.add(p);
            PlayQueue.set(s, q, 0, "");
            ActivityController<PlayerActivity> vc = Robolectric.buildActivity(PlayerActivity.class).setup();
            idle(800);
            shot(vc.get(), "15-film-player");
            press(vc.get(), KeyEvent.KEYCODE_DPAD_DOWN);
            idle(600);
            Dialog d = ShadowDialog.getLatestDialog();
            if (d != null && d.isShowing()) {
                View dv = d.getWindow().getDecorView();
                note("Optionsfenster: Touch-Modus " + dv.isInTouchMode() + ", Fokus " + (dv.findFocus() == null ? "keiner" : text(dv.findFocus())));
                shot(dv, "16-optionen-fenster");
            }
            else note("Kein Optionsfenster");
            vc.pause().stop().destroy();
        } catch (Throwable t) {
            note("Film-Player: " + t);
        }

        // ---- Design-Vorschau: Oberflaeche ohne Hintergrund (fuer Live-Animationen auf der Vorschauseite)
        variant("ui-start", false, () -> de.eclipseiptv.ui.Design.previewTransparent = true);
        variant("ui-sender", true, () -> de.eclipseiptv.ui.Design.previewTransparent = true);
        de.eclipseiptv.ui.Design.previewTransparent = false;
    }

    /** Setzt die Design-Werte zurueck, wendet eine Variante an und fotografiert Senderliste oder Start. */
    private void variant(String name, boolean sender, Runnable apply) throws Exception {
        de.eclipseiptv.ui.Design.rowBacking = false;
        de.eclipseiptv.ui.Design.listEclipseRim = 0xFFDD1A34;
        de.eclipseiptv.ui.Design.listEclipse = new float[]{0.44f, 0.56f, 0.47f};
        de.eclipseiptv.ui.Design.listEclipseIntensity = 0.34f;
        apply.run();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity m = c.get();
        idle(1500);
        if (sender) {
            press(m, KeyEvent.KEYCODE_DPAD_DOWN);
            idle(600);
            press(m, KeyEvent.KEYCODE_DPAD_RIGHT);
            idle(600);
            press(m, KeyEvent.KEYCODE_DPAD_DOWN);
            press(m, KeyEvent.KEYCODE_DPAD_DOWN);
            idle(900);
            press(m, KeyEvent.KEYCODE_DPAD_RIGHT);
            press(m, KeyEvent.KEYCODE_DPAD_DOWN);
            idle(900);
        }
        shot(m, name);
        c.pause().stop().destroy();
        idle(300);
    }
}

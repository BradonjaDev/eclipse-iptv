package de.umbratv.data;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Liest eine Playlist im Hintergrund ein und meldet den Fortschritt an die Oberflaeche. */
public final class Sync {
    public interface Listener {
        void onSync();
    }

    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static volatile boolean running;
    private static volatile String error;
    private static volatile int stage = -1;
    private static final int[] COUNTS = new int[3];
    /** Zaehlt hoch, sobald ein Einlesen erfolgreich war. Daran erkennen Ansichten, dass sie neu laden muessen. */
    private static volatile int generation;

    private Sync() {
    }

    public static boolean running() {
        return running;
    }

    public static String error() {
        return error;
    }

    public static int generation() {
        return generation;
    }

    public static void clearError() {
        error = null;
    }

    /** Text fuer die Anzeige, z. B. "Filme werden geladen … 12.000". */
    public static String status() {
        int s = stage;
        if (s < 0) return "Verbindung wird aufgebaut …";
        String what = s == 0 ? "Sender" : s == 1 ? "Filme" : "Serien";
        int n = COUNTS[s];
        return what + " werden geladen …" + (n > 0 ? " " + String.format("%,d", n).replace(',', '.') : "");
    }

    public static void add(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void remove(Listener l) {
        LISTENERS.remove(l);
    }

    private static void notifyUi() {
        Bg.ui(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<>(LISTENERS)) l.onSync();
            }
        });
    }

    /** Startet das Einlesen. activate: die Quelle danach zur aktiven machen. */
    public static synchronized boolean start(Context c, final Source s, final boolean activate) {
        if (running) return false;
        final Context app = c.getApplicationContext();
        running = true;
        error = null;
        stage = -1;
        COUNTS[0] = COUNTS[1] = COUNTS[2] = 0;
        notifyUi();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int id = Db.get(app).importSource(s, new Importer.Sink() {
                        long last;

                        @Override
                        public void category(int kind, String ext, String name, boolean adult) {
                        }

                        @Override
                        public void item(Item it) {
                        }

                        @Override
                        public void episode(String seriesExt, Episode e) {
                        }

                        @Override
                        public void progress(int kind, int count) {
                            stage = kind;
                            COUNTS[kind] = count;
                            long now = System.currentTimeMillis();
                            if (now - last > 250) {
                                last = now;
                                notifyUi();
                            }
                        }
                    });
                    if (activate) new Prefs(app).source(id);
                    generation++;
                } catch (Throwable e) {
                    if (s.id != 0 && Db.get(app).source(s.id) == null) s.id = 0;
                    String m = e.getMessage();
                    error = m == null || m.isEmpty() ? "Die Playlist konnte nicht geladen werden." : friendly(m);
                } finally {
                    running = false;
                    notifyUi();
                }
            }
        }, "sync");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
        return true;
    }

    private static String friendly(String m) {
        String low = m.toLowerCase();
        if (low.contains("unable to resolve host") || low.contains("unknownhost")) return "Der Server wurde nicht gefunden. Stimmt die Adresse, und ist das Geraet online?";
        if (low.contains("timeout") || low.contains("timed out")) return "Der Server antwortet nicht (Zeitueberschreitung).";
        if (low.contains("failed to connect") || low.contains("refused")) return "Keine Verbindung zum Server moeglich.";
        if (low.contains("cleartext")) return "Unverschluesselte Verbindung wurde blockiert.";
        return m;
    }
}

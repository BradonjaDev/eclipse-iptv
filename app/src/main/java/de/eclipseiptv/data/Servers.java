package de.eclipseiptv.data;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ausweich-Server fuer Xtream-Zugaenge. Viele Anbieter nennen mehrere Adressen fuer
 * denselben Zugang. Faellt eine aus, versucht die App der Reihe nach die anderen und
 * merkt sich die, die zuletzt funktioniert hat.
 */
public final class Servers {
    /** Ein Abruf gegen einen bestimmten Server. */
    public interface Call<T> {
        T run(String base) throws IOException;
    }

    /** Speichert den funktionierenden Server dauerhaft (in der App: die Datenbank). */
    public interface Store {
        void save(int sourceId, String base, String alts);
    }

    private static final Map<Integer, String> active = new ConcurrentHashMap<>();
    private static volatile Store store;

    private Servers() {
    }

    public static void setStore(Store s) {
        store = s;
    }

    /** Alle Server der Quelle, der zuletzt funktionierende zuerst. */
    public static List<String> order(Source s) {
        List<String> all = s.servers();
        String a = active.get(s.id);
        if (a != null && all.remove(a)) all.add(0, a);
        return all;
    }

    /** Der Server, der gerade verwendet wird. */
    public static String current(Source s) {
        List<String> all = order(s);
        return all.isEmpty() ? s.base : all.get(0);
    }

    /**
     * Fuehrt einen Abruf aus. Ist der Server nicht erreichbar oder antwortet mit einem
     * Fehler, kommen die anderen Server der Reihe nach dran. Scheitern alle, wird der
     * erste Fehler geworfen, der eine echte Antwort eines Servers war; war keiner
     * erreichbar, eine Meldung, die das sagt.
     */
    public static <T> T call(Source s, Call<T> c) throws IOException {
        List<String> all = order(s);
        if (all.size() <= 1) return c.run(all.isEmpty() ? s.base : all.get(0));
        IOException answered = null, first = null;
        for (String base : all) {
            try {
                T t = c.run(base);
                works(s, base);
                return t;
            } catch (IOException e) {
                if (first == null) first = e;
                if (answered == null && answered(e)) answered = e;
            }
        }
        if (answered != null) throw answered;
        IOException e = new IOException("Keiner der " + all.size() + " Server antwortet. Stimmen die Adressen, und ist das Gerät online?");
        e.initCause(first);
        throw e;
    }

    /** War der Fehler eine Antwort des Servers (und nicht etwa ein Verbindungsabbruch)? */
    static boolean answered(IOException e) {
        return e instanceof Http.Status;
    }

    /** Merkt sich, dass dieser Server funktioniert. Er wird dann als erster versucht. */
    public static void works(Source s, String base) {
        if (s == null || Text.empty(base)) return;
        String old = active.put(s.id, base);
        if (base.equals(old)) return;
        if (base.equalsIgnoreCase(s.base) && old == null) return;
        List<String> all = order(s);
        all.remove(base);
        StringBuilder alts = new StringBuilder();
        for (String a : all) {
            if (alts.length() > 0) alts.append('\n');
            alts.append(a);
        }
        Store st = store;
        if (st != null && s.id != 0) st.save(s.id, base, alts.toString());
    }

    public static void forget(int sourceId) {
        active.remove(sourceId);
    }

    /** Nur fuer Tests. */
    static void reset() {
        active.clear();
    }
}

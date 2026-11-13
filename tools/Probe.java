import de.eclipseiptv.data.*;
import java.util.*;

/** Entwickler-Werkzeug: liest eine Quelle ein und gibt eine Zusammenfassung aus. Aufruf: Probe <Link> [m3u] */
public class Probe {
    public static void main(String[] a) throws Exception {
        Source s = Source.fromUrl(a[0]);
        if (a.length > 1 && a[1].equals("m3u")) s.kind = Source.M3U;
        System.out.println("Art: " + (s.isXtream() ? "Xtream " + s.base : "M3U"));
        final int[] cats = new int[3], items = new int[3], eps = new int[1], adult = new int[1];
        final List<Item> sample = new ArrayList<>();
        final Map<String,Integer> perCat = new HashMap<>();
        long t = System.currentTimeMillis();
        Importer.run(s, new Importer.Sink() {
            public void category(int kind, String ext, String name, boolean ad) { cats[kind]++; if (cats[kind] <= 3) System.out.println("  Kategorie " + kind + ": " + name + (ad ? " [18+]" : "")); }
            public void item(Item it) { items[it.kind]++; if (it.adult) adult[0]++; if (items[it.kind] == 2 || items[it.kind] == 2000) sample.add(it); perCat.merge(it.kind + "/" + it.cat, 1, Integer::sum); }
            public void episode(String se, Episode e) { eps[0]++; if (eps[0] < 4) System.out.println("  Folge: " + se + " S" + e.season + "E" + e.num + " " + e.title); }
            public void progress(int kind, int count) { }
        });
        System.out.println("Kategorien " + Arrays.toString(cats) + " Eintraege " + Arrays.toString(items) + " Folgen " + eps[0] + " 18+ " + adult[0] + " in " + (System.currentTimeMillis() - t) + " ms");
        for (Item it : sample) System.out.println("  " + it.kind + " #" + it.num + " [" + it.ext + "] " + it.name + " | cat=" + it.cat + " | " + it.year + " | " + it.rating + " | trailer=" + it.trailer + " | epg=" + it.epg + " | ext=" + it.cext + " | url=" + (it.url.isEmpty() ? "-" : "ja") + " | plot=" + (it.plot.length()) + " | backdrop=" + it.backdrop);
        if (s.isXtream()) {
            Xtream x = new Xtream(s);
            Xtream.Account acc = x.account();
            System.out.println("Konto: " + acc.status + " bis " + new Date(acc.expires) + " Verbindungen " + acc.maxConnections);
            Details d = x.vodInfo("1112415");
            System.out.println("Film: " + d.name + " | " + d.released + " | " + d.durationSecs + "s | " + d.rating + " | trailer=" + d.trailer + " | " + d.genre + " | " + d.backdrop + " | plot " + d.plot.length());
            Details se = x.seriesInfo("23838");
            System.out.println("Serie: " + se.name + " Folgen " + se.episodes.size() + " erste: S" + se.episodes.get(0).season + "E" + se.episodes.get(0).num + " " + se.episodes.get(0).title + " " + se.episodes.get(0).durationSecs + "s " + se.episodes.get(0).icon);
            for (Programme p : x.shortEpg("95550", 3)) System.out.println("EPG: " + new Date(p.start) + " " + p.title + " | " + (p.desc.length() > 60 ? p.desc.substring(0, 60) : p.desc));
        }
    }
}

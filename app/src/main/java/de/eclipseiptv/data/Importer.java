package de.eclipseiptv.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest eine Quelle komplett ein und reicht Kategorien und Eintraege an einen
 * Empfaenger weiter (in der App: die Datenbank). Arbeitet im Datenstrom, damit
 * auch sehr grosse Playlists wenig Speicher brauchen.
 */
public final class Importer {
    public interface Sink {
        void category(int kind, String ext, String name, boolean adult);

        void item(Item it);

        void episode(String seriesExt, Episode e);

        /** Fortschritt fuer die Anzeige, z. B. "Filme" und 12000. */
        void progress(int kind, int count);
    }

    private static final Pattern SERIES = Pattern.compile("(?i)^(.*?)[\\s._-]*\\bS(\\d{1,2})\\s*[._ -]?\\s*E(\\d{1,3})\\b(.*)$");
    private static final Pattern VIDEO_EXT = Pattern.compile("(?i)\\.(mp4|mkv|avi|mov|m4v|flv|wmv|mpg|mpeg|webm)(\\?.*)?$");

    private Importer() {
    }

    public static void run(Source s, Sink sink) throws IOException {
        if (s.isXtream()) xtream(s, sink);
        else m3u(s, sink);
    }

    // ---- Xtream ----------------------------------------------------------------

    private static void xtream(Source s, Sink sink) throws IOException {
        Xtream x = new Xtream(s);
        x.account();
        String[] catActions = {"get_live_categories", "get_vod_categories", "get_series_categories"};
        String[] itemActions = {"get_live_streams", "get_vod_streams", "get_series"};
        for (int kind = 0; kind < 3; kind++) {
            sink.progress(kind, 0);
            try (Json j = Http.json(x.api(catActions[kind]), 60000)) {
                if (j.isArray()) {
                    j.beginArray();
                    while (j.hasNext()) {
                        String id = "", name = "";
                        j.beginObject();
                        while (j.hasNext()) {
                            String k = j.nextName();
                            if ("category_id".equals(k)) id = nz(j.nextString());
                            else if ("category_name".equals(k)) name = nz(j.nextString());
                            else j.skipValue();
                        }
                        j.endObject();
                        if (!id.isEmpty()) sink.category(kind, id, clean(name), Text.looksAdult(name));
                    }
                    j.endArray();
                }
            }
            int n = 0;
            try (Json j = Http.json(x.api(itemActions[kind]), 180000)) {
                if (!j.isArray()) continue;
                j.beginArray();
                while (j.hasNext()) {
                    Item it = xtreamItem(j, kind);
                    if (it.ext.isEmpty() || it.name.isEmpty()) continue;
                    it.num = ++n;
                    sink.item(it);
                    if (n % 500 == 0) sink.progress(kind, n);
                }
                j.endArray();
            }
            sink.progress(kind, n);
        }
    }

    private static Item xtreamItem(Json j, int kind) throws IOException {
        Item it = new Item();
        it.kind = kind;
        j.beginObject();
        while (j.hasNext()) {
            String k = j.nextName();
            if (k == null) {
                j.skipValue();
                continue;
            }
            switch (k) {
                case "name": it.name = clean(j.nextString()); break;
                case "stream_id": case "series_id": it.ext = nz(j.nextString()); break;
                case "stream_icon": case "cover": it.icon = nz(j.nextString()); break;
                case "category_id": it.cat = nz(j.nextString()); break;
                case "epg_channel_id": it.epg = nz(j.nextString()); break;
                case "is_adult": it.adult = "1".equals(j.nextString()); break;
                case "tv_archive": it.archive = "1".equals(j.nextString()); break;
                case "added": case "last_modified": it.added = Json.toLong(j.nextString()); break;
                case "container_extension": it.cext = nz(j.nextString()); break;
                case "rating": it.rating = Json.toDouble(j.nextString()); break;
                case "trailer": case "youtube_trailer": it.trailer = Text.youtubeId(j.nextString()); break;
                case "plot": it.plot = nz(j.nextString()); break;
                case "cast": it.cast = nz(j.nextString()); break;
                case "director": it.director = nz(j.nextString()); break;
                case "genre": it.genre = nz(j.nextString()); break;
                case "releaseDate": case "release_date": {
                    String v = nz(j.nextString());
                    if (!v.isEmpty()) it.released = v;
                    break;
                }
                case "backdrop_path":
                    if (j.isArray()) {
                        j.beginArray();
                        while (j.hasNext()) {
                            String v = j.nextString();
                            if (it.backdrop.isEmpty() && v != null) it.backdrop = v;
                        }
                        j.endArray();
                    } else {
                        it.backdrop = nz(j.nextString());
                    }
                    break;
                default: j.skipValue();
            }
        }
        j.endObject();
        if (Text.empty(it.director)) it.director = "";
        it.year = Text.year(it.released, it.name);
        return it;
    }

    // ---- M3U -------------------------------------------------------------------

    private static void m3u(Source s, final Sink sink) throws IOException {
        final Set<String>[] cats = newSets();
        final Map<String, int[]> seriesSeen = new HashMap<>();
        final int[] n = new int[3];
        try (BufferedReader r = Http.reader(s.url, 180000)) {
            M3u.parse(r, new M3u.Handler() {
                @Override
                public void header(Map<String, String> attrs) {
                }

                @Override
                public void entry(M3u.Entry e) {
                    String url = e.url;
                    String low = url.toLowerCase();
                    Matcher ext = VIDEO_EXT.matcher(url);
                    boolean file = ext.find();
                    Matcher sm = SERIES.matcher(e.name);
                    boolean episode = low.contains("/series/") || (file && sm.matches());
                    int kind = episode ? Item.SERIES : (low.contains("/movie/") || file) ? Item.VOD : Item.LIVE;
                    String group = e.group.isEmpty() ? "Sonstige" : clean(e.group);
                    if (cats[kind].add(group)) sink.category(kind, group, group, Text.looksAdult(group));

                    if (kind == Item.SERIES) {
                        String series = e.name;
                        int season = 1, num;
                        sm.reset();
                        if (sm.matches()) {
                            series = sm.group(1).trim();
                            season = Integer.parseInt(sm.group(2));
                            num = Integer.parseInt(sm.group(3));
                            if (series.isEmpty()) series = e.name;
                        } else {
                            num = 0;
                        }
                        series = clean(series);
                        String key = Text.hash(group + "|" + Text.fold(series));
                        int[] count = seriesSeen.get(key);
                        if (count == null) {
                            count = new int[1];
                            seriesSeen.put(key, count);
                            Item it = new Item();
                            it.kind = Item.SERIES;
                            it.ext = key;
                            it.name = series;
                            it.icon = e.logo;
                            it.cat = group;
                            it.num = ++n[Item.SERIES];
                            it.year = Text.year(null, series);
                            it.adult = Text.looksAdult(group);
                            sink.item(it);
                            if (it.num % 200 == 0) sink.progress(Item.SERIES, it.num);
                        }
                        Episode ep = new Episode();
                        ep.id = Text.hash(url);
                        ep.season = season;
                        ep.num = num > 0 ? num : count[0] + 1;
                        count[0]++;
                        ep.title = Text.episodeTitle(e.name, ep.num);
                        ep.url = url;
                        ep.icon = e.logo;
                        sink.episode(key, ep);
                        return;
                    }
                    Item it = new Item();
                    it.kind = kind;
                    it.ext = Text.hash(url);
                    it.name = clean(e.name);
                    it.icon = e.logo;
                    it.cat = group;
                    it.url = url;
                    it.epg = e.tvgId;
                    it.num = ++n[kind];
                    it.adult = Text.looksAdult(group);
                    if (kind == Item.VOD) {
                        it.cext = file ? ext.group(1).toLowerCase() : "";
                        it.year = Text.year(null, it.name);
                    }
                    sink.item(it);
                    if (it.num % 500 == 0) sink.progress(kind, it.num);
                }
            });
        }
        if (n[0] + n[1] + n[2] == 0) throw new IOException("In der Playlist wurden keine Eintraege gefunden.");
        for (int k = 0; k < 3; k++) sink.progress(k, n[k]);
    }

    @SuppressWarnings("unchecked")
    private static Set<String>[] newSets() {
        return new Set[]{new HashSet<String>(), new HashSet<String>(), new HashSet<String>()};
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /** Entfernt Steuerzeichen und doppelte Leerzeichen aus Namen. */
    static String clean(String s) {
        if (s == null) return "";
        return s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}

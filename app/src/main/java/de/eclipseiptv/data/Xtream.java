package de.eclipseiptv.data;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Zugriff auf die Schnittstelle eines Xtream-Zugangs (player_api.php). */
public final class Xtream {
    public final Source s;

    public Xtream(Source s) {
        this.s = s;
    }

    public static final class Account {
        public String status = "", message = "";
        public long expires;
        public int maxConnections;
    }

    private static String enc(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8");
        } catch (Exception e) {
            return v;
        }
    }

    public String api(String action, String... kv) {
        StringBuilder b = new StringBuilder(s.base).append("/player_api.php?username=").append(enc(s.user))
                .append("&password=").append(enc(s.pass));
        if (action != null) b.append("&action=").append(action);
        for (int i = 0; i + 1 < kv.length; i += 2) b.append('&').append(kv[i]).append('=').append(enc(kv[i + 1]));
        return b.toString();
    }

    private String stream(String type, String id, String ext) {
        return s.base + "/" + type + "/" + enc(s.user) + "/" + enc(s.pass) + "/" + id + (Text.empty(ext) ? "" : "." + ext);
    }

    public String liveUrl(String id, boolean hls) {
        return stream("live", id, hls ? "m3u8" : "ts");
    }

    public String movieUrl(String id, String ext) {
        return stream("movie", id, Text.empty(ext) ? "mp4" : ext);
    }

    public String episodeUrl(String id, String ext) {
        return stream("series", id, Text.empty(ext) ? "mp4" : ext);
    }

    /** Prueft die Zugangsdaten. Wirft eine verstaendliche Meldung, wenn sie nicht stimmen. */
    public Account account() throws IOException {
        Object root;
        try (Json j = Http.json(api(null), 30000)) {
            root = j.readValue();
        }
        Map<String, Object> u = Json.obj(root, "user_info");
        if (u.isEmpty()) throw new IOException("Der Server hat die Anmeldung nicht beantwortet.");
        Account a = new Account();
        a.status = Json.str(u, "status");
        a.message = Json.str(u, "message");
        a.expires = Json.num(u, "exp_date") * 1000L;
        a.maxConnections = (int) Json.num(u, "max_connections");
        if ("0".equals(Json.str(u, "auth"))) throw new IOException("Benutzername oder Passwort stimmen nicht.");
        if (!a.status.isEmpty() && !"Active".equalsIgnoreCase(a.status)) {
            throw new IOException("Der Zugang ist nicht aktiv (Status: " + a.status + ").");
        }
        return a;
    }

    public Details vodInfo(String id) throws IOException {
        Object root;
        try (Json j = Http.json(api("get_vod_info", "vod_id", id), 30000)) {
            root = j.readValue();
        }
        Map<String, Object> i = Json.obj(root, "info");
        Details d = new Details();
        fill(d, i);
        // Manche Anbieter schreiben Minuten in das Sekunden-Feld; "runtime" ist verlaesslicher.
        int secs = (int) Json.num(i, "duration_secs"), runtime = (int) Json.num(i, "runtime");
        if (runtime <= 0) runtime = (int) Json.num(i, "episode_run_time");
        if (secs >= 600) d.durationSecs = secs;
        else if (runtime > 0) d.durationSecs = runtime * 60;
        else d.durationSecs = secs * 60;
        return d;
    }

    private static void fill(Details d, Map<String, Object> i) {
        d.name = Json.str(i, "name");
        d.cover = first(Json.str(i, "cover_big"), Json.str(i, "movie_image"), Json.str(i, "cover"));
        d.plot = first(Json.str(i, "plot"), Json.str(i, "description"));
        d.genre = Json.str(i, "genre");
        d.cast = first(Json.str(i, "cast"), Json.str(i, "actors"));
        d.director = Json.str(i, "director");
        d.backdrop = Json.str(i, "backdrop_path");
        d.trailer = Text.youtubeId(Json.str(i, "youtube_trailer"));
        d.released = first(Json.str(i, "releasedate"), Json.str(i, "releaseDate"), Json.str(i, "release_date"));
        d.rating = Json.toDouble(Json.str(i, "rating"));
    }

    private static String first(String... c) {
        for (String x : c) if (!Text.empty(x)) return x;
        return "";
    }

    public Details seriesInfo(String id) throws IOException {
        Object root;
        try (Json j = Http.json(api("get_series_info", "series_id", id), 45000)) {
            root = j.readValue();
        }
        Details d = new Details();
        fill(d, Json.obj(root, "info"));
        Object eps = Json.obj(root).get("episodes");
        List<Object> groups = new ArrayList<>();
        if (eps instanceof Map) groups.addAll(Json.obj(eps).values());
        else groups.addAll(Json.arr(eps));
        for (Object g : groups) {
            for (Object o : Json.arr(g)) {
                Map<String, Object> e = Json.obj(o), info = Json.obj(o, "info");
                Episode ep = new Episode();
                ep.id = Json.str(e, "id");
                if (ep.id.isEmpty()) continue;
                ep.season = (int) Json.num(e, "season");
                ep.num = (int) Json.num(e, "episode_num");
                ep.title = Text.episodeTitle(Json.str(e, "title"), ep.num);
                ep.cext = Json.str(e, "container_extension");
                ep.icon = Json.str(info, "movie_image");
                ep.plot = Json.str(info, "plot");
                ep.durationSecs = (int) Json.num(info, "duration_secs");
                ep.rating = Json.toDouble(Json.str(info, "rating"));
                ep.released = first(Json.str(info, "air_date"), Json.str(info, "releasedate"));
                d.episodes.add(ep);
            }
        }
        Collections.sort(d.episodes, new Comparator<Episode>() {
            @Override
            public int compare(Episode a, Episode b) {
                return a.season != b.season ? Integer.compare(a.season, b.season) : Integer.compare(a.num, b.num);
            }
        });
        return d;
    }

    /** Die naechsten Sendungen eines Senders. Leer, wenn der Anbieter kein Programm liefert. */
    public List<Programme> shortEpg(String streamId, int limit) throws IOException {
        Object root;
        try (Json j = Http.json(api("get_short_epg", "stream_id", streamId, "limit", String.valueOf(limit)), 15000)) {
            root = j.readValue();
        }
        List<Programme> out = new ArrayList<>();
        for (Object o : Json.arr(root, "epg_listings")) {
            Programme p = new Programme();
            p.title = Text.base64(Json.str(o, "title"));
            p.desc = Text.base64(Json.str(o, "description"));
            p.start = Json.num(o, "start_timestamp") * 1000L;
            p.end = Json.num(o, "stop_timestamp") * 1000L;
            if (p.end > p.start && !p.title.isEmpty()) out.add(p);
        }
        return out;
    }
}

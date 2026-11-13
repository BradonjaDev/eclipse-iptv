package de.eclipseiptv.data;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/** Eine eingebundene Playlist: entweder ein M3U-Link oder ein Xtream-Zugang. */
public final class Source {
    public static final int M3U = 0, XTREAM = 1;

    public int id, kind;
    public String name = "", url = "", base = "", user = "", pass = "";
    public long updated;
    public int nLive, nVod, nSeries;

    public boolean isXtream() {
        return kind == XTREAM;
    }

    /**
     * Erkennt aus einem eingefuegten Link, um was es sich handelt. Links der Form
     * .../get.php?username=..&password=.. gehoeren zu einem Xtream-Zugang; fuer die
     * holt die App Kategorien, Beschreibungen und Programm direkt vom Server.
     */
    public static Source fromUrl(String input) {
        String u = input == null ? "" : input.trim();
        if (u.isEmpty()) throw new IllegalArgumentException("Bitte einen Link eingeben.");
        if (!u.matches("(?i)^https?://.*")) u = "http://" + u;
        Source s = new Source();
        s.url = u;
        try {
            URI uri = new URI(u.replace(" ", "%20"));
            String host = uri.getHost();
            if (host == null) throw new IllegalArgumentException("Der Link ist unvollstaendig.");
            s.name = host;
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
            Map<String, String> q = query(uri.getRawQuery());
            String user = q.get("username"), pass = q.get("password");
            if ((path.endsWith("get.php") || path.endsWith("player_api.php") || path.endsWith("xmltv.php"))
                    && user != null && pass != null && !user.isEmpty()) {
                s.kind = XTREAM;
                s.user = user;
                s.pass = pass;
                s.base = uri.getScheme() + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Der Link ist ungueltig.");
        }
        return s;
    }

    public static Source xtream(String server, String user, String pass) {
        String b = server == null ? "" : server.trim();
        if (b.isEmpty() || user == null || user.trim().isEmpty()) throw new IllegalArgumentException("Server und Benutzername fehlen.");
        if (!b.matches("(?i)^https?://.*")) b = "http://" + b;
        try {
            URI uri = new URI(b);
            if (uri.getHost() == null) throw new IllegalArgumentException("Die Server-Adresse ist ungueltig.");
            Source s = new Source();
            s.kind = XTREAM;
            s.base = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            s.user = user.trim();
            s.pass = pass == null ? "" : pass.trim();
            s.name = uri.getHost();
            s.url = s.base;
            return s;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Die Server-Adresse ist ungueltig.");
        }
    }

    static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            try {
                m.put(URLDecoder.decode(part.substring(0, i), "UTF-8").toLowerCase(), URLDecoder.decode(part.substring(i + 1), "UTF-8"));
            } catch (Exception ignored) {
            }
        }
        return m;
    }
}

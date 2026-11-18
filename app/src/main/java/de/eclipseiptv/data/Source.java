package de.eclipseiptv.data;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Eine eingebundene Playlist: entweder ein M3U-Link oder ein Xtream-Zugang. */
public final class Source {
    public static final int M3U = 0, XTREAM = 1;

    public int id, kind;
    public String name = "", url = "", base = "", user = "", pass = "";
    /** Ausweich-Server eines Xtream-Zugangs, eine Adresse pro Zeile (nur scheme://host:port). */
    public String alts = "";
    public long updated;
    public int nLive, nVod, nSeries;

    public boolean isXtream() {
        return kind == XTREAM;
    }

    /** Alle Server des Zugangs: der Haupt-Server zuerst, danach die Ausweich-Server, ohne Doppelte. */
    public List<String> servers() {
        Set<String> out = new LinkedHashSet<>();
        if (!Text.empty(base)) out.add(base);
        if (alts != null) {
            for (String a : alts.split("\n")) {
                a = a.trim();
                if (!a.isEmpty()) out.add(a);
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Uebernimmt Ausweich-Server aus einer Eingabe. Getrennt wird an Zeilen, Kommas und
     * Leerzeichen; erlaubt sind nackte Hosts, Host:Port und ganze Links. Ungueltige
     * Eintraege und der Haupt-Server selbst werden uebergangen.
     */
    public void setAlternates(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text != null) {
            for (String part : text.split("[\\s,;]+")) {
                if (part.isEmpty()) continue;
                try {
                    String b = server(part);
                    if (!b.equalsIgnoreCase(base)) out.add(b);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        StringBuilder b = new StringBuilder();
        for (String a : out) {
            if (b.length() > 0) b.append('\n');
            b.append(a);
        }
        alts = b.toString();
    }

    /** Macht aus einer Eingabe die Server-Adresse scheme://host[:port]. */
    public static String server(String input) {
        String b = input == null ? "" : input.trim();
        if (b.isEmpty()) throw new IllegalArgumentException("Die Server-Adresse fehlt.");
        if (!b.matches("(?i)^https?://.*")) b = "http://" + b;
        try {
            URI uri = new URI(b.replace(" ", "%20"));
            String host = uri.getHost();
            if (host == null) throw new IllegalArgumentException("Die Server-Adresse ist ungueltig.");
            return uri.getScheme().toLowerCase() + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Die Server-Adresse ist ungueltig.");
        }
    }

    /**
     * Wie fromUrl, aber mit mehreren Zeilen: Die erste Zeile ist der Link, jede weitere
     * ein Ausweich-Server. Ausweich-Server gibt es nur bei Xtream-Zugaengen.
     */
    public static Source fromInput(String text) {
        String t = text == null ? "" : text.trim();
        String[] lines = t.split("\\s*[\\r\\n]+\\s*");
        Source s = fromUrl(lines[0]);
        StringBuilder rest = new StringBuilder();
        for (int i = 1; i < lines.length; i++) rest.append(lines[i]).append('\n');
        if (rest.toString().trim().isEmpty()) return s;
        if (!s.isXtream()) throw new IllegalArgumentException("Ausweich-Server gibt es nur bei Xtream-Zugängen (Links mit get.php?username=…&password=…).");
        s.setAlternates(rest.toString());
        return s;
    }

    /** Dieselbe Quelle? Bei Xtream: gleicher Benutzer und mindestens ein gemeinsamer Server. */
    public boolean sameAs(Source o) {
        if (o == null || o.kind != kind) return false;
        if (!isXtream()) return url.equals(o.url);
        if (!user.equals(o.user)) return false;
        for (String a : servers()) for (String b : o.servers()) if (a.equalsIgnoreCase(b)) return true;
        return false;
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
        return xtream(server, user, pass, null);
    }

    public static Source xtream(String server, String user, String pass, String alts) {
        String b = server == null ? "" : server.trim();
        if (b.isEmpty() || user == null || user.trim().isEmpty()) throw new IllegalArgumentException("Server und Benutzername fehlen.");
        Source s = new Source();
        s.kind = XTREAM;
        s.base = server(b);
        s.user = user.trim();
        s.pass = pass == null ? "" : pass.trim();
        s.name = URI.create(s.base).getHost();
        s.url = s.base;
        s.setAlternates(alts);
        return s;
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

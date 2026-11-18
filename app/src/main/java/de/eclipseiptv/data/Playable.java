package de.eclipseiptv.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Etwas Abspielbares: ein Sender, ein Film oder eine Folge. */
public final class Playable {
    public String title = "", subtitle = "", url = "", altUrl = "", icon = "";
    public boolean live;
    /** Der Sender (fuer Programm und Favoriten); bei Filmen der Film, bei Folgen die Serie. */
    public Item item;
    public int markKind;
    public String markExt = "", markParent = "", markName = "", markIcon = "";
    public long startPos;
    /** Bei Xtream: die Quelle und der Server, auf den url und altUrl gerade zeigen. */
    public Source source;
    public String server = "";
    /** Server, die fuer diesen Eintrag schon gescheitert sind. */
    private final Set<String> tried = new HashSet<>();

    private void xtream(Source s) {
        if (s == null || !s.isXtream()) return;
        source = s;
        server = Servers.current(s);
    }

    private static String swap(String url, String from, String to) {
        if (Text.empty(url) || !url.startsWith(from + "/")) return url;
        return to + url.substring(from.length());
    }

    private void moveTo(String to) {
        url = swap(url, server, to);
        altUrl = swap(altUrl, server, to);
        server = to;
    }

    /** Folgt vor dem Start dem Server, der zuletzt funktioniert hat. */
    public void fresh() {
        if (source == null || Text.empty(server)) return;
        String now = Servers.current(source);
        if (!now.equals(server) && !tried.contains(now)) moveTo(now);
    }

    /** Stellt auf den naechsten Server um, der noch nicht versucht wurde. False, wenn keiner mehr uebrig ist. */
    public boolean nextServer() {
        if (source == null || Text.empty(server)) return false;
        tried.add(server);
        List<String> all = Servers.order(source);
        for (String b : all) {
            if (!tried.contains(b)) {
                moveTo(b);
                return true;
            }
        }
        return false;
    }

    /** Der Eintrag laeuft: Server merken und beim naechsten Fehler wieder alle versuchen. */
    public void works() {
        if (source != null && !Text.empty(server)) Servers.works(source, server);
        tried.clear();
    }

    /** Der Rechnername des aktuellen Servers (fuer Meldungen). */
    public String host() {
        if (Text.empty(server)) return "";
        try {
            String h = java.net.URI.create(server).getHost();
            return h == null ? server : h;
        } catch (Exception e) {
            return server;
        }
    }

    public static Playable live(Source s, Item it, boolean hls) {
        Playable p = new Playable();
        p.live = true;
        p.item = it;
        p.title = it.name;
        p.icon = it.icon;
        if (s.isXtream()) {
            Xtream x = new Xtream(s);
            p.xtream(s);
            p.url = x.liveUrl(it.ext, hls);
            p.altUrl = x.liveUrl(it.ext, !hls);
        } else {
            p.url = it.url;
        }
        p.markKind = Item.LIVE;
        p.markExt = it.ext;
        p.markName = it.name;
        p.markIcon = it.icon;
        return p;
    }

    public static Playable movie(Source s, Item it) {
        Playable p = new Playable();
        p.item = it;
        p.title = it.name;
        p.icon = it.icon;
        p.xtream(s);
        p.url = s.isXtream() ? new Xtream(s).movieUrl(it.ext, it.cext) : it.url;
        p.markKind = Item.VOD;
        p.markExt = it.ext;
        p.markName = it.name;
        p.markIcon = it.icon;
        return p;
    }

    public static Playable episode(Source s, Item series, Episode e) {
        Playable p = new Playable();
        p.item = series;
        p.title = series.name;
        p.subtitle = "Staffel " + e.season + "  ·  Folge " + e.num + (e.title.startsWith("Folge ") ? "" : "  ·  " + e.title);
        p.icon = series.icon;
        p.xtream(s);
        p.url = s.isXtream() ? new Xtream(s).episodeUrl(e.id, e.cext) : e.url;
        p.markKind = Item.EPISODE;
        p.markExt = e.id;
        p.markParent = series.ext;
        p.markName = series.name + "  ·  S" + e.season + " F" + e.num;
        p.markIcon = series.icon;
        return p;
    }
}

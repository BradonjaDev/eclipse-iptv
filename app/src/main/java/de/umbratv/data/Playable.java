package de.umbratv.data;

/** Etwas Abspielbares: ein Sender, ein Film oder eine Folge. */
public final class Playable {
    public String title = "", subtitle = "", url = "", altUrl = "", icon = "";
    public boolean live;
    /** Der Sender (fuer Programm und Favoriten); bei Filmen der Film, bei Folgen die Serie. */
    public Item item;
    public int markKind;
    public String markExt = "", markParent = "", markName = "", markIcon = "";
    public long startPos;

    public static Playable live(Source s, Item it, boolean hls) {
        Playable p = new Playable();
        p.live = true;
        p.item = it;
        p.title = it.name;
        p.icon = it.icon;
        if (s.isXtream()) {
            Xtream x = new Xtream(s);
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
        p.url = s.isXtream() ? new Xtream(s).episodeUrl(e.id, e.cext) : e.url;
        p.markKind = Item.EPISODE;
        p.markExt = e.id;
        p.markParent = series.ext;
        p.markName = series.name + "  ·  S" + e.season + " F" + e.num;
        p.markIcon = series.icon;
        return p;
    }
}

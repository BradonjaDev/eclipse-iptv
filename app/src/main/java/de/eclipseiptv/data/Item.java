package de.eclipseiptv.data;

/** Ein Eintrag der Playlist: Sender, Film, Serie oder Folge. */
public final class Item {
    public static final int LIVE = 0, VOD = 1, SERIES = 2, EPISODE = 3;

    public long id;
    public int source, kind, num, year;
    /** Kennung beim Anbieter (bleibt beim Aktualisieren gleich). */
    public String ext = "";
    public String name = "", icon = "", cat = "", url = "", cext = "", epg = "";
    public String plot = "", genre = "", cast = "", director = "", backdrop = "", trailer = "", released = "";
    public double rating;
    public boolean adult, archive;
    public long added;

    public boolean isLive() {
        return kind == LIVE;
    }
}

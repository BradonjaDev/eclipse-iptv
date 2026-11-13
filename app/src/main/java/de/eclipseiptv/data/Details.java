package de.eclipseiptv.data;

import java.util.ArrayList;
import java.util.List;

/** Zusatzinfos zu einem Film oder einer Serie, die erst beim Oeffnen geladen werden. */
public final class Details {
    public String name = "", cover = "", plot = "", genre = "", cast = "", director = "", backdrop = "", trailer = "", released = "";
    public int durationSecs;
    public double rating;
    public final List<Episode> episodes = new ArrayList<>();
}

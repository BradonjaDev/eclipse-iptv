package de.eclipseiptv.data;

import java.util.ArrayList;
import java.util.List;

/** Die Abspielliste, die an den Player uebergeben wird (zu gross fuer einen Intent). */
public final class PlayQueue {
    public static List<Playable> list = new ArrayList<>();
    public static int index;
    public static Source source;
    public static String listName = "";

    private PlayQueue() {
    }

    public static void set(Source s, List<Playable> l, int i, String name) {
        source = s;
        list = l;
        index = Math.max(0, Math.min(i, l.size() - 1));
        listName = name == null ? "" : name;
    }
}

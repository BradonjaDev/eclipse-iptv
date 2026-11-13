package de.eclipseiptv.data;

/** Eine Sendung aus dem Programmfuehrer. Zeiten in Millisekunden. */
public final class Programme {
    public String title = "", desc = "";
    public long start, end;

    public boolean runsAt(long t) {
        return start <= t && t < end;
    }
}

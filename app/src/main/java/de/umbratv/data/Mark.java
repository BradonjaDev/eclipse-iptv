package de.umbratv.data;

/** Merker zu einem Eintrag: Favorit, Abspielposition, zuletzt gesehen. */
public final class Mark {
    public int source, kind;
    public String ext = "", name = "", icon = "", parent = "";
    public boolean fav;
    /** Position und Laenge in Millisekunden. */
    public long pos, dur, last;

    public int percent() {
        return dur > 0 ? (int) Math.min(100, pos * 100 / dur) : 0;
    }

    /** Angefangen, aber noch nicht zu Ende gesehen. */
    public boolean resumable() {
        return pos > 30000 && (dur <= 0 || pos < dur - Math.max(60000, dur / 20));
    }
}

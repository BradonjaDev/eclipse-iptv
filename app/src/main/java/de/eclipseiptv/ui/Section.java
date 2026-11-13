package de.eclipseiptv.ui;

/** Ein Bereich der App (Start, Sender, Filme, …). */
public interface Section {
    /** Wird aufgerufen, wenn der Bereich sichtbar wird oder sich Daten geaendert haben. */
    void refresh();
}

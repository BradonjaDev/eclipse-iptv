package de.eclipseiptv.ui;

import java.util.List;

import de.eclipseiptv.data.Db;
import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.Mark;
import de.eclipseiptv.data.Prefs;
import de.eclipseiptv.data.Source;

/** Was die Bereiche von der Haupt-Ansicht brauchen. */
public interface Host {
    Source source();

    Prefs prefs();

    Db db();

    /** Spielt einen Sender; queue ist die Liste, in der umgeschaltet wird. */
    void playLive(List<Item> queue, int index);

    /** Oeffnet die Detailseite eines Films oder einer Serie. */
    void open(Item item);

    /** Oeffnet etwas Angefangenes wieder. */
    void open(Mark mark);

    void openSetup();

    void refreshNow();

    /** Alle Bereiche neu aufbauen, z. B. nach geaenderten Einstellungen. */
    void invalidate();
}

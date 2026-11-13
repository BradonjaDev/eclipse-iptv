package de.umbratv.ui;

import java.util.List;

import de.umbratv.data.Db;
import de.umbratv.data.Item;
import de.umbratv.data.Mark;
import de.umbratv.data.Prefs;
import de.umbratv.data.Source;

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

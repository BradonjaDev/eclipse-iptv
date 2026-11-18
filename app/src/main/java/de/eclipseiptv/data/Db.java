package de.eclipseiptv.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Die lokale Datenbank: Quellen, Kategorien, Eintraege und Merker. */
public final class Db extends SQLiteOpenHelper {
    private static Db instance;
    /** Merker werden im Hintergrund geschrieben, damit die Bedienung nie auf ein laufendes Einlesen warten muss. */
    private final java.util.concurrent.ExecutorService writer = java.util.concurrent.Executors.newSingleThreadExecutor();

    public static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "eclipse.db", null, 2);
        setWriteAheadLoggingEnabled(true);
        // Hat ein Ausweich-Server funktioniert, wird er zum Haupt-Server der Quelle.
        Servers.setStore(new Servers.Store() {
            @Override
            public void save(final int sourceId, final String base, final String alts) {
                writer.execute(new Runnable() {
                    @Override
                    public void run() {
                        ContentValues v = new ContentValues();
                        v.put("base", base);
                        v.put("alts", alts);
                        getWritableDatabase().update("source", v, "id=? AND kind=?", new String[]{String.valueOf(sourceId), String.valueOf(Source.XTREAM)});
                    }
                });
            }
        });
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE source(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, kind INT, url TEXT, base TEXT,"
                + " user TEXT, pass TEXT, updated INT DEFAULT 0, n0 INT DEFAULT 0, n1 INT DEFAULT 0, n2 INT DEFAULT 0, alts TEXT DEFAULT '')");
        d.execSQL("CREATE TABLE category(source INT, kind INT, ext TEXT, name TEXT, sort INT, hidden INT DEFAULT 0,"
                + " adult INT DEFAULT 0, cnt INT DEFAULT 0, PRIMARY KEY(source,kind,ext))");
        d.execSQL("CREATE TABLE item(id INTEGER PRIMARY KEY, source INT, kind INT, ext TEXT, name TEXT, sname TEXT,"
                + " icon TEXT, cat TEXT, num INT, url TEXT, cext TEXT, epg TEXT, rating REAL, year INT, adult INT,"
                + " archive INT, added INT, trailer TEXT, plot TEXT, genre TEXT, cast_ TEXT, director TEXT,"
                + " backdrop TEXT, released TEXT)");
        d.execSQL("CREATE INDEX item_cat ON item(source,kind,cat,num)");
        d.execSQL("CREATE INDEX item_ext ON item(source,kind,ext)");
        d.execSQL("CREATE INDEX item_added ON item(source,kind,added)");
        d.execSQL("CREATE TABLE episode(source INT, series TEXT, season INT, num INT, ext TEXT, title TEXT, url TEXT, icon TEXT)");
        d.execSQL("CREATE INDEX episode_series ON episode(source,series,season,num)");
        d.execSQL("CREATE TABLE mark(source INT, kind INT, ext TEXT, name TEXT, icon TEXT, parent TEXT DEFAULT '',"
                + " fav INT DEFAULT 0, pos INT DEFAULT 0, dur INT DEFAULT 0, last INT DEFAULT 0, PRIMARY KEY(source,kind,ext))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int from, int to) {
        if (from < 2) d.execSQL("ALTER TABLE source ADD COLUMN alts TEXT DEFAULT ''");
    }

    // ---- Quellen ---------------------------------------------------------------

    private static Source readSource(Cursor c) {
        Source s = new Source();
        s.id = c.getInt(0);
        s.name = c.getString(1);
        s.kind = c.getInt(2);
        s.url = c.getString(3);
        s.base = c.getString(4);
        s.user = c.getString(5);
        s.pass = c.getString(6);
        s.updated = c.getLong(7);
        s.nLive = c.getInt(8);
        s.nVod = c.getInt(9);
        s.nSeries = c.getInt(10);
        s.alts = c.isNull(11) ? "" : c.getString(11);
        return s;
    }

    public List<Source> sources() {
        List<Source> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,kind,url,base,user,pass,updated,n0,n1,n2,alts FROM source ORDER BY id", null)) {
            while (c.moveToNext()) out.add(readSource(c));
        }
        return out;
    }

    public Source source(int id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,kind,url,base,user,pass,updated,n0,n1,n2,alts FROM source WHERE id=?", new String[]{String.valueOf(id)})) {
            return c.moveToNext() ? readSource(c) : null;
        }
    }

    public void deleteSource(int id) {
        SQLiteDatabase d = getWritableDatabase();
        String[] a = {String.valueOf(id)};
        d.beginTransaction();
        try {
            for (String t : new String[]{"category", "item", "episode", "mark"}) d.delete(t, "source=?", a);
            d.delete("source", "id=?", a);
            d.setTransactionSuccessful();
        } finally {
            d.endTransaction();
        }
        Servers.forget(id);
    }

    public void renameSource(int id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        getWritableDatabase().update("source", v, "id=?", new String[]{String.valueOf(id)});
    }

    // ---- Einlesen --------------------------------------------------------------

    /**
     * Liest eine Quelle ein. Alles passiert in einer Transaktion: geht etwas schief,
     * bleibt der bisherige Stand unveraendert. Neue Quellen (id 0) werden dabei angelegt.
     */
    public int importSource(final Source s, final Importer.Sink progress) throws IOException {
        final SQLiteDatabase d = getWritableDatabase();
        d.beginTransactionNonExclusive();
        try {
            if (s.id == 0) {
                ContentValues v = new ContentValues();
                v.put("name", s.name);
                v.put("kind", s.kind);
                v.put("url", s.url);
                v.put("base", s.base);
                v.put("user", s.user);
                v.put("pass", s.pass);
                v.put("alts", s.alts);
                s.id = (int) d.insertOrThrow("source", null, v);
            }
            final String sid = String.valueOf(s.id);
            final Set<String> hidden = new HashSet<>();
            try (Cursor c = d.rawQuery("SELECT kind,ext FROM category WHERE source=? AND hidden=1", new String[]{sid})) {
                while (c.moveToNext()) hidden.add(c.getInt(0) + "/" + c.getString(1));
            }
            for (String t : new String[]{"category", "item", "episode"}) d.delete(t, "source=?", new String[]{sid});

            final SQLiteStatement insCat = d.compileStatement("INSERT OR IGNORE INTO category(source,kind,ext,name,sort,hidden,adult) VALUES(?,?,?,?,?,?,?)");
            final SQLiteStatement insItem = d.compileStatement("INSERT INTO item(source,kind,ext,name,sname,icon,cat,num,url,cext,epg,rating,year,adult,"
                    + "archive,added,trailer,plot,genre,cast_,director,backdrop,released) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            final SQLiteStatement insEp = d.compileStatement("INSERT INTO episode(source,series,season,num,ext,title,url,icon) VALUES(?,?,?,?,?,?,?,?)");
            final int[] sort = new int[1];

            Importer.run(s, new Importer.Sink() {
                @Override
                public void category(int kind, String ext, String name, boolean adult) {
                    insCat.bindLong(1, s.id);
                    insCat.bindLong(2, kind);
                    insCat.bindString(3, ext);
                    insCat.bindString(4, name);
                    insCat.bindLong(5, ++sort[0]);
                    insCat.bindLong(6, hidden.contains(kind + "/" + ext) ? 1 : 0);
                    insCat.bindLong(7, adult ? 1 : 0);
                    insCat.executeInsert();
                }

                @Override
                public void item(Item it) {
                    insItem.bindLong(1, s.id);
                    insItem.bindLong(2, it.kind);
                    insItem.bindString(3, it.ext);
                    insItem.bindString(4, it.name);
                    insItem.bindString(5, Text.fold(it.name));
                    insItem.bindString(6, it.icon);
                    insItem.bindString(7, it.cat);
                    insItem.bindLong(8, it.num);
                    insItem.bindString(9, it.url);
                    insItem.bindString(10, it.cext);
                    insItem.bindString(11, it.epg);
                    insItem.bindDouble(12, it.rating);
                    insItem.bindLong(13, it.year);
                    insItem.bindLong(14, it.adult ? 1 : 0);
                    insItem.bindLong(15, it.archive ? 1 : 0);
                    insItem.bindLong(16, it.added);
                    insItem.bindString(17, it.trailer);
                    insItem.bindString(18, it.plot);
                    insItem.bindString(19, it.genre);
                    insItem.bindString(20, it.cast);
                    insItem.bindString(21, it.director);
                    insItem.bindString(22, it.backdrop);
                    insItem.bindString(23, it.released);
                    insItem.executeInsert();
                }

                @Override
                public void episode(String series, Episode e) {
                    insEp.bindLong(1, s.id);
                    insEp.bindString(2, series);
                    insEp.bindLong(3, e.season);
                    insEp.bindLong(4, e.num);
                    insEp.bindString(5, e.id);
                    insEp.bindString(6, e.title);
                    insEp.bindString(7, e.url);
                    insEp.bindString(8, e.icon);
                    insEp.executeInsert();
                }

                @Override
                public void progress(int kind, int count) {
                    if (progress != null) progress.progress(kind, count);
                }
            });

            String[] a = {sid};
            // Eintraege, deren Kategorie der Anbieter nicht nennt, landen unter "Sonstige".
            d.execSQL("UPDATE item SET cat='' WHERE source=? AND NOT EXISTS (SELECT 1 FROM category c WHERE c.source=item.source"
                    + " AND c.kind=item.kind AND c.ext=item.cat)", a);
            d.execSQL("INSERT OR IGNORE INTO category(source,kind,ext,name,sort) SELECT DISTINCT source,kind,'','Sonstige',999999"
                    + " FROM item WHERE source=? AND cat=''", a);
            // Erwachsenen-Kennzeichnung in beide Richtungen angleichen.
            d.execSQL("UPDATE category SET adult=1 WHERE source=? AND adult=0 AND EXISTS (SELECT 1 FROM item i WHERE i.source=category.source"
                    + " AND i.kind=category.kind AND i.cat=category.ext AND i.adult=1)"
                    + " AND NOT EXISTS (SELECT 1 FROM item i WHERE i.source=category.source AND i.kind=category.kind"
                    + " AND i.cat=category.ext AND i.adult=0)", a);
            d.execSQL("UPDATE item SET adult=1 WHERE source=? AND adult=0 AND EXISTS (SELECT 1 FROM category c WHERE c.source=item.source"
                    + " AND c.kind=item.kind AND c.ext=item.cat AND c.adult=1)", a);
            d.execSQL("UPDATE category SET cnt=(SELECT COUNT(*) FROM item i WHERE i.source=category.source AND i.kind=category.kind"
                    + " AND i.cat=category.ext) WHERE source=?", a);
            d.execSQL("UPDATE source SET updated=" + System.currentTimeMillis()
                    + ", n0=(SELECT COUNT(*) FROM item WHERE source=" + sid + " AND kind=0)"
                    + ", n1=(SELECT COUNT(*) FROM item WHERE source=" + sid + " AND kind=1)"
                    + ", n2=(SELECT COUNT(*) FROM item WHERE source=" + sid + " AND kind=2) WHERE id=" + sid);
            if (s.isXtream()) {
                // Zugangsdaten und Server uebernehmen; der zuletzt funktionierende Server steht vorn.
                List<String> order = Servers.order(s);
                ContentValues v = new ContentValues();
                v.put("url", s.url);
                v.put("pass", s.pass);
                if (!order.isEmpty()) {
                    v.put("base", order.get(0));
                    StringBuilder alts = new StringBuilder();
                    for (int i = 1; i < order.size(); i++) alts.append(i > 1 ? "\n" : "").append(order.get(i));
                    v.put("alts", alts.toString());
                }
                d.update("source", v, "id=?", a);
            }
            d.setTransactionSuccessful();
            return s.id;
        } catch (RuntimeException e) {
            throw new IOException("Die Playlist konnte nicht gespeichert werden: " + e.getMessage());
        } finally {
            d.endTransaction();
        }
    }

    // ---- Kategorien ------------------------------------------------------------

    public List<Category> categories(int source, int kind, boolean adult) {
        List<Category> out = new ArrayList<>();
        String sql = "SELECT ext,name,cnt,adult FROM category WHERE source=? AND kind=? AND hidden=0 AND cnt>0"
                + (adult ? "" : " AND adult=0") + " ORDER BY sort";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(source), String.valueOf(kind)})) {
            while (c.moveToNext()) {
                Category k = new Category(kind, c.getString(0), c.getString(1));
                k.count = c.getInt(2);
                k.adult = c.getInt(3) != 0;
                out.add(k);
            }
        }
        return out;
    }

    public void hideCategory(final int source, final int kind, final String ext, final boolean hidden) {
        ContentValues v = new ContentValues();
        v.put("hidden", hidden ? 1 : 0);
        getWritableDatabase().update("category", v, "source=? AND kind=? AND ext=?", new String[]{String.valueOf(source), String.valueOf(kind), ext});
    }

    public int hiddenCount(int source) {
        return (int) count("SELECT COUNT(*) FROM category WHERE source=? AND hidden=1", String.valueOf(source));
    }

    public void unhideAll(int source) {
        getWritableDatabase().execSQL("UPDATE category SET hidden=0 WHERE source=" + source);
    }

    private long count(String sql, String... args) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            return c.moveToNext() ? c.getLong(0) : 0;
        }
    }

    // ---- Eintraege -------------------------------------------------------------

    private static final String LIGHT = "i.id,i.source,i.kind,i.ext,i.name,i.icon,i.cat,i.num,i.url,i.cext,i.epg,i.rating,i.year,i.adult,i.archive,i.trailer,i.added";
    private static final String FULL = LIGHT + ",i.plot,i.genre,i.cast_,i.director,i.backdrop,i.released";

    private static Item read(Cursor c, boolean full) {
        Item it = new Item();
        it.id = c.getLong(0);
        it.source = c.getInt(1);
        it.kind = c.getInt(2);
        it.ext = c.getString(3);
        it.name = c.getString(4);
        it.icon = c.getString(5);
        it.cat = c.getString(6);
        it.num = c.getInt(7);
        it.url = c.getString(8);
        it.cext = c.getString(9);
        it.epg = c.getString(10);
        it.rating = c.getDouble(11);
        it.year = c.getInt(12);
        it.adult = c.getInt(13) != 0;
        it.archive = c.getInt(14) != 0;
        it.trailer = c.getString(15);
        it.added = c.getLong(16);
        if (full) {
            it.plot = c.getString(17);
            it.genre = c.getString(18);
            it.cast = c.getString(19);
            it.director = c.getString(20);
            it.backdrop = c.getString(21);
            it.released = c.getString(22);
        }
        return it;
    }

    private List<Item> list(String sql, String... args) {
        List<Item> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) out.add(read(c, false));
        }
        return out;
    }

    /** Bedingung, die ausgeblendete Kategorien und (falls gewuenscht) Erwachsenen-Inhalte ausschliesst. */
    private static String visible(boolean adult) {
        return (adult ? "" : " AND i.adult=0") + " AND NOT EXISTS (SELECT 1 FROM category c WHERE c.source=i.source AND c.kind=i.kind"
                + " AND c.ext=i.cat AND c.hidden=1)";
    }

    /** Eintraege einer Kategorie; kennt auch die besonderen Listen (alle, Favoriten, zuletzt, neu). */
    public List<Item> items(int source, int kind, String cat, boolean adult) {
        String s = String.valueOf(source), k = String.valueOf(kind);
        if (Category.ALL.equals(cat)) {
            return list("SELECT " + LIGHT + " FROM item i WHERE i.source=? AND i.kind=?" + visible(adult) + " ORDER BY i.num", s, k);
        }
        if (Category.NEW.equals(cat)) {
            return list("SELECT " + LIGHT + " FROM item i WHERE i.source=? AND i.kind=?" + visible(adult) + " ORDER BY i.added DESC, i.num LIMIT 150", s, k);
        }
        if (Category.FAV.equals(cat)) {
            return list("SELECT " + LIGHT + " FROM mark m JOIN item i ON i.source=m.source AND i.kind=m.kind AND i.ext=m.ext"
                    + " WHERE m.source=? AND m.kind=? AND m.fav=1 GROUP BY i.ext ORDER BY i.num", s, k);
        }
        if (Category.RECENT.equals(cat)) {
            return list("SELECT " + LIGHT + " FROM mark m JOIN item i ON i.source=m.source AND i.kind=m.kind AND i.ext=m.ext"
                    + " WHERE m.source=? AND m.kind=? AND m.last>0" + (adult ? "" : " AND i.adult=0") + " GROUP BY i.ext ORDER BY m.last DESC LIMIT 60", s, k);
        }
        return list("SELECT " + LIGHT + " FROM item i WHERE i.source=? AND i.kind=? AND i.cat=? ORDER BY i.num", s, k, cat);
    }

    public int markCount(int source, int kind, boolean fav) {
        return (int) count("SELECT COUNT(*) FROM mark m WHERE m.source=? AND m.kind=? AND " + (fav ? "m.fav=1" : "m.last>0")
                + " AND EXISTS (SELECT 1 FROM item i WHERE i.source=m.source AND i.kind=m.kind AND i.ext=m.ext)", String.valueOf(source), String.valueOf(kind));
    }

    public List<Item> search(int source, int kind, String query, boolean adult, int limit) {
        String q = Text.fold(query).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        if (q.isEmpty()) return new ArrayList<>();
        return list("SELECT " + LIGHT + " FROM item i WHERE i.source=? AND i.kind=? AND i.sname LIKE ? ESCAPE '\\'" + visible(adult)
                        + " ORDER BY (i.sname LIKE ? ESCAPE '\\') DESC, i.num LIMIT " + limit,
                String.valueOf(source), String.valueOf(kind), "%" + q + "%", q + "%");
    }

    /** Ein Eintrag mit allen Feldern, gesucht ueber die Kennung beim Anbieter. */
    public Item item(int source, int kind, String ext) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + FULL + " FROM item i WHERE i.source=? AND i.kind=? AND i.ext=? LIMIT 1",
                new String[]{String.valueOf(source), String.valueOf(kind), ext})) {
            return c.moveToNext() ? read(c, true) : null;
        }
    }

    public List<Episode> episodes(int source, String series) {
        List<Episode> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT ext,season,num,title,url,icon FROM episode WHERE source=? AND series=? ORDER BY season,num",
                new String[]{String.valueOf(source), series})) {
            while (c.moveToNext()) {
                Episode e = new Episode();
                e.id = c.getString(0);
                e.season = c.getInt(1);
                e.num = c.getInt(2);
                e.title = c.getString(3);
                e.url = c.getString(4);
                e.icon = c.getString(5);
                out.add(e);
            }
        }
        return out;
    }

    // ---- Merker ----------------------------------------------------------------

    private static final String MARK = "source,kind,ext,name,icon,parent,fav,pos,dur,last";

    private static Mark readMark(Cursor c) {
        Mark m = new Mark();
        m.source = c.getInt(0);
        m.kind = c.getInt(1);
        m.ext = c.getString(2);
        m.name = c.getString(3);
        m.icon = c.getString(4);
        m.parent = c.getString(5);
        m.fav = c.getInt(6) != 0;
        m.pos = c.getLong(7);
        m.dur = c.getLong(8);
        m.last = c.getLong(9);
        return m;
    }

    public Mark mark(int source, int kind, String ext) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + MARK + " FROM mark WHERE source=? AND kind=? AND ext=?",
                new String[]{String.valueOf(source), String.valueOf(kind), ext})) {
            return c.moveToNext() ? readMark(c) : null;
        }
    }

    private void ensureMark(SQLiteDatabase d, int source, int kind, String ext, String name, String icon, String parent) {
        d.execSQL("INSERT OR IGNORE INTO mark(source,kind,ext,name,icon,parent) VALUES(?,?,?,?,?,?)",
                new Object[]{source, kind, ext, name == null ? "" : name, icon == null ? "" : icon, parent == null ? "" : parent});
    }

    public boolean isFav(Item it) {
        return count("SELECT fav FROM mark WHERE source=? AND kind=? AND ext=?", String.valueOf(it.source), String.valueOf(it.kind), it.ext) != 0;
    }

    public void setFav(final Item it, final boolean fav) {
        writer.execute(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase d = getWritableDatabase();
                ensureMark(d, it.source, it.kind, it.ext, it.name, it.icon, "");
                d.execSQL("UPDATE mark SET fav=? WHERE source=? AND kind=? AND ext=?", new Object[]{fav ? 1 : 0, it.source, it.kind, it.ext});
            }
        });
    }

    public Set<String> favorites(int source, int kind) {
        Set<String> out = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT ext FROM mark WHERE source=? AND kind=? AND fav=1",
                new String[]{String.valueOf(source), String.valueOf(kind)})) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    /** Merkt, dass etwas gerade gesehen wurde, samt Position (bei Sendern 0). */
    public void saveProgress(final int source, final int kind, final String ext, final String name, final String icon,
                             final String parent, final long pos, final long dur) {
        writer.execute(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase d = getWritableDatabase();
                ensureMark(d, source, kind, ext, name, icon, parent);
                d.execSQL("UPDATE mark SET pos=?, dur=?, last=?, name=?, icon=?, parent=? WHERE source=? AND kind=? AND ext=?",
                        new Object[]{pos, dur, System.currentTimeMillis(), name == null ? "" : name, icon == null ? "" : icon,
                                parent == null ? "" : parent, source, kind, ext});
            }
        });
    }

    public Item itemByNum(int source, int kind, int num) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + LIGHT + " FROM item i WHERE i.source=? AND i.kind=? AND i.num=? LIMIT 1",
                new String[]{String.valueOf(source), String.valueOf(kind), String.valueOf(num)})) {
            return c.moveToNext() ? read(c, false) : null;
        }
    }

    /** Angefangene Filme und Folgen, neueste zuerst. Pro Serie nur die zuletzt gesehene Folge. */
    public List<Mark> continueWatching(int source, int limit) {
        List<Mark> out = new ArrayList<>();
        Set<String> series = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + MARK + " FROM mark WHERE source=? AND kind IN (1,3) AND pos>30000 ORDER BY last DESC LIMIT 80",
                new String[]{String.valueOf(source)})) {
            while (c.moveToNext() && out.size() < limit) {
                Mark m = readMark(c);
                if (m.kind == Item.EPISODE) {
                    if (!series.add(m.parent)) continue;
                } else if (!m.resumable()) {
                    continue;
                }
                out.add(m);
            }
        }
        return out;
    }

    /** Merker aller Folgen einer Serie, nach Folgen-Kennung. */
    public Map<String, Mark> episodeMarks(int source, String series) {
        Map<String, Mark> out = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + MARK + " FROM mark WHERE source=? AND kind=3 AND parent=?",
                new String[]{String.valueOf(source), series})) {
            while (c.moveToNext()) {
                Mark m = readMark(c);
                out.put(m.ext, m);
            }
        }
        return out;
    }
}

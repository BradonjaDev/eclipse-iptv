package de.umbratv.data;

import android.util.LruCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Programminfos zu Sendern, mit kurzem Zwischenspeicher. */
public final class Epg {
    public interface Callback {
        void onEpg(Item item, List<Programme> list);
    }

    private static final class Entry {
        List<Programme> list;
        long at;
    }

    private static final LruCache<String, Entry> CACHE = new LruCache<>(300);
    private static final long TTL = 10 * 60 * 1000L;

    private Epg() {
    }

    private static String key(Item it) {
        return it.source + ":" + it.ext;
    }

    /** Bereits bekannte Sendungen, sonst null. */
    public static List<Programme> cached(Item it) {
        Entry e = CACHE.get(key(it));
        if (e == null || System.currentTimeMillis() - e.at > TTL) return null;
        return e.list;
    }

    public static Programme now(List<Programme> list) {
        if (list == null) return null;
        long t = System.currentTimeMillis();
        for (Programme p : list) if (p.runsAt(t)) return p;
        return null;
    }

    /** Sendungen, die nach der laufenden beginnen. */
    public static List<Programme> next(List<Programme> list) {
        List<Programme> out = new ArrayList<>();
        if (list == null) return out;
        long t = System.currentTimeMillis();
        for (Programme p : list) if (p.start > t) out.add(p);
        return out;
    }

    public static void load(final Source s, final Item it, final Callback cb) {
        List<Programme> c = cached(it);
        if (c != null) {
            cb.onEpg(it, c);
            return;
        }
        if (s == null || !s.isXtream() || it.kind != Item.LIVE) {
            cb.onEpg(it, Collections.<Programme>emptyList());
            return;
        }
        Bg.run(new Runnable() {
            @Override
            public void run() {
                List<Programme> list;
                try {
                    list = new Xtream(s).shortEpg(it.ext, 6);
                } catch (Exception e) {
                    list = Collections.emptyList();
                }
                Entry e = new Entry();
                e.list = list;
                e.at = System.currentTimeMillis();
                CACHE.put(key(it), e);
                final List<Programme> result = list;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        cb.onEpg(it, result);
                    }
                });
            }
        });
    }
}

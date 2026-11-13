package de.umbratv.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Liest M3U-Playlists Zeile fuer Zeile, auch wenn sie hunderttausende Eintraege haben. */
public final class M3u {
    public static final class Entry {
        public String name = "", logo = "", group = "", tvgId = "", url = "";
    }

    public interface Handler {
        void header(Map<String, String> attrs);

        void entry(Entry e) throws IOException;
    }

    private M3u() {
    }

    public static void parse(BufferedReader r, Handler h) throws IOException {
        String line;
        Entry cur = null;
        boolean first = true;
        Map<String, String> attrs = new HashMap<>();
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (first) {
                first = false;
                if (!line.isEmpty() && line.charAt(0) == '﻿') line = line.substring(1);
                String low = line.length() > 200 ? line.substring(0, 200).toLowerCase() : line.toLowerCase();
                if (low.startsWith("<!doctype") || low.startsWith("<html")) {
                    throw new IOException("Der Link liefert eine Webseite statt einer Playlist.");
                }
            }
            if (line.isEmpty()) continue;
            if (line.charAt(0) == '#') {
                if (startsWith(line, "#EXTINF:")) {
                    cur = new Entry();
                    attrs.clear();
                    String name = attributes(line, 8, attrs);
                    cur.name = name;
                    cur.logo = get(attrs, "tvg-logo");
                    cur.group = get(attrs, "group-title");
                    cur.tvgId = get(attrs, "tvg-id");
                    if (cur.name.isEmpty()) cur.name = get(attrs, "tvg-name");
                } else if (startsWith(line, "#EXTGRP:")) {
                    if (cur != null && cur.group.isEmpty()) cur.group = line.substring(8).trim();
                } else if (startsWith(line, "#EXTM3U")) {
                    Map<String, String> head = new HashMap<>();
                    attributes(line, 7, head);
                    h.header(head);
                }
                continue;
            }
            Entry e = cur != null ? cur : new Entry();
            cur = null;
            e.url = line;
            if (e.name.isEmpty()) {
                int slash = line.lastIndexOf('/');
                e.name = slash >= 0 && slash < line.length() - 1 ? line.substring(slash + 1) : line;
            }
            h.entry(e);
        }
    }

    private static boolean startsWith(String line, String prefix) {
        return line.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String get(Map<String, String> m, String k) {
        String v = m.get(k);
        return v == null ? "" : v;
    }

    /**
     * Liest key="value"-Paare ab Position from und liefert den Namen hinter dem
     * ersten Komma, das nicht in Anfuehrungszeichen steht.
     */
    static String attributes(String line, int from, Map<String, String> out) {
        int n = line.length(), i = from;
        while (i < n) {
            char c = line.charAt(i);
            if (c == ',') return line.substring(i + 1).trim();
            if (c == '=' && i + 1 < n && line.charAt(i + 1) == '"') {
                int ks = i;
                while (ks > from && !Character.isWhitespace(line.charAt(ks - 1))) ks--;
                int ve = line.indexOf('"', i + 2);
                if (ve < 0) return "";
                out.put(line.substring(ks, i).toLowerCase(), line.substring(i + 2, ve).trim());
                i = ve + 1;
                continue;
            }
            i++;
        }
        return "";
    }
}

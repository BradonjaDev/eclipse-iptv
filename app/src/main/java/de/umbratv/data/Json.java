package de.umbratv.data;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kleiner, nachsichtiger JSON-Leser, der direkt aus dem Datenstrom liest.
 * Damit lassen sich auch Antworten mit zehntausenden Eintraegen verarbeiten,
 * ohne sie komplett im Speicher zu halten. Alle einfachen Werte (Text, Zahl,
 * wahr/falsch) werden als Text geliefert, weil Anbieter die Typen mischen.
 */
public final class Json implements Closeable {
    private final Reader in;
    private final char[] buf = new char[8192];
    private int pos, lim;
    private final StringBuilder sb = new StringBuilder(256);

    public Json(Reader in) {
        this.in = in;
    }

    private int read() throws IOException {
        if (pos >= lim) {
            lim = in.read(buf, 0, buf.length);
            pos = 0;
            if (lim <= 0) {
                lim = 0;
                return -1;
            }
        }
        return buf[pos++];
    }

    /** Naechstes bedeutsames Zeichen, ohne es zu verbrauchen. -1 am Ende. */
    private int look() throws IOException {
        while (true) {
            int c = read();
            if (c < 0) return -1;
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',' || c == ':' || c == 0xFEFF) continue;
            pos--;
            return c;
        }
    }

    public boolean isObject() throws IOException {
        return look() == '{';
    }

    public boolean isArray() throws IOException {
        return look() == '[';
    }

    public void beginObject() throws IOException {
        expect('{');
    }

    public void beginArray() throws IOException {
        expect('[');
    }

    public void endObject() throws IOException {
        expect('}');
    }

    public void endArray() throws IOException {
        expect(']');
    }

    private void expect(char want) throws IOException {
        int c = look();
        if (c != want) throw new IOException("JSON: '" + want + "' erwartet, gefunden '" + (c < 0 ? "Ende" : String.valueOf((char) c)) + "'");
        pos++;
    }

    /** Gibt es im aktuellen Objekt oder in der aktuellen Liste noch einen Eintrag? */
    public boolean hasNext() throws IOException {
        int c = look();
        return c >= 0 && c != '}' && c != ']';
    }

    public String nextName() throws IOException {
        return nextString();
    }

    /** Liest einen einfachen Wert als Text. Verschachtelte Werte werden uebersprungen (null). */
    public String nextString() throws IOException {
        int c = look();
        if (c < 0) throw new IOException("JSON: unerwartetes Ende");
        if (c == '{' || c == '[') {
            skipValue();
            return null;
        }
        if (c == '"') {
            pos++;
            return quoted();
        }
        sb.setLength(0);
        while (true) {
            c = read();
            if (c < 0) break;
            if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos--;
                break;
            }
            sb.append((char) c);
        }
        String s = sb.toString();
        return "null".equals(s) ? null : s;
    }

    private String quoted() throws IOException {
        sb.setLength(0);
        while (true) {
            int c = read();
            if (c < 0) throw new IOException("JSON: Text nicht abgeschlossen");
            if (c == '"') break;
            if (c == '\\') {
                c = read();
                switch (c) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': break;
                    case 'b': case 'f': break;
                    case 'u': {
                        int v = 0;
                        for (int i = 0; i < 4; i++) {
                            int h = Character.digit(read(), 16);
                            v = (v << 4) | (h < 0 ? 0 : h);
                        }
                        sb.append((char) v);
                        break;
                    }
                    default:
                        if (c >= 0) sb.append((char) c);
                }
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }

    public void skipValue() throws IOException {
        int c = look();
        if (c == '{') {
            pos++;
            while (hasNext()) {
                nextString();
                skipValue();
            }
            endObject();
        } else if (c == '[') {
            pos++;
            while (hasNext()) skipValue();
            endArray();
        } else {
            nextString();
        }
    }

    /** Liest einen beliebigen Wert als Baum aus Map, List und String (oder null). */
    public Object readValue() throws IOException {
        int c = look();
        if (c == '{') {
            pos++;
            Map<String, Object> m = new LinkedHashMap<>();
            while (hasNext()) {
                String k = nextString();
                m.put(k == null ? "" : k, readValue());
            }
            endObject();
            return m;
        }
        if (c == '[') {
            pos++;
            List<Object> l = new ArrayList<>();
            while (hasNext()) l.add(readValue());
            endArray();
            return l;
        }
        return nextString();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    // ---- Hilfen fuer den Baum ------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<Object>();
    }

    public static Map<String, Object> obj(Object o, String key) {
        return obj(obj(o).get(key));
    }

    public static List<Object> arr(Object o, String key) {
        return arr(obj(o).get(key));
    }

    /** Text eines Feldes; bei Listen der erste Eintrag. Nie null. */
    public static String str(Object o, String key) {
        Object v = obj(o).get(key);
        if (v instanceof List) {
            List<?> l = (List<?>) v;
            v = l.isEmpty() ? null : l.get(0);
        }
        return v instanceof String ? ((String) v).trim() : "";
    }

    public static long num(Object o, String key) {
        return toLong(str(o, key));
    }

    public static long toLong(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double toDouble(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

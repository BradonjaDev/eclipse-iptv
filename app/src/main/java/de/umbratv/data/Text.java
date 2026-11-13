package de.umbratv.data;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text-Helfer ohne Android-Abhaengigkeit. */
public final class Text {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern YEAR = Pattern.compile("\\(((?:19|20)\\d\\d)\\)");
    private static final Pattern YEAR_END = Pattern.compile("[-–]\\s*((?:19|20)\\d\\d)\\s*$");
    private static final Pattern YT = Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})");
    private static final Pattern ADULT = Pattern.compile("(?i)(\\bxxx\\b|adult|porn|erotik|erotic|\\+18|18\\+|\\bsex\\b)");
    private static final Pattern EPISODE_PREFIX = Pattern.compile("(?i)^.*?\\bS\\d{1,2}\\s*[._ -]?\\s*E\\d{1,3}\\b\\s*[-–:.]?\\s*");

    private Text() {
    }

    /** Kleinschreibung ohne Akzente, fuer die Suche. */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD);
        return MARKS.matcher(n).replaceAll("").replace("ß", "ss").trim();
    }

    public static int year(String released, String name) {
        if (released != null && released.length() >= 4) {
            try {
                int y = Integer.parseInt(released.substring(0, 4));
                if (y > 1880 && y < 2100) return y;
            } catch (NumberFormatException ignored) {
            }
        }
        if (name != null) {
            Matcher m = YEAR.matcher(name);
            int y = 0;
            while (m.find()) y = Integer.parseInt(m.group(1));
            if (y == 0) {
                m = YEAR_END.matcher(name);
                if (m.find()) y = Integer.parseInt(m.group(1));
            }
            return y;
        }
        return 0;
    }

    /** YouTube-Kennung aus einer Kennung oder einem beliebigen YouTube-Link. */
    public static String youtubeId(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.matches("[A-Za-z0-9_-]{11}")) return s;
        Matcher m = YT.matcher(s);
        return m.find() ? m.group(1) : "";
    }

    public static boolean looksAdult(String name) {
        return name != null && ADULT.matcher(name).find();
    }

    /** "Serie - S01E02 - Titel" wird zu "Titel". */
    public static String episodeTitle(String raw, int num) {
        String t = raw == null ? "" : raw.trim();
        String cut = EPISODE_PREFIX.matcher(t).replaceFirst("").trim();
        if (!cut.equals(t)) t = cut;
        return t.isEmpty() ? "Folge " + num : t;
    }

    public static String hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return Long.toHexString(h);
    }

    public static String base64(String s) {
        if (s == null || s.isEmpty()) return "";
        byte[] out = new byte[s.length() * 3 / 4 + 3];
        int n = 0, acc = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int v;
            if (c >= 'A' && c <= 'Z') v = c - 'A';
            else if (c >= 'a' && c <= 'z') v = c - 'a' + 26;
            else if (c >= '0' && c <= '9') v = c - '0' + 52;
            else if (c == '+' || c == '-') v = 62;
            else if (c == '/' || c == '_') v = 63;
            else continue;
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[n++] = (byte) ((acc >> bits) & 0xff);
            }
        }
        return new String(out, 0, n, StandardCharsets.UTF_8).trim();
    }

    public static String clock(int secs) {
        if (secs < 0) secs = 0;
        int h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    public static String minutes(int secs) {
        if (secs <= 0) return "";
        int m = Math.max(1, Math.round(secs / 60f));
        return m >= 60 ? (m / 60) + " Std. " + (m % 60) + " Min." : m + " Min.";
    }

    public static boolean empty(String s) {
        return s == null || s.trim().isEmpty() || "N/A".equalsIgnoreCase(s.trim());
    }
}

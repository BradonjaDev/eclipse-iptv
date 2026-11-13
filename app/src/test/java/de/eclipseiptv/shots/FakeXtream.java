package de.eclipseiptv.shots;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import javax.imageio.ImageIO;

/** Ein kleiner Xtream-Server mit erfundenen Daten fuer die Screenshot-Tests. */
public final class FakeXtream {
    static final String[][] LIVE_CATS = {{"1", "DE • Deutschland"}, {"2", "DE • Sport"}, {"3", "DE • Nachrichten"}, {"4", "DE • Kinder"}, {"5", "DE • Dokumentation"},
            {"6", "DE • Musik"}, {"7", "AT • Österreich"}, {"8", "CH • Schweiz"}, {"9", "UK • United Kingdom"}, {"10", "FOR ADULTS"}};
    static final String[] CHANNELS = {"Das Erste HD", "ZDF HD", "RTL HD", "SAT.1 HD", "ProSieben HD", "VOX HD", "kabel eins HD", "RTL ZWEI HD",
            "3sat HD", "arte HD", "ZDFneo HD", "ONE HD", "WDR HD Köln", "NDR HD Hamburg", "BR HD Süd", "SWR HD BW", "MDR HD Sachsen",
            "hr-fernsehen HD", "rbb Berlin HD", "phoenix HD", "tagesschau24 HD", "Comedy Central HD", "DMAX HD", "TLC HD", "sixx HD"};
    static final String[] SHOWS = {"Tagesschau", "Tatort", "Wer weiß denn sowas?", "heute journal", "Sportschau", "Terra X", "Der Bergdoktor",
            "Familien im Brennpunkt", "Galileo", "Die Höhle der Löwen", "Bares für Rares", "Mord mit Aussicht"};
    static final String[] MOVIES = {"Der letzte Zug nach Prag", "Nachtfalter", "Im Schatten des Mondes", "Rote Wolken", "Stille Wasser", "Die Brücke von Ostende",
            "Kaltfront", "Sturmwarnung", "Das Vermächtnis", "Glutnacht", "Grenzgänger", "Mondfinsternis", "Ferne Ufer", "Das Haus am See",
            "Eisenherz", "Die Uhrmacherin", "Funkstille", "Schwarzes Gold", "Zwischen den Welten", "Abendrot", "Feuerprobe", "Der Lotse",
            "Nordlicht", "Spiegelbild"};
    static final String[] SERIES = {"Dunkelfeld", "Die Akademie", "Küstenwache Nord", "Schattenspiel", "Das Archiv", "Blutmond", "Hafenkante",
            "Der Kreis", "Zeitsprung", "Eisland", "Wolkenreich", "Stadt der Lichter"};
    static final Color[] PALETTE = {new Color(0x5B1A2B), new Color(0x1E3A5F), new Color(0x2E4A2E), new Color(0x4A2E5B), new Color(0x6B3A1A),
            new Color(0x1A4A4A), new Color(0x3A1A1A), new Color(0x2B2B4A)};

    private final HttpServer server;
    public final String base;

    public FakeXtream() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private void handle(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
            if (path.startsWith("/img/")) {
                send(ex, "image/png", image(path.substring(5)));
                return;
            }
            String action = "";
            String id = "";
            for (String p : query.split("&")) {
                if (p.startsWith("action=")) action = p.substring(7);
                if (p.startsWith("vod_id=") || p.startsWith("series_id=") || p.startsWith("stream_id=")) id = p.substring(p.indexOf('=') + 1);
            }
            send(ex, "application/json", json(action, id).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            try {
                ex.sendResponseHeaders(500, -1);
            } catch (Exception ignored) {
            }
        } finally {
            ex.close();
        }
    }

    private static void send(HttpExchange ex, String type, byte[] body) throws Exception {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(body);
        }
    }

    private String json(String action, String id) {
        StringBuilder b = new StringBuilder();
        switch (action) {
            case "":
                return "{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"exp_date\":\"1999999999\",\"max_connections\":\"1\"},\"server_info\":{}}";
            case "get_live_categories":
                b.append('[');
                for (int i = 0; i < LIVE_CATS.length; i++) {
                    if (i > 0) b.append(',');
                    b.append("{\"category_id\":").append(q(LIVE_CATS[i][0])).append(",\"category_name\":").append(q(LIVE_CATS[i][1])).append(",\"parent_id\":0}");
                }
                return b.append(']').toString();
            case "get_vod_categories":
                return "[{\"category_id\":\"20\",\"category_name\":\"DE • Kino 2026\"},{\"category_id\":\"21\",\"category_name\":\"DE • Action & Abenteuer\"},"
                        + "{\"category_id\":\"22\",\"category_name\":\"DE • Komödie\"},{\"category_id\":\"23\",\"category_name\":\"DE • Drama\"},"
                        + "{\"category_id\":\"24\",\"category_name\":\"DE • Dokumentation\"},{\"category_id\":\"25\",\"category_name\":\"DE • Kinder\"}]";
            case "get_series_categories":
                return "[{\"category_id\":\"30\",\"category_name\":\"DE • Serien\"},{\"category_id\":\"31\",\"category_name\":\"DE • Krimi & Thriller\"},"
                        + "{\"category_id\":\"32\",\"category_name\":\"DE • Sci-Fi & Fantasy\"}]";
            case "get_live_streams": {
                b.append('[');
                int n = 0;
                for (int c = 0; c < LIVE_CATS.length; c++) {
                    int count = c == 0 ? CHANNELS.length : 6;
                    for (int i = 0; i < count; i++) {
                        String name = c == 0 ? CHANNELS[i] : LIVE_CATS[c][1].replaceAll("^.*• ", "") + " " + (i + 1) + " HD";
                        if (n++ > 0) b.append(',');
                        b.append("{\"num\":").append(n).append(",\"name\":").append(q(name)).append(",\"stream_type\":\"live\",\"stream_id\":").append(1000 + n)
                                .append(",\"stream_icon\":").append(q(base + "/img/logo-" + n + ".png")).append(",\"epg_channel_id\":\"x").append(n)
                                .append("\",\"added\":\"1700000000\",\"is_adult\":").append(c == 9 ? 1 : 0).append(",\"category_id\":").append(q(LIVE_CATS[c][0]))
                                .append(",\"tv_archive\":0}");
                    }
                }
                return b.append(']').toString();
            }
            case "get_vod_streams": {
                b.append('[');
                for (int i = 0; i < 60; i++) {
                    String name = MOVIES[i % MOVIES.length] + (i >= MOVIES.length ? " " + (i / MOVIES.length + 1) : "") + " (" + (2026 - i % 9) + ")";
                    if (i > 0) b.append(',');
                    b.append("{\"num\":").append(i + 1).append(",\"name\":").append(q(name)).append(",\"stream_type\":\"movie\",\"stream_id\":").append(5000 + i)
                            .append(",\"stream_icon\":").append(q(base + "/img/poster-" + i + ".png")).append(",\"rating\":\"").append(5.5 + (i * 7 % 40) / 10.0)
                            .append("\",\"added\":\"").append(1790000000 - i * 3600).append("\",\"is_adult\":0,\"category_id\":\"").append(20 + i % 6)
                            .append("\",\"container_extension\":\"mkv\",\"trailer\":\"dQw4w9WgXcQ\"}");
                }
                return b.append(']').toString();
            }
            case "get_series": {
                b.append('[');
                for (int i = 0; i < 36; i++) {
                    String name = SERIES[i % SERIES.length] + (i >= SERIES.length ? " " + (i / SERIES.length + 1) : "") + " (" + (2025 - i % 6) + ")";
                    if (i > 0) b.append(',');
                    b.append("{\"num\":").append(i + 1).append(",\"name\":").append(q(name)).append(",\"series_id\":").append(7000 + i)
                            .append(",\"cover\":").append(q(base + "/img/cover-" + i + ".png")).append(",\"plot\":\"Eine Geschichte über Licht und Schatten.\"")
                            .append(",\"genre\":\"Drama, Krimi\",\"rating\":\"").append(6 + i % 4).append("\",\"last_modified\":\"").append(1790000000 - i * 7200)
                            .append("\",\"backdrop_path\":[").append(q(base + "/img/backdrop-" + i + ".png")).append("],\"youtube_trailer\":\"dQw4w9WgXcQ\",\"category_id\":\"")
                            .append(30 + i % 3).append("\"}");
                }
                return b.append(']').toString();
            }
            case "get_vod_info":
                return "{\"info\":{\"name\":\"Der letzte Zug nach Prag (2026)\",\"cover_big\":" + q(base + "/img/poster-0.png")
                        + ",\"plot\":\"Auf der letzten Fahrt des legendären Nachtzugs treffen Agenten, Diebe und eine junge Fotografin aufeinander. "
                        + "Während der Zug durch die Nacht rast, schwindet das Vertrauen – und jeder hat etwas zu verbergen.\","
                        + "\"genre\":\"Thriller, Drama\",\"director\":\"Mara Lindqvist\",\"cast\":\"Jonas Weber, Elif Kaya, Tom Brandt, Sofia Marin\","
                        + "\"releasedate\":\"2026-09-10\",\"rating\":\"7.8\",\"duration_secs\":\"6300\",\"youtube_trailer\":\"dQw4w9WgXcQ\","
                        + "\"backdrop_path\":[" + q(base + "/img/backdrop-0.png") + "]},\"movie_data\":{}}";
            case "get_series_info": {
                b.append("{\"info\":{\"name\":\"Dunkelfeld (2025)\",\"cover\":").append(q(base + "/img/cover-0.png"))
                        .append(",\"plot\":\"Eine Ermittlerin kehrt in ihre Heimatstadt zurück, in der seit Jahren Menschen spurlos verschwinden.\",")
                        .append("\"genre\":\"Krimi, Drama\",\"cast\":\"Lea Hoffmann, Ben Kaiser\",\"rating\":\"8.1\",\"releaseDate\":\"2025-03-01\",")
                        .append("\"youtube_trailer\":\"dQw4w9WgXcQ\",\"backdrop_path\":[").append(q(base + "/img/backdrop-1.png")).append("]},\"episodes\":{");
                String[] titles = {"Die Rückkehr", "Spuren im Schnee", "Das Schweigen", "Unter Tage", "Alte Wunden", "Der Zeuge", "Nachtschicht", "Finsternis"};
                for (int s = 1; s <= 3; s++) {
                    if (s > 1) b.append(',');
                    b.append('"').append(s).append("\":[");
                    for (int e = 1; e <= 8; e++) {
                        if (e > 1) b.append(',');
                        b.append("{\"id\":\"").append(9000 + s * 100 + e).append("\",\"episode_num\":").append(e).append(",\"season\":").append(s)
                                .append(",\"title\":").append(q("Dunkelfeld - S0" + s + "E0" + e + " - " + titles[e - 1])).append(",\"container_extension\":\"mkv\",")
                                .append("\"info\":{\"duration_secs\":").append(2700 + e * 60).append(",\"plot\":\"Die Ermittlungen führen in eine unerwartete Richtung.\",")
                                .append("\"movie_image\":").append(q(base + "/img/still-" + s + e + ".png")).append("}}");
                    }
                    b.append(']');
                }
                return b.append("}}").toString();
            }
            case "get_short_epg": {
                long now = System.currentTimeMillis() / 1000;
                long start = now - 1500;
                int seed = id.isEmpty() ? 0 : Integer.parseInt(id) % SHOWS.length;
                b.append("{\"epg_listings\":[");
                for (int i = 0; i < 5; i++) {
                    long s = start + i * 2700L, e = s + 2700;
                    if (i > 0) b.append(',');
                    b.append("{\"title\":\"").append(b64(SHOWS[(seed + i) % SHOWS.length])).append("\",\"description\":\"")
                            .append(b64("Spannende Unterhaltung am Abend. Heute mit neuen Gästen, Überraschungen und einem Blick hinter die Kulissen."))
                            .append("\",\"start_timestamp\":\"").append(s).append("\",\"stop_timestamp\":\"").append(e).append("\"}");
                }
                return b.append("]}").toString();
            }
            default:
                return "[]";
        }
    }

    /** Erzeugt Platzhalterbilder: Logos, Poster, Hintergruende. */
    private static byte[] image(String name) throws Exception {
        String key = name.replace(".png", "");
        int n = Math.abs(key.hashCode());
        boolean logo = key.startsWith("logo"), poster = key.startsWith("poster") || key.startsWith("cover"), wide = key.startsWith("backdrop") || key.startsWith("still");
        int w = logo ? 200 : poster ? 300 : 640, h = logo ? 120 : poster ? 450 : 360;
        BufferedImage img = new BufferedImage(w, h, logo ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color c = PALETTE[n % PALETTE.length];
        if (logo) {
            g.setColor(new Color(255, 255, 255, 235));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 52));
            String t = key.substring(5);
            String label = new String[]{"ARD", "ZDF", "RTL", "SAT1", "PRO7", "VOX", "K1", "RTL2", "3sat", "arte", "neo", "ONE"}[Integer.parseInt(t) % 12];
            int tw = g.getFontMetrics().stringWidth(label);
            g.drawString(label, (w - tw) / 2, 78);
        } else {
            g.setPaint(new GradientPaint(0, 0, c.brighter(), w, h, c.darker().darker()));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 255, 255, 40));
            g.fillOval(w / 2, h / 6, w, w);
            if (poster) {
                g.setColor(new Color(255, 255, 255, 220));
                g.setFont(new Font(Font.SERIF, Font.BOLD, 34));
                g.drawString(String.format(Locale.ROOT, "Nº %d", n % 97), 22, h - 40);
            }
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}

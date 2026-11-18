package de.eclipseiptv.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Ausweich-Server: Abrufe mit lokalen Servern, die ausfallen, Fehler melden oder funktionieren. */
public class ServersTest {
    private final List<ServerSocket> running = new ArrayList<>();
    private final List<String[]> saved = new ArrayList<>();

    @Before
    public void setUp() {
        Servers.reset();
        Servers.setStore(new Servers.Store() {
            @Override
            public void save(int sourceId, String base, String alts) {
                saved.add(new String[]{String.valueOf(sourceId), base, alts});
            }
        });
    }

    @After
    public void tearDown() {
        for (ServerSocket s : running) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        Servers.setStore(null);
        Servers.reset();
    }

    /** Ein Server, der auf alles mit diesem Status und Inhalt antwortet. */
    private String server(final int status, final String body) throws IOException {
        final ServerSocket ss = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        running.add(ss);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!ss.isClosed()) {
                    try (Socket c = ss.accept()) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                        String line;
                        while ((line = in.readLine()) != null && !line.isEmpty()) {
                            // Anfrage-Kopf ueberspringen
                        }
                        byte[] b = body.getBytes(StandardCharsets.UTF_8);
                        OutputStream o = c.getOutputStream();
                        o.write(("HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\nContent-Length: " + b.length
                                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        o.write(b);
                        o.flush();
                    } catch (IOException ignored) {
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return "http://127.0.0.1:" + ss.getLocalPort();
    }

    /** Eine Adresse, an der niemand lauscht. */
    private static String dead() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return "http://127.0.0.1:" + s.getLocalPort();
        }
    }

    private static Source source(String base, String... alts) {
        Source s = Source.xtream(base, "anna", "geheim", String.join("\n", alts));
        s.id = 7;
        return s;
    }

    private static final String OK = "{\"user_info\":{\"auth\":1,\"status\":\"Active\",\"max_connections\":\"2\"}}";

    @Test
    public void fallsBackToTheNextWorkingServer() throws Exception {
        String down = dead(), bad = server(502, "kaputt"), denied = server(401, "nein"), ok = server(200, OK);
        Source s = source(down, bad, denied, ok);
        assertEquals(Arrays.asList(down, bad, denied, ok), s.servers());

        Xtream.Account a = new Xtream(s).account();
        assertEquals(2, a.maxConnections);
        assertEquals(ok, Servers.current(s));
        assertEquals(ok, Servers.order(s).get(0));
        assertTrue(new Xtream(s).api("get_live_streams").startsWith(ok + "/player_api.php?username=anna&password=geheim"));
        assertTrue(new Xtream(s).liveUrl("12", true).startsWith(ok + "/live/anna/geheim/12.m3u8"));

        assertEquals(1, saved.size());
        assertEquals("7", saved.get(0)[0]);
        assertEquals(ok, saved.get(0)[1]);
        assertEquals(down + "\n" + bad + "\n" + denied, saved.get(0)[2]);

        // Beim naechsten Abruf kommt der funktionierende Server zuerst dran, ohne neues Speichern.
        new Xtream(s).account();
        assertEquals(1, saved.size());
    }

    @Test
    public void throwsTheFirstRealServerAnswer() throws Exception {
        String down = dead(), bad = server(502, "kaputt"), denied = server(401, "nein");
        Source s = source(down, bad, denied);
        try {
            new Xtream(s).account();
            fail();
        } catch (Http.Status e) {
            assertEquals(502, e.code);
            assertEquals("Der Server antwortet mit Fehler 502", e.getMessage());
        }
        assertTrue(saved.isEmpty());
    }

    @Test
    public void saysSoWhenNoServerIsReachable() throws Exception {
        Source s = source(dead(), dead());
        try {
            new Xtream(s).account();
            fail();
        } catch (IOException e) {
            assertFalse(e instanceof Http.Status);
            assertEquals("Keiner der 2 Server antwortet. Stimmen die Adressen, und ist das Gerät online?", e.getMessage());
        }
    }

    @Test
    public void singleServerKeepsItsOwnError() throws Exception {
        Source s = source(server(401, "nein"));
        try {
            new Xtream(s).account();
            fail();
        } catch (Http.Status e) {
            assertEquals(401, e.code);
        }
    }

    @Test
    public void mainServerWorkingIsNotSaved() throws Exception {
        String ok = server(200, OK);
        Source s = source(ok, dead());
        new Xtream(s).account();
        assertEquals(ok, Servers.current(s));
        assertTrue(saved.isEmpty());
        Servers.forget(s.id);
        assertEquals(ok, Servers.current(s));
    }

    @Test
    public void jsonGoesThroughServers() throws Exception {
        String ok = server(200, "[{\"category_id\":\"3\",\"category_name\":\"News\"}]");
        Source s = source(dead(), ok);
        try (Json j = new Xtream(s).json("get_live_categories", 5000)) {
            assertTrue(j.isArray());
            assertEquals("News", Json.str(Json.arr(j.readValue()).get(0), "category_name"));
        }
        assertEquals(ok, Servers.current(s));
    }

    @Test
    public void playableSwitchesServers() {
        Source s = source("http://a.tv:80", "http://b.tv:8080", "https://c.tv");
        Item it = new Item();
        it.ext = "12";
        it.name = "Das Erste";
        Playable p = Playable.live(s, it, true);
        assertEquals("http://a.tv:80/live/anna/geheim/12.m3u8", p.url);
        assertEquals("http://a.tv:80/live/anna/geheim/12.ts", p.altUrl);
        assertEquals("a.tv", p.host());

        assertTrue(p.nextServer());
        assertEquals("http://b.tv:8080/live/anna/geheim/12.m3u8", p.url);
        assertEquals("http://b.tv:8080/live/anna/geheim/12.ts", p.altUrl);
        assertEquals("b.tv", p.host());
        assertTrue(p.nextServer());
        assertEquals("https://c.tv/live/anna/geheim/12.m3u8", p.url);
        assertFalse(p.nextServer());
        assertEquals("https://c.tv/live/anna/geheim/12.m3u8", p.url);

        p.works();
        assertEquals("https://c.tv", Servers.current(s));
        assertEquals(1, saved.size());
        assertEquals("https://c.tv", saved.get(0)[1]);
        assertEquals("http://a.tv:80\nhttp://b.tv:8080", saved.get(0)[2]);

        // Nach works() sind wieder alle Server moeglich.
        assertTrue(p.nextServer());
        assertEquals("http://a.tv:80/live/anna/geheim/12.m3u8", p.url);

        // Ein vorher gebauter Eintrag folgt vor dem Start dem aktiven Server.
        Item m = new Item();
        m.ext = "99";
        m.cext = "mkv";
        Servers.reset();
        Playable movie = Playable.movie(s, m);
        assertEquals("http://a.tv:80/movie/anna/geheim/99.mkv", movie.url);
        Servers.works(s, "http://b.tv:8080");
        movie.fresh();
        assertEquals("http://b.tv:8080/movie/anna/geheim/99.mkv", movie.url);
    }

    @Test
    public void m3uPlayableHasNoServers() {
        Source s = Source.fromUrl("http://example.com/liste.m3u");
        Item it = new Item();
        it.url = "http://example.com/1.ts";
        it.name = "Eins";
        Playable p = Playable.live(s, it, true);
        assertFalse(p.nextServer());
        p.fresh();
        p.works();
        assertEquals("http://example.com/1.ts", p.url);
        assertTrue(saved.isEmpty());
    }

    @Test
    public void parsesInput() {
        Source s = Source.fromInput("http://tv.example.com:8080/get.php?username=anna&password=geheim&type=m3u_plus\n"
                + "  backup.example.com:8080 , https://drei.example.com/get.php?username=anna&password=geheim\r\n"
                + "vier.example.com\n\ntv.example.com:8080\n");
        assertTrue(s.isXtream());
        assertEquals("http://tv.example.com:8080", s.base);
        assertEquals("http://backup.example.com:8080\nhttps://drei.example.com\nhttp://vier.example.com", s.alts);
        assertEquals(Arrays.asList("http://tv.example.com:8080", "http://backup.example.com:8080", "https://drei.example.com",
                "http://vier.example.com"), s.servers());

        Source one = Source.fromInput("  http://tv.example.com:8080/get.php?username=anna&password=geheim  ");
        assertEquals("", one.alts);
        assertEquals(Arrays.asList("http://tv.example.com:8080"), one.servers());

        assertEquals("http://example.com/liste.m3u", Source.fromInput("example.com/liste.m3u").url);
        try {
            Source.fromInput("http://example.com/liste.m3u\nhttp://backup.example.com");
            fail();
        } catch (IllegalArgumentException expected) {
        }

        Source x = Source.xtream("tv.example.com:8080/", "anna", "geheim", "a.example.com, b.example.com:81 http://tv.example.com:8080");
        assertEquals("http://tv.example.com:8080", x.base);
        assertEquals("http://a.example.com\nhttp://b.example.com:81", x.alts);
        assertEquals("http://h.example.com:9", Source.server(" H.example.com:9/irgendwas?x=1 ").toLowerCase());
    }

    @Test
    public void sameAsComparesUserAndServers() {
        Source a = Source.xtream("tv.example.com:8080", "anna", "geheim", "backup.example.com");
        Source b = Source.xtream("backup.example.com", "anna", "neu", null);
        Source c = Source.xtream("backup.example.com", "bert", "geheim", null);
        Source d = Source.xtream("anders.example.com", "anna", "geheim", null);
        assertTrue(a.sameAs(b));
        assertTrue(b.sameAs(a));
        assertFalse(a.sameAs(c));
        assertFalse(a.sameAs(d));
        assertTrue(Source.fromUrl("example.com/a.m3u").sameAs(Source.fromUrl("http://example.com/a.m3u")));
        assertFalse(Source.fromUrl("example.com/a.m3u").sameAs(a));
    }
}

package de.eclipseiptv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.eclipseiptv.data.Json;
import de.eclipseiptv.data.M3u;
import de.eclipseiptv.data.Source;
import de.eclipseiptv.data.Text;

public class DataTest {
    private static List<M3u.Entry> parse(String s) throws Exception {
        final List<M3u.Entry> out = new ArrayList<>();
        M3u.parse(new BufferedReader(new StringReader(s)), new M3u.Handler() {
            @Override
            public void header(Map<String, String> attrs) {
            }

            @Override
            public void entry(M3u.Entry e) {
                out.add(e);
            }
        });
        return out;
    }

    @Test
    public void m3uReadsAttributesAndNames() throws Exception {
        List<M3u.Entry> l = parse("\uFEFF#EXTM3U url-tvg=\"http://x/epg.xml\"\n"
                + "#EXTINF:-1 tvg-id=\"ard.de\" tvg-name=\"Das Erste\" tvg-logo=\"http://x/a.png\" group-title=\"DE, Haupt\",Das Erste HD\n"
                + "http://x/live/1.ts\n\n"
                + "#EXTINF:-1,Nur Name\n#EXTGRP:Gruppe B\nhttp://x/2\n"
                + "http://x/ohne/info.mp4\n");
        assertEquals(3, l.size());
        assertEquals("Das Erste HD", l.get(0).name);
        assertEquals("DE, Haupt", l.get(0).group);
        assertEquals("ard.de", l.get(0).tvgId);
        assertEquals("http://x/a.png", l.get(0).logo);
        assertEquals("http://x/live/1.ts", l.get(0).url);
        assertEquals("Gruppe B", l.get(1).group);
        assertEquals("info.mp4", l.get(2).name);
    }

    @Test(expected = java.io.IOException.class)
    public void m3uRejectsWebPages() throws Exception {
        parse("<!DOCTYPE html><html><body>Login</body></html>");
    }

    @Test
    public void sourceDetectsXtreamLinks() {
        Source s = Source.fromUrl(" http://tv.example.com:8080/get.php?username=anna&password=geheim&type=m3u_plus ");
        assertTrue(s.isXtream());
        assertEquals("http://tv.example.com:8080", s.base);
        assertEquals("anna", s.user);
        assertEquals("geheim", s.pass);
        Source m = Source.fromUrl("example.com/liste.m3u");
        assertFalse(m.isXtream());
        assertEquals("http://example.com/liste.m3u", m.url);
        Source x = Source.xtream("tv.example.com:8080/", "anna", "geheim");
        assertEquals("http://tv.example.com:8080", x.base);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sourceRejectsEmpty() {
        Source.fromUrl("   ");
    }

    @Test
    public void jsonHandlesMixedTypes() throws Exception {
        Json j = new Json(new StringReader("[{\"id\":12,\"name\":\"A \\\"B\\\" \\u00e4\",\"x\":null,\"list\":[1,[2,3],{\"k\":true}],\"r\":6.5},{\"id\":\"13\"}]"));
        j.beginArray();
        j.beginObject();
        String id = null, name = null, r = null;
        boolean xNull = false;
        while (j.hasNext()) {
            String k = j.nextName();
            if (k.equals("id")) id = j.nextString();
            else if (k.equals("name")) name = j.nextString();
            else if (k.equals("x")) xNull = j.nextString() == null;
            else if (k.equals("r")) r = j.nextString();
            else j.skipValue();
        }
        j.endObject();
        assertEquals("12", id);
        assertEquals("A \"B\" ä", name);
        assertTrue(xNull);
        assertEquals("6.5", r);
        Object second = j.readValue();
        assertEquals("13", Json.str(second, "id"));
        assertFalse(j.hasNext());
        j.endArray();
    }

    @Test
    public void jsonTreeIsForgiving() throws Exception {
        Object o = new Json(new StringReader("{\"info\":[],\"a\":{\"b\":[\"x\",\"y\"]},\"n\":\"7.9\"}")).readValue();
        assertTrue(Json.obj(o, "info").isEmpty());
        assertEquals("x", Json.str(Json.obj(o, "a"), "b"));
        assertEquals(7, Json.num(o, "n"));
        assertEquals("", Json.str(o, "fehlt"));
    }

    @Test
    public void textHelpers() {
        assertEquals("strasse uber", Text.fold("Straße Über"));
        assertEquals(2026, Text.year(null, "The Train (2026) NEU"));
        assertEquals(2020, Text.year("", "Der geheime Club - 2020"));
        assertEquals(1994, Text.year("1994-11-06", "Earth 2"));
        assertEquals("P7TQEYJla6k", Text.youtubeId("https://www.youtube.com/watch?v=P7TQEYJla6k&t=3"));
        assertEquals("P7TQEYJla6k", Text.youtubeId("P7TQEYJla6k"));
        assertEquals("", Text.youtubeId("kein trailer"));
        assertEquals("Einladung", Text.episodeTitle("King & Conqueror (2025) DE - S01E01 - Einladung", 1));
        assertEquals("Folge 4", Text.episodeTitle("Serie S02 E04", 4));
        assertEquals("Quizduell", Text.base64("UXVpemR1ZWxs"));
        assertTrue(Text.looksAdult("XXX • FOR ADULTS"));
        assertFalse(Text.looksAdult("DE • KINDER"));
        assertEquals("1:02:03", Text.clock(3723));
        assertEquals("1 Std. 45 Min.", Text.minutes(6300));
        assertNull(null);
    }
}

package de.umbratv.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class Http {
    public static final String UA = "Umbra/1.0 (Android)";

    private Http() {
    }

    /** Oeffnet einen Link und folgt Weiterleitungen, auch zwischen http und https. */
    public static InputStream open(String url, int readTimeoutMs) throws IOException {
        String u = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(readTimeoutMs);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Encoding", "gzip");
            c.setRequestProperty("Accept", "*/*");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Weiterleitung ohne Ziel");
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new IOException("Der Server antwortet mit Fehler " + code);
            }
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in, 16384);
            return in;
        }
        throw new IOException("Zu viele Weiterleitungen");
    }

    public static BufferedReader reader(String url, int readTimeoutMs) throws IOException {
        return new BufferedReader(new InputStreamReader(open(url, readTimeoutMs), StandardCharsets.UTF_8), 32768);
    }

    public static Json json(String url, int readTimeoutMs) throws IOException {
        return new Json(new InputStreamReader(open(url, readTimeoutMs), StandardCharsets.UTF_8));
    }
}

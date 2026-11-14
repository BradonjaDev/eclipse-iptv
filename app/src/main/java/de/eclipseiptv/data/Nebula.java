package de.eclipseiptv.data;

/**
 * Rechnet den Hintergrund der App: tiefes Schwarz mit rotem Rauch, Mondlicht aus der
 * oberen rechten Ecke, einem dunkleren Rand (Vignette) und feinem Korn gegen Farbstufen.
 *
 * Das passiert einmal beim Start in einem Hintergrund-Thread und in geringer Aufloesung;
 * das Ergebnis wird weich hochskaliert. Danach kostet der Hintergrund nichts mehr. (Ein
 * echter Grafik-Shader wuerde dieselbe Rechnung bei jedem Bild wiederholen – auf einer
 * TV-Box unnoetig teuer, und erst ab Android 13 moeglich.)
 *
 * Reines Java ohne Android, damit es sich auch am PC pruefen laesst (tools/Nebula*).
 */
public final class Nebula {
    private Nebula() {
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ seed * 0x9E3779B9;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        return h ^ (h >>> 16);
    }

    /** Glattes Rauschen zwischen 0 und 1 (Wertrauschen mit weicher Interpolation). */
    private static float noise(float x, float y, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
        float a = (hash(xi, yi, seed) & 0xFFFF) / 65535f;
        float b = (hash(xi + 1, yi, seed) & 0xFFFF) / 65535f;
        float c = (hash(xi, yi + 1, seed) & 0xFFFF) / 65535f;
        float d = (hash(xi + 1, yi + 1, seed) & 0xFFFF) / 65535f;
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    /** Mehrere Lagen Rauschen uebereinander: grosse Schwaden mit feinen Rauchfaeden. */
    private static float fbm(float x, float y, int seed) {
        float sum = 0, amp = 0.5f, norm = 0;
        for (int o = 0; o < 5; o++) {
            sum += amp * noise(x, y, seed + o * 17);
            norm += amp;
            x = x * 2.03f + 13.1f;
            y = y * 2.03f + 7.7f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float smooth(float e0, float e1, float v) {
        float t = clamp((v - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    /** Liefert w*h Bildpunkte (ARGB). */
    public static int[] render(int w, int h, int seed) {
        int[] px = new int[w * h];
        float aspect = w / (float) h;
        java.util.Random grain = new java.util.Random(seed);
        for (int j = 0; j < h; j++) {
            float y = j / (float) h;
            for (int i = 0; i < w; i++) {
                float x = i / (float) w;
                float sx = x * aspect * 2.2f, sy = y * 2.2f;
                // Rauch mit verwirbelten Koordinaten (Domain Warping)
                float qx = fbm(sx, sy, seed), qy = fbm(sx + 5.2f, sy + 1.3f, seed);
                float n = fbm(sx + 2.4f * qx, sy + 2.4f * qy, seed + 101);
                float smoke = smooth(0.42f, 0.86f, n);

                // Licht: oben rechts stark, ein schraeges Band durch die Mitte, unten links schwach
                float dx = x - 0.93f, dy = (y + 0.08f) / aspect * 1.7f;
                float moon = (float) Math.exp(-(dx * dx + dy * dy) * 2.6f);
                float band = (float) Math.exp(-Math.pow((y - 0.75f + x * 0.55f) * 3.4f, 2));
                float corner = (float) Math.exp(-((x - 0.02f) * (x - 0.02f) + (y - 1.05f) * (y - 1.05f)) * 6f);
                float light = moon * 1.25f + band * 0.26f + corner * 0.14f;

                // Grundton: fast schwarz, leicht warm
                float r = 0.034f, g = 0.020f, b = 0.026f;
                // Mondlicht als weiches Rot
                r += moon * 0.17f;
                g += moon * 0.010f;
                b += moon * 0.030f;
                // Rauch: dunkles Blutrot, hell wo Licht hinfaellt
                float s = smoke * (0.09f + light * 0.55f);
                r += s * 0.56f;
                g += s * 0.03f;
                b += s * 0.09f;
                // Hellere Kanten im Rauch (wie angeleuchtet)
                float rim = smooth(0.62f, 0.74f, n) * (1 - smooth(0.74f, 0.9f, n)) * light;
                r += rim * 0.12f;
                g += rim * 0.02f;
                b += rim * 0.03f;
                // Vignette: Raender dunkler, damit der Blick in die Mitte geht
                float vx = x - 0.5f, vy = (y - 0.5f) * 1.1f;
                float vig = 1 - clamp((vx * vx + vy * vy) * 1.25f) * 0.7f;
                r *= vig;
                g *= vig;
                b *= vig;
                // Korn gegen Farbstufen
                float k = (grain.nextFloat() - 0.5f) * (2.2f / 255f);
                int ri = (int) (clamp(r + k) * 255 + 0.5f), gi = (int) (clamp(g + k) * 255 + 0.5f), bi = (int) (clamp(b + k) * 255 + 0.5f);
                px[j * w + i] = 0xFF000000 | (ri << 16) | (gi << 8) | bi;
            }
        }
        return px;
    }
}

import de.eclipseiptv.data.Nebula;
import java.io.FileOutputStream;

/** Entwickler-Werkzeug: schreibt den Hintergrund als PPM-Bild. Aufruf: NebulaPreview <Datei> [Breite Hoehe] */
public class NebulaPreview {
    public static void main(String[] a) throws Exception {
        int w = a.length > 2 ? Integer.parseInt(a[1]) : 640, h = a.length > 2 ? Integer.parseInt(a[2]) : 360;
        long t = System.nanoTime();
        int[] px = Nebula.render(w, h, 7);
        System.out.println("Rechenzeit: " + (System.nanoTime() - t) / 1000000 + " ms");
        try (FileOutputStream f = new FileOutputStream(a[0])) {
            f.write(("P6\n" + w + " " + h + "\n255\n").getBytes());
            byte[] row = new byte[w * 3];
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    int p = px[j * w + i];
                    row[i * 3] = (byte) (p >> 16);
                    row[i * 3 + 1] = (byte) (p >> 8);
                    row[i * 3 + 2] = (byte) p;
                }
                f.write(row);
            }
        }
    }
}

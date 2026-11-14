package de.eclipseiptv.ui;

/**
 * Stellschrauben fuer das Aussehen an einer Stelle. Die Vorschau-Tests aendern sie, um
 * Varianten nebeneinander zu zeigen, bevor eine davon eingebaut wird.
 */
public final class Design {
    private Design() {
    }

    /** Finsternis hinter der Senderliste: Mitte x, Mitte y (Anteile), Radius (Anteil der Hoehe). */
    public static float[] listEclipse = {0.44f, 0.56f, 0.47f};
    public static float listEclipseIntensity = 0.34f;
    /** Farbe des Randes der Finsternis hinter der Senderliste (im Logo bleibt er weiss). */
    public static int listEclipseRim = 0xFFDD1A34;
    /** Nur fuer die Vorschau: Oberflaeche ohne Hintergrund fotografieren (durchsichtig). */
    public static boolean previewTransparent = false;
}

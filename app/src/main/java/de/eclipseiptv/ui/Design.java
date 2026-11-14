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
    /** Farbe des Randes der Finsternis hinter der Senderliste. */
    public static int listEclipseRim = 0xFFDD1A34;
    /** Dunkler, halbdurchsichtiger Grund hinter jeder Zeile der Senderliste. */
    public static boolean rowBacking = false;

    /** Motiv im Hintergrund: "clouds" (ziehende Wolken) oder eines aus Scenery. */
    public static String scenery = "none";
    /** Nur fuer die Vorschau: Oberflaeche ohne Hintergrund fotografieren (durchsichtig). */
    public static boolean previewTransparent = false;

    /** Wolken im Hintergrund: Bahn (y), Hoehe, Tempo (Einheiten/s), Deckkraft, Startpunkt 0..1. */
    public static float[][] sky = {
            {10, 24, 7, 0.30f, 0.15f},
            {196, 22, 6, 0.26f, 0.70f},
            {112, 36, 11, 0.36f, 0.55f},
            {318, 40, 12, 0.36f, 0.05f},
            {262, 54, 16, 0.40f, 0.85f},
            {440, 66, 19, 0.44f, 0.35f},
    };
}

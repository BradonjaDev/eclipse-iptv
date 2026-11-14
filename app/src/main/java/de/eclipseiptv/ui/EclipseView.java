package de.eclipseiptv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Das Zeichen der App: eine Finsternis mit rotem Lichtkranz und weisser Kontur. */
public final class EclipseView extends View {
    public EclipseView(Context c) {
        super(c);
        setBackground(new Art(1f));
    }

    /**
     * Die Finsternis als Drawable. intensity 1 = volles Logo, kleinere Werte fuer
     * dezente Hintergruende.
     */
    public static final class Art extends Drawable {
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float intensity;
        private float cx, cy, r;
        /** Mittelpunkt und Radius relativ zur Flaeche; null = zentriert und ausgefuellt. */
        private float[] place;

        public Art(float intensity) {
            this.intensity = intensity;
            disc.setColor(0xFF050304);
            rim.setStyle(Paint.Style.STROKE);
            rim.setColor(0xFFFFFFFF);
        }

        /** Positioniert die Finsternis: Mitte (Anteil von Breite/Hoehe) und Radius (Anteil der Hoehe). */
        public Art at(float fx, float fy, float fr) {
            place = new float[]{fx, fy, fr};
            return this;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            if (place == null) {
                cx = b.exactCenterX();
                cy = b.exactCenterY();
                r = Math.min(b.width(), b.height()) / 2f;
            } else {
                cx = b.left + b.width() * place[0];
                cy = b.top + b.height() * place[1];
                r = b.height() * place[2];
            }
            if (r <= 0) return;
            // Bei gedaempfter Darstellung bleibt das rote Leuchten kraeftiger als der weisse Ring.
            int a = Math.round(255 * Math.min(1f, intensity * 1.7f));
            glow.setShader(new RadialGradient(cx, cy, r, new int[]{0xFFFFD6DA, 0xFFFF2B42, 0xB0B00A20, 0x307E0A1B, 0x007E0A1B},
                    new float[]{0f, 0.52f, 0.62f, 0.82f, 1f}, Shader.TileMode.CLAMP));
            glow.setAlpha(a);
            // Duenne Kontur: bei grossen Finsternissen (Hintergrund) nicht dicker als ein feiner Strich
            rim.setStrokeWidth(Math.max(1f, Math.min(r * 0.03f, Ui.px(2.2f))));
            // Weisser Rand wie im Logo, auch bei gedaempfter Darstellung klar weiss
            rim.setAlpha(Math.round(255 * Math.min(1f, 0.45f + intensity * 1.2f)));
            disc.setAlpha(255);
        }

        @Override
        public void draw(Canvas c) {
            if (r <= 0) return;
            c.drawCircle(cx, cy, r, glow);
            c.drawCircle(cx, cy, r * 0.54f, disc);
            c.drawCircle(cx, cy, r * 0.54f, rim);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}

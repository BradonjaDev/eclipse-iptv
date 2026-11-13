package de.umbratv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Das Zeichen der App: eine Sonnenfinsternis mit goldener Korona. */
public final class EclipseView extends View {
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);

    public EclipseView(Context c) {
        super(c);
        disc.setColor(0xFF050409);
        rim.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float r = Math.min(w, h) / 2f;
        glow.setShader(new RadialGradient(w / 2f, h / 2f, r,
                new int[]{0xFFFFE2B0, 0xFFFFB84D, 0x99FF7A1A, 0x22FF7A1A, 0x00FF7A1A},
                new float[]{0f, 0.52f, 0.62f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        rim.setStrokeWidth(Math.max(1f, r * 0.035f));
        rim.setShader(new RadialGradient(w * 0.66f, h * 0.30f, r * 1.1f,
                new int[]{0xFFFFF4DD, 0x55FFB84D, 0x00FFB84D}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f;
        c.drawCircle(cx, cy, r, glow);
        c.drawCircle(cx - r * 0.03f, cy + r * 0.03f, r * 0.54f, disc);
        c.drawCircle(cx - r * 0.03f, cy + r * 0.03f, r * 0.54f, rim);
    }
}

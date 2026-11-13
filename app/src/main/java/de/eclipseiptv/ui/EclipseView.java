package de.eclipseiptv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Das Zeichen der App: eine Finsternis mit rotem Lichtkranz und weisser Kontur. */
public final class EclipseView extends View {
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);

    public EclipseView(Context c) {
        super(c);
        disc.setColor(0xFF050304);
        rim.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float r = Math.min(w, h) / 2f;
        glow.setShader(new RadialGradient(w / 2f, h / 2f, r,
                new int[]{0xFFFFD6DA, 0xFFFF2B42, 0xAAB00A20, 0x267E0A1B, 0x007E0A1B},
                new float[]{0f, 0.52f, 0.62f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        rim.setStrokeWidth(Math.max(1f, r * 0.035f));
        rim.setShader(new RadialGradient(w * 0.66f, h * 0.30f, r * 1.1f,
                new int[]{0xFFFFFFFF, 0xCCFFFFFF, 0x55FFFFFF}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f;
        c.drawCircle(cx, cy, r, glow);
        c.drawCircle(cx - r * 0.03f, cy + r * 0.03f, r * 0.54f, disc);
        c.drawCircle(cx - r * 0.03f, cy + r * 0.03f, r * 0.54f, rim);
    }
}

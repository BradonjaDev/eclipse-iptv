package de.eclipseiptv.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.PathParser;

/**
 * Gestaltung der App: eine rote Finsternis. Tiefes Schwarz, Blutmond-Rot und duenne
 * weisse Konturen, dazu Wolkenbaender im Stil japanischer Tuschezeichnungen (siehe Clouds)
 * und Bausteine fuer die Oberflaeche. Alle Masse beziehen sich auf eine Flaeche von
 * 960 x 540 Einheiten und werden auf den echten Bildschirm umgerechnet, damit die
 * App auf jedem Fernseher gleich aussieht.
 */
public final class Ui {
    public static final int BG = 0xFF070507;
    public static final int SURFACE = 0xFF120C0F;
    public static final int SURFACE2 = 0xFF1C1217;
    public static final int SURFACE3 = 0xFF2B1820;
    public static final int LINE = 0xFF3A222B;
    public static final int TEXT = 0xFFF7F1EE;
    public static final int DIM = 0xFFBBA9AE;
    public static final int FAINT = 0xFF806A70;
    /** Das Rot des Blutmonds: Flaechen und Rahmen. */
    public static final int ACCENT = 0xFFDD1A34;
    /** Hellere Variante fuer kleine Schrift auf Schwarz. */
    public static final int ACCENT_HOT = 0xFFFF5C6C;
    public static final int ACCENT_DEEP = 0xFF7E0A1B;
    public static final int ON_ACCENT = 0xFFFFFFFF;
    public static final int WHITE = 0xFFFFFFFF;
    /** Warnungen und Fehler (bewusst nicht rot, weil Rot die Akzentfarbe ist). */
    public static final int WARN = 0xFFFFB02E;
    public static final int SCRIM = 0xE6070507;

    private static float scale = 2f;
    public static int screenW = 1920, screenH = 1080;

    private Ui() {
    }

    public static void init(Activity a) {
        DisplayMetrics m = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getRealMetrics(m);
        screenW = Math.max(m.widthPixels, m.heightPixels);
        screenH = Math.min(m.widthPixels, m.heightPixels);
        scale = Math.min(screenW / 960f, screenH / 540f);
        Window w = a.getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        immersive(a);
    }

    public static void immersive(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Rechnet Gestaltungs-Einheiten in Pixel um. */
    public static int dp(float v) {
        return Math.round(v * scale);
    }

    public static float px(float v) {
        return v * scale;
    }

    // ---- Text ------------------------------------------------------------------

    private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    public static TextView text(Context c, float size, int color, boolean strong) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTextColor(color);
        t.setTypeface(strong ? MEDIUM : REGULAR);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView line(Context c, float size, int color, boolean strong) {
        TextView t = text(c, size, color, strong);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static void lines(TextView t, int max) {
        t.setMaxLines(max);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    /** Kleine Ueberschrift in Grossbuchstaben mit Sperrung. */
    public static TextView label(Context c, String s) {
        TextView t = line(c, 9.5f, FAINT, true);
        t.setText(s.toUpperCase());
        t.setLetterSpacing(0.14f);
        return t;
    }

    public static ColorStateList states(int focused, int activated, int normal) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_activated}, {}},
                new int[]{focused, activated, normal});
    }

    // ---- Flaechen --------------------------------------------------------------

    public static GradientDrawable rect(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        return g;
    }

    public static GradientDrawable outline(int fill, float radius, int stroke, float width) {
        GradientDrawable g = rect(fill, radius);
        g.setStroke(Math.max(1, dp(width)), stroke);
        return g;
    }

    public static GradientDrawable gradient(GradientDrawable.Orientation o, int... colors) {
        return new GradientDrawable(o, colors);
    }

    /** Hintergrund fuer Listenzeilen: dunkelrote Flaeche mit rotem Rahmen bei Fokus, dezente Toenung wenn ausgewaehlt. */
    public static Drawable rowBg(float radius) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable f = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF5A0D1C, 0xFF3A0B15});
        f.setCornerRadius(px(radius));
        f.setStroke(Math.max(1, dp(1.4f)), ACCENT_HOT);
        s.addState(new int[]{android.R.attr.state_focused}, f);
        s.addState(new int[]{android.R.attr.state_activated}, rect(0x38DD1A34, radius));
        s.addState(new int[]{}, rect(0x00000000, radius));
        return s;
    }

    /** Rote Flaeche mit weisser Kontur: das Zeichen fuer "hier ist der Fokus". */
    public static GradientDrawable cloak(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFEA2540, 0xFFB80F27});
        g.setCornerRadius(px(radius));
        g.setStroke(Math.max(1, dp(1.3f)), WHITE);
        return g;
    }

    /** Hintergrund fuer Schaltflaechen: rot mit weisser Kontur bei Fokus. */
    public static Drawable buttonBg(float radius) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, cloak(radius));
        s.addState(new int[]{android.R.attr.state_activated}, outline(0xE01C1217, radius, ACCENT, 1.2f));
        s.addState(new int[]{}, rect(0xD01C1217, radius));
        return s;
    }

    /** Rahmen, der sich bei Fokus ueber ein Bild legt: weiss aussen, rot innen. */
    public static Drawable frame(float radius) {
        StateListDrawable s = new StateListDrawable();
        android.graphics.drawable.LayerDrawable focus = new android.graphics.drawable.LayerDrawable(new Drawable[]{
                outline(0x00000000, radius, ACCENT, 3.2f), outline(0x00000000, radius, WHITE, 1.3f)});
        s.addState(new int[]{android.R.attr.state_focused}, focus);
        s.addState(new int[]{}, outline(0x00000000, radius, 0x14FFFFFF, 0.5f));
        return s;
    }

    public static void round(View v, final float radius) {
        if (Build.VERSION.SDK_INT >= 21) {
            v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(radius));
                }
            });
            v.setClipToOutline(true);
        }
    }

    /** Sanftes Vergroessern bei Fokus. */
    public static void lift(final View v, final float factor) {
        final View.OnFocusChangeListener before = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                float to = has ? factor : 1f;
                view.animate().scaleX(to).scaleY(to).setDuration(130).start();
                if (Build.VERSION.SDK_INT >= 21) view.setTranslationZ(has ? px(6) : 0);
                if (before != null) before.onFocusChange(view, has);
            }
        });
    }

    public static void fade(final View v, boolean show) {
        if (show) {
            if (v.getVisibility() != View.VISIBLE) {
                v.setAlpha(0f);
                v.setVisibility(View.VISIBLE);
            }
            v.animate().alpha(1f).setDuration(160).withEndAction(null).start();
        } else if (v.getVisibility() == View.VISIBLE) {
            v.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                @Override
                public void run() {
                    v.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    public static void toast(Context c, String s) {
        Toast t = Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_SHORT);
        t.show();
    }

    // ---- Symbole ---------------------------------------------------------------

    public static final String I_HOME = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
    public static final String I_SEARCH = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
    public static final String I_LIVE = "M21 6h-7.59l3.29-3.29L16 2l-4 4-4-4-.71.71L10.59 6H3c-1.1 0-2 .89-2 2v12c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.11-.9-2-2-2zm0 14H3V8h18v12zM9 10v8l7-4z";
    public static final String I_MOVIE = "M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z";
    public static final String I_SERIES = "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z";
    public static final String I_SETTINGS = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
    public static final String I_STAR = "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z";
    public static final String I_STAR_OFF = "M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z";
    public static final String I_PLAY = "M8 5v14l11-7z";
    public static final String I_PAUSE = "M6 19h4V5H6v14zm8-14v14h4V5h-4z";
    public static final String I_REPLAY = "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z";
    public static final String I_TRAILER = "M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12zm-8-1.5l6-4.5-6-4.5v9z";
    public static final String I_CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    public static final String I_CLOCK = "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z";
    public static final String I_NEW = "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-2 10h-4v4h-2v-4H7v-2h4V7h2v4h4v2z";
    public static final String I_LIST = "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z";
    public static final String I_REFRESH = "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";

    /** Zeichnet ein Symbol aus Pfaddaten (24er-Raster) und faerbt es je nach Zustand. */
    public static final class PathDrawable extends Drawable {
        private final Path src;
        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ColorStateList tint;

        public PathDrawable(String data, int color) {
            src = PathParser.createPathFromPathData(data);
            paint.setColor(color);
        }

        public PathDrawable tint(ColorStateList t) {
            tint = t;
            paint.setColor(t.getColorForState(getState(), t.getDefaultColor()));
            invalidateSelf();
            return this;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            Matrix m = new Matrix();
            float s = Math.min(b.width(), b.height()) / 24f;
            m.setScale(s, s);
            m.postTranslate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
            src.transform(m, path);
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, paint);
        }

        @Override
        public boolean isStateful() {
            return tint != null;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            if (tint == null) return false;
            int col = tint.getColorForState(state, tint.getDefaultColor());
            if (col == paint.getColor()) return false;
            paint.setColor(col);
            invalidateSelf();
            return true;
        }

        @Override
        public void setAlpha(int a) {
            paint.setAlpha(a);
        }

        @Override
        public void setColorFilter(ColorFilter f) {
            paint.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }
    }

    public static ImageView icon(Context c, String data, int color) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new PathDrawable(data, color));
        return v;
    }

    /** Symbol, das die Farbe mit dem Zustand der umgebenden Zeile wechselt. */
    public static ImageView icon(Context c, String data, ColorStateList tint) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new PathDrawable(data, tint.getDefaultColor()).tint(tint));
        v.setDuplicateParentStateEnabled(true);
        return v;
    }

    public static int gravityCenterV() {
        return Gravity.CENTER_VERTICAL;
    }

    public static ValueAnimator pulse(final View v) {
        ValueAnimator a = ValueAnimator.ofFloat(0.35f, 1f);
        a.setDuration(900);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator an) {
                v.setAlpha((Float) an.getAnimatedValue());
            }
        });
        return a;
    }
}

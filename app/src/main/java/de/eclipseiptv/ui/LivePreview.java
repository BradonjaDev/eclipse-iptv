package de.eclipseiptv.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.List;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Epg;
import de.eclipseiptv.data.Playable;
import de.eclipseiptv.data.Programme;
import de.eclipseiptv.data.Source;
import de.eclipseiptv.data.Text;

/**
 * Kleines Bild neben der Senderliste. Kommt man aus dem Vollbild zurueck, laeuft der
 * Sender hier weiter, ohne neu zu laden.
 *
 * Das Video laeuft ueber eine SurfaceView: Der Decoder liefert die Bilder direkt an die
 * Grafik-Hardware, die sie selbst ins Bild setzt. Die App zeichnet dafuer nichts neu, und
 * Animationen oder Blaettern in der Liste koennen das Video nicht ausbremsen. (Eine
 * TextureView wuerde jedes Videobild durch die Oberflaeche der App schleusen.)
 */
public final class LivePreview extends FrameLayout implements Player.Listener {
    private final SurfaceView surface;
    private final View placeholder, overlay;
    private final TextView name, now, hint, badge;
    private final ProgressBar spinner;
    private ExoPlayer player;
    private Source source;
    private int retries;
    private boolean triedAlt;
    /** Fuer welchen Sender die Sendungszeile zuletzt geladen wurde. */
    private Playable epgFor;

    public LivePreview(Context c) {
        super(c);
        setBackgroundColor(0xFF000000);
        setFocusable(false);

        surface = new SurfaceView(c);
        addView(surface, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        LinearLayout ph = new LinearLayout(c);
        ph.setOrientation(LinearLayout.VERTICAL);
        ph.setGravity(Gravity.CENTER);
        ph.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1A0A10, 0xFF07050A}));
        View eclipse = new View(c);
        eclipse.setBackground(new EclipseView.Art(0.75f));
        ph.addView(eclipse, new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(58)));
        hint = Ui.text(c, 9.5f, Ui.DIM, false);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(Ui.px(2), 1f);
        hint.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), 0);
        hint.setText("OK startet einen Sender im Vollbild.\nMit Zurück läuft er hier weiter.");
        ph.addView(hint);
        placeholder = ph;
        addView(ph, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout ov = new LinearLayout(c);
        ov.setOrientation(LinearLayout.VERTICAL);
        ov.setGravity(Gravity.BOTTOM);
        ov.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xE6000000}));
        ov.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        badge = Ui.line(c, 8f, Ui.WHITE, true);
        badge.setText("LIVE");
        badge.setLetterSpacing(0.12f);
        badge.setBackground(Ui.rect(Ui.ACCENT, 4));
        badge.setPadding(Ui.dp(5), Ui.dp(2), Ui.dp(5), Ui.dp(2));
        top.addView(badge);
        name = Ui.line(c, 11.5f, Ui.WHITE, true);
        name.setPadding(Ui.dp(7), 0, 0, 0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ov.addView(top);
        now = Ui.line(c, 9.5f, 0xFFE8DCDF, false);
        now.setPadding(0, Ui.dp(3), 0, 0);
        ov.addView(now);
        overlay = ov;
        addView(ov, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spinner = new ProgressBar(c);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.WHITE));
        spinner.setVisibility(GONE);
        addView(spinner, new LayoutParams(Ui.dp(26), Ui.dp(26), Gravity.CENTER));

        View frame = new View(c);
        frame.setBackground(Ui.outline(0x00000000, 2, 0x4DFFFFFF, 0.8f));
        addView(frame, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        show();
    }

    /** Uebernimmt den gemeinsamen Player, falls gerade ein Sender laeuft. */
    public void attach(Source s) {
        source = s;
        ExoPlayer p = LivePlayer.peek();
        if (p != player) {
            detach();
            player = p;
            if (player != null) {
                player.addListener(this);
                player.setVideoSurfaceView(surface);
                fit(player.getVideoSize());
            }
        } else if (player != null) {
            player.setVideoSurfaceView(surface);
        }
        retries = 0;
        triedAlt = false;
        show();
    }

    public void detach() {
        Bg.cancel(retry);
        epgFor = null;
        if (player != null) {
            player.removeListener(this);
            // Nur wenn der Player noch lebt (er wird freigegeben, sobald die App in den Hintergrund geht)
            if (player == LivePlayer.peek()) player.clearVideoSurfaceView(surface);
        }
        player = null;
    }

    /** Aktualisiert Name, Sendung und Platzhalter. */
    public void show() {
        Playable p = LivePlayer.current();
        boolean on = p != null && player != null && player.getPlaybackState() != Player.STATE_IDLE;
        placeholder.setVisibility(on ? GONE : VISIBLE);
        overlay.setVisibility(on ? VISIBLE : GONE);
        spinner.setVisibility(on && player.getPlaybackState() == Player.STATE_BUFFERING ? VISIBLE : GONE);
        if (!on) {
            hint.setText("OK startet einen Sender im Vollbild.\nMit Zurück läuft er hier weiter.");
            return;
        }
        name.setText(p.title);
        if (p == epgFor) return;
        epgFor = p;
        now.setText("");
        now.setVisibility(GONE);
        if (p.item != null) {
            final Playable shown = p;
            Epg.load(source, p.item, new Epg.Callback() {
                @Override
                public void onEpg(de.eclipseiptv.data.Item item, List<Programme> list) {
                    if (LivePlayer.current() != shown) return;
                    Programme pr = Epg.now(list);
                    now.setText(pr == null ? "" : EpgPanel.hm(pr.start) + " – " + EpgPanel.hm(pr.end) + "   " + pr.title);
                    now.setVisibility(pr == null ? GONE : VISIBLE);
                }
            });
        }
    }

    private void fit(VideoSize v) {
        int w = getWidth(), h = getHeight();
        if (v == null || v.width <= 0 || v.height <= 0 || w <= 0 || h <= 0) return;
        float ratio = v.width * v.pixelWidthHeightRatio / v.height, box = w / (float) h;
        int nw, nh;
        if (Math.abs(ratio - box) < 0.02f) {
            nw = ViewGroup.LayoutParams.MATCH_PARENT;
            nh = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if (ratio > box) {
            nw = w;
            nh = Math.round(w / ratio);
        } else {
            nw = Math.round(h * ratio);
            nh = h;
        }
        LayoutParams lp = (LayoutParams) surface.getLayoutParams();
        if (lp.width == nw && lp.height == nh) return;
        lp.width = nw;
        lp.height = nh;
        surface.setLayoutParams(lp);
    }

    @Override
    public void onVideoSizeChanged(VideoSize v) {
        fit(v);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_READY) retries = 0;
        show();
    }

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            Playable p = LivePlayer.current();
            if (p != null && player != null && isAttachedToWindow()) LivePlayer.play(getContext(), p, triedAlt ? p.altUrl : p.url);
        }
    };

    @Override
    public void onPlayerError(PlaybackException e) {
        Playable p = LivePlayer.current();
        if (p == null || player == null) return;
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition();
            player.prepare();
            return;
        }
        if (!triedAlt && !Text.empty(p.altUrl)) {
            triedAlt = true;
            LivePlayer.play(getContext(), p, p.altUrl);
            return;
        }
        if (retries < 3) {
            retries++;
            Bg.cancel(retry);
            Bg.uiDelayed(retry, 2000L * retries);
            return;
        }
        placeholder.setVisibility(VISIBLE);
        overlay.setVisibility(GONE);
        spinner.setVisibility(GONE);
        hint.setText(p.title + " ist gerade nicht erreichbar.");
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (player != null) {
            final VideoSize v = player.getVideoSize();
            post(new Runnable() {
                @Override
                public void run() {
                    fit(v);
                }
            });
        }
    }

    /**
     * Ist die Vorschau nicht zu sehen (Vollbild offen, App im Hintergrund), meldet sie sich
     * vom Player ab. Sonst wuerden Vorschau und Vollbild bei einer Stoerung gleichzeitig
     * neu verbinden. Beim naechsten attach() meldet sie sich wieder an.
     */
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) detach();
    }

    @Override
    protected void onDetachedFromWindow() {
        detach();
        super.onDetachedFromWindow();
    }
}

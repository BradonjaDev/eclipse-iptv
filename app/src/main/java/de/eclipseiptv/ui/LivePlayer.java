package de.eclipseiptv.ui;

import android.content.Context;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.util.Locale;

import de.eclipseiptv.data.Http;
import de.eclipseiptv.data.Playable;
import de.eclipseiptv.data.Text;

/**
 * Der eine Player fuer Sender. Er wird zwischen Vollbild und der kleinen Vorschau in der
 * Senderliste weitergereicht, damit ein Sender ohne Neuladen weiterlaeuft und immer nur
 * eine Verbindung zum Anbieter offen ist (viele Zugaenge erlauben nur eine).
 *
 * Jede Ansicht, die ihn zeigen kann, meldet sich in onStart an und in onStop ab. Ist
 * keine mehr angemeldet (App im Hintergrund), wird er freigegeben.
 */
public final class LivePlayer {
    private static ExoPlayer player;
    private static Playable current;
    private static int users;

    private LivePlayer() {
    }

    /** Baut einen Player mit den Einstellungen der App (auch fuer Filme verwendet). */
    public static ExoPlayer create(Context c) {
        Context app = c.getApplicationContext();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory().setUserAgent(Http.UA)
                .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15000).setReadTimeoutMs(20000);
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(app)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).setEnableDecoderFallback(true);
        ExoPlayer p = new ExoPlayer.Builder(app, renderers)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(app, http))).build();
        p.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguage(Locale.getDefault().getLanguage()).build());
        return p;
    }

    public static MediaItem item(String url) {
        MediaItem.Builder b = new MediaItem.Builder().setUri(url);
        String low = url.toLowerCase(Locale.ROOT);
        if (low.contains(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (low.contains(".mpd")) b.setMimeType(MimeTypes.APPLICATION_MPD);
        return b.build();
    }

    public static ExoPlayer get(Context c) {
        if (player == null) player = create(c);
        return player;
    }

    /** Der Player, falls es ihn gerade gibt (sonst null). */
    public static ExoPlayer peek() {
        return player;
    }

    public static Playable current() {
        return player == null ? null : current;
    }

    /** Laeuft (oder laedt) gerade genau dieser Sender? */
    public static boolean isOn(Playable p) {
        return p != null && player != null && current != null && p.markExt.equals(current.markExt)
                && player.getPlaybackState() != Player.STATE_IDLE && player.getPlaybackState() != Player.STATE_ENDED;
    }

    /** Spielt einen Sender ueber die angegebene Adresse (Haupt- oder Ersatzadresse). */
    public static void play(Context c, Playable p, String url) {
        ExoPlayer pl = get(c);
        current = p;
        if (Text.empty(url)) return;
        pl.setMediaItem(item(url));
        pl.prepare();
        pl.setPlayWhenReady(true);
    }

    /** Haelt an und gibt die Verbindung frei, behaelt den Player aber. */
    public static void stop() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        current = null;
    }

    public static void attach() {
        users++;
    }

    public static void detach() {
        users = Math.max(0, users - 1);
        if (users == 0) release();
    }

    public static void release() {
        if (player != null) player.release();
        player = null;
        current = null;
    }
}

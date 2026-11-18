package de.eclipseiptv;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Db;
import de.eclipseiptv.data.Epg;
import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.PlayQueue;
import de.eclipseiptv.data.Playable;
import de.eclipseiptv.data.Prefs;
import de.eclipseiptv.data.Programme;
import de.eclipseiptv.data.Source;
import de.eclipseiptv.data.Text;
import de.eclipseiptv.ui.Cards;
import de.eclipseiptv.ui.EpgPanel;
import de.eclipseiptv.ui.Img;
import de.eclipseiptv.ui.LivePlayer;
import de.eclipseiptv.ui.Sheet;
import de.eclipseiptv.ui.TvRecycler;
import de.eclipseiptv.ui.Ui;

/** Der Player fuer Sender, Filme und Folgen. */
public final class PlayerActivity extends Activity implements Player.Listener {
    private static final String[] RESIZE = {"Anpassen", "Strecken", "Zoomen"};

    private Db db;
    private Prefs prefs;
    private Source source;
    private List<Playable> queue;
    private int index, lastIndex = -1;
    private Playable cur;
    private boolean live;

    private ExoPlayer player;
    private PlayerView video;
    private FrameLayout root;
    private ProgressBar spinner;
    private TextView message, number;

    // Sender: Einblendung unten und Senderliste links
    private LinearLayout banner;
    private ImageView bLogo;
    private TextView bName, bNow, bTime, bNext, bClock, bDesc;
    private Bar bBar;
    private LinearLayout listPanel;
    private TvRecycler list;
    private Cards listCards;

    // Filme: Steuerleiste unten
    private LinearLayout controls;
    private TextView cTitle, cSub, cPos, cDur, cHint;
    private ImageView cState;
    private Bar cBar;

    private int retries;
    private boolean triedAlt, okLong, started, ready;
    private long scrub = -1, resumePos = -1, lastSave;
    private String digits = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        db = Db.get(this);
        prefs = new Prefs(this);
        source = PlayQueue.source;
        queue = PlayQueue.list;
        if (source == null || queue == null || queue.isEmpty()) {
            finish();
            return;
        }
        index = Math.max(0, Math.min(PlayQueue.index, queue.size() - 1));
        live = queue.get(index).live;
        ready = true;
        build();
    }

    // ---- Aufbau ----------------------------------------------------------------

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        video = new PlayerView(this);
        video.setUseController(false);
        video.setFocusable(false);
        video.setResizeMode(resizeMode(prefs.resize()));
        video.setShutterBackgroundColor(0xFF000000);
        root.addView(video, match());

        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ACCENT));
        root.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(44), Ui.dp(44), Gravity.CENTER));

        message = Ui.text(this, 13f, Ui.TEXT, false);
        message.setGravity(Gravity.CENTER);
        message.setBackground(Ui.rect(0xE6120C0F, 12));
        message.setPadding(Ui.dp(22), Ui.dp(14), Ui.dp(22), Ui.dp(14));
        message.setLineSpacing(Ui.px(4), 1f);
        message.setVisibility(View.GONE);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        mp.leftMargin = mp.rightMargin = Ui.dp(120);
        root.addView(message, mp);

        number = Ui.text(this, 34f, Ui.ACCENT_HOT, true);
        number.setBackground(Ui.rect(0xD9120C0F, 12));
        number.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(18), Ui.dp(8));
        number.setVisibility(View.GONE);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        np.topMargin = np.rightMargin = Ui.dp(28);
        root.addView(number, np);

        if (live) {
            buildBanner();
            buildList();
        } else {
            buildControls();
        }
        // Tippen auf dem Touchscreen wirkt wie OK. Kein Klick-Listener: der wuerde die OK-Taste
        // der Fernbedienung abfangen, bevor langes Druecken erkannt werden kann.
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_UP && !listOpen()) ok();
                return true;
            }
        });
        setContentView(root);
        Ui.noWindowBackground(this);
        root.requestFocus();
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static int resizeMode(int i) {
        return i == 1 ? AspectRatioFrameLayout.RESIZE_MODE_FILL : i == 2 ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT;
    }

    private void buildBanner() {
        banner = new LinearLayout(this);
        banner.setGravity(Gravity.BOTTOM);
        banner.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xCC07060B, 0xF707060B}));
        banner.setPadding(Ui.dp(40), Ui.dp(70), Ui.dp(40), Ui.dp(30));
        banner.setVisibility(View.GONE);
        bLogo = new ImageView(this);
        bLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bLogo.setBackground(Ui.rect(0x1FFFFFFF, 10));
        bLogo.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        banner.addView(bLogo, new LinearLayout.LayoutParams(Ui.dp(96), Ui.dp(62)));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = Ui.dp(18);
        lp.rightMargin = Ui.dp(24);
        banner.addView(mid, lp);
        bName = Ui.line(this, 18f, Ui.TEXT, true);
        mid.addView(bName);
        bNow = Ui.line(this, 12.5f, Ui.ACCENT_HOT, false);
        bNow.setPadding(0, Ui.dp(5), 0, 0);
        mid.addView(bNow);
        bDesc = Ui.text(this, 10.5f, Ui.DIM, false);
        Ui.lines(bDesc, 2);
        bDesc.setLineSpacing(Ui.px(2.5f), 1f);
        bDesc.setPadding(0, Ui.dp(4), 0, 0);
        mid.addView(bDesc);
        bBar = new Bar(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(3));
        bp.topMargin = Ui.dp(8);
        mid.addView(bBar, bp);
        LinearLayout foot = new LinearLayout(this);
        foot.setPadding(0, Ui.dp(6), 0, 0);
        bTime = Ui.line(this, 10f, Ui.DIM, false);
        foot.addView(bTime);
        bNext = Ui.line(this, 10f, Ui.FAINT, false);
        bNext.setPadding(Ui.dp(18), 0, 0, 0);
        foot.addView(bNext, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        mid.addView(foot);
        bClock = Ui.line(this, 20f, Ui.TEXT, false);
        banner.addView(bClock);
        root.addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    private void buildList() {
        listPanel = new LinearLayout(this);
        listPanel.setOrientation(LinearLayout.VERTICAL);
        listPanel.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFA07060B, 0xF007060B, 0xC807060B}));
        listPanel.setPadding(Ui.dp(16), Ui.dp(22), Ui.dp(14), 0);
        listPanel.setVisibility(View.GONE);
        TextView t = Ui.line(this, 15f, Ui.TEXT, true);
        t.setText(Text.empty(PlayQueue.listName) ? "Sender" : PlayQueue.listName);
        t.setPadding(Ui.dp(8), 0, 0, Ui.dp(10));
        listPanel.addView(t);
        listCards = new Cards(Cards.ROW, 0, new Cards.Listener() {
            @Override
            public void onClick(Cards.Entry e, int position) {
                closeList();
                if (position != index) tune(position);
                else showBanner();
            }

            @Override
            public boolean onLong(Cards.Entry e, int position) {
                return false;
            }
        });
        list = new TvRecycler(this).vertical().lockAxis(true);
        list.setPadding(0, Ui.dp(2), 0, Ui.dp(20));
        list.setAdapter(listCards);
        listPanel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(listPanel, new FrameLayout.LayoutParams(Ui.dp(340), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
    }

    private void buildControls() {
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xCC07060B, 0xF707060B}));
        controls.setPadding(Ui.dp(44), Ui.dp(80), Ui.dp(44), Ui.dp(26));
        controls.setVisibility(View.GONE);
        cTitle = Ui.line(this, 19f, Ui.TEXT, true);
        controls.addView(cTitle);
        cSub = Ui.line(this, 11.5f, Ui.ACCENT_HOT, false);
        cSub.setPadding(0, Ui.dp(4), 0, 0);
        controls.addView(cSub);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(14), 0, 0);
        cState = new ImageView(this);
        row.addView(cState, new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        cPos = Ui.line(this, 11.5f, Ui.TEXT, false);
        cPos.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        row.addView(cPos);
        cBar = new Bar(this);
        row.addView(cBar, new LinearLayout.LayoutParams(0, Ui.dp(5), 1));
        cDur = Ui.line(this, 11.5f, Ui.DIM, false);
        cDur.setPadding(Ui.dp(12), 0, 0, 0);
        row.addView(cDur);
        controls.addView(row);
        cHint = Ui.line(this, 9.5f, Ui.FAINT, false);
        cHint.setPadding(Ui.dp(34), Ui.dp(10), 0, 0);
        cHint.setText("OK  Pause      ◀ ▶  Spulen      ▼  Ton, Untertitel und Bildformat");
        controls.addView(cHint);
        root.addView(controls, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    /** Schmaler Fortschrittsbalken. */
    private static final class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float value, buffered;

        Bar(Context c) {
            super(c);
        }

        void set(float v, float b) {
            value = Math.max(0f, Math.min(1f, v));
            buffered = Math.max(0f, Math.min(1f, b));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth(), rad = h / 2f;
            r.set(0, 0, w, h);
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(r, rad, rad, p);
            if (buffered > 0) {
                r.set(0, 0, w * buffered, h);
                p.setColor(0x55FFFFFF);
                c.drawRoundRect(r, rad, rad, p);
            }
            if (value > 0) {
                r.set(0, 0, Math.max(h, w * value), h);
                p.setColor(Ui.ACCENT);
                c.drawRoundRect(r, rad, rad, p);
            }
        }
    }

    // ---- Lebenszyklus ----------------------------------------------------------

    @Override
    protected void onStart() {
        super.onStart();
        if (!ready) return;
        if (live) {
            // Sender laufen ueber den gemeinsamen Player, damit sie in der Vorschau weiterlaufen koennen.
            LivePlayer.attach();
            player = LivePlayer.get(this);
        } else {
            LivePlayer.release();
            player = LivePlayer.create(this);
        }
        player.addListener(this);
        video.setPlayer(player);
        Playable want = queue.get(index);
        if (live && LivePlayer.isOn(want)) {
            // Laeuft schon (aus der Vorschau): nur uebernehmen, nicht neu laden.
            cur = want;
            retries = 0;
            triedAlt = false;
            started = player.getPlaybackState() == Player.STATE_READY;
            spinner.setVisibility(player.getPlaybackState() == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
            showBanner();
            loadEpg();
            Bg.cancel(markWatched);
            Bg.uiDelayed(markWatched, 8000);
        } else {
            start(resumePos);
        }
        tick.run();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    @Override
    protected void onStop() {
        Bg.cancel(tick);
        Bg.cancel(retry);
        Bg.cancel(hideBanner);
        Bg.cancel(hideControls);
        Bg.cancel(commitScrub);
        Bg.cancel(commitNumber);
        Bg.cancel(markWatched);
        if (player != null) {
            save(false);
            resumePos = live ? -1 : player.getCurrentPosition();
            video.setPlayer(null);
            player.removeListener(this);
            if (live) LivePlayer.detach();
            else player.release();
            player = null;
        }
        super.onStop();
    }

    /** Startet den aktuellen Eintrag der Liste. from: Startposition, kleiner 0 = aus dem Eintrag. */
    private void start(long from) {
        cur = queue.get(index);
        cur.fresh();
        retries = 0;
        triedAlt = false;
        started = false;
        scrub = -1;
        hideMessage();
        open(cur.url, from >= 0 ? from : cur.startPos);
        if (live) {
            showBanner();
            loadEpg();
            Bg.cancel(markWatched);
            Bg.uiDelayed(markWatched, 8000);
        } else {
            cTitle.setText(cur.title);
            cSub.setText(cur.subtitle);
            cSub.setVisibility(cur.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
            showControls();
        }
    }

    private void open(String url, long from) {
        if (player == null) return;
        if (Text.empty(url)) {
            showMessage("Für diesen Eintrag gibt es keinen Link.");
            return;
        }
        if (live) {
            LivePlayer.play(this, cur, url);
            return;
        }
        if (from > 0) player.setMediaItem(LivePlayer.item(url), from);
        else player.setMediaItem(LivePlayer.item(url));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private final Runnable markWatched = new Runnable() {
        @Override
        public void run() {
            if (cur != null && live) db.saveProgress(source.id, Item.LIVE, cur.markExt, cur.markName, cur.markIcon, "", 0, 0);
        }
    };

    private void save(boolean finished) {
        if (live || player == null || cur == null || !started) return;
        long dur = player.getDuration(), pos = player.getCurrentPosition();
        if (dur <= 0 || dur == C.TIME_UNSET) dur = 0;
        if (finished && dur > 0) pos = dur;
        if (pos < 5000 && !finished) return;
        db.saveProgress(source.id, cur.markKind, cur.markExt, cur.markName, cur.markIcon, cur.markParent, pos, dur);
        lastSave = System.currentTimeMillis();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            if (live) {
                if (banner.getVisibility() == View.VISIBLE) bClock.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date()));
            } else {
                if (controls.getVisibility() == View.VISIBLE) updateControls();
                if (player.isPlaying() && System.currentTimeMillis() - lastSave > 15000) save(false);
            }
            Bg.uiDelayed(this, 500);
        }
    };

    // ---- Ereignisse des Players ------------------------------------------------

    @Override
    public void onPlaybackStateChanged(int state) {
        spinner.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
        if (state == Player.STATE_READY) {
            started = true;
            if (cur != null) cur.works();
            hideMessage();
        } else if (state == Player.STATE_ENDED) {
            if (live) {
                Bg.cancel(retry);
                Bg.uiDelayed(retry, 1000);
            } else {
                save(true);
                if (index < queue.size() - 1) {
                    index++;
                    PlayQueue.index = index;
                    start(0);
                } else {
                    finish();
                }
            }
        }
    }

    @Override
    public void onIsPlayingChanged(boolean playing) {
        if (!live && controls != null) {
            updateControls();
            if (playing) scheduleHideControls();
            else showControls();
        }
        if (playing) retries = 0;
    }

    @Override
    public void onPlayerError(PlaybackException e) {
        if (player == null) return;
        spinner.setVisibility(View.GONE);
        if (live) {
            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition();
                player.prepare();
                return;
            }
            // Antwortet der Server gar nicht, hilft das andere Format nicht: gleich zum naechsten Server.
            if (LivePlayer.serverDown(e) && !started && retries == 0 && !triedAlt && switchServer(0)) return;
            if (!triedAlt && !Text.empty(cur.altUrl) && !started) {
                triedAlt = true;
                open(cur.altUrl, 0);
                return;
            }
            if (retries < 4) {
                retries++;
                showMessage("Verbindung wird neu aufgebaut …");
                Bg.cancel(retry);
                Bg.uiDelayed(retry, 1500L * retries);
                return;
            }
            if (switchServer(0)) return;
            showMessage("Dieser Sender ist gerade nicht erreichbar.\n" + reason(e));
        } else {
            long pos = Math.max(0, player.getCurrentPosition());
            if (pos <= 0 && !started) pos = Math.max(0, cur.startPos);
            if (LivePlayer.serverDown(e) && !started && retries == 0 && switchServer(pos)) return;
            if (retries < 2) {
                retries++;
                player.seekTo(pos);
                player.prepare();
                return;
            }
            // Filme setzen auf dem anderen Server an der letzten Position fort.
            if (switchServer(pos)) return;
            showMessage("Das Video kann nicht abgespielt werden.\n" + reason(e));
        }
    }

    /** Wechselt zum naechsten Ausweich-Server. False, wenn es keinen mehr gibt. */
    private boolean switchServer(long from) {
        if (cur == null || !cur.nextServer()) return false;
        retries = 0;
        triedAlt = false;
        Bg.cancel(retry);
        showMessage("Server antwortet nicht – wechsle zu " + cur.host() + " …");
        open(cur.url, live ? 0 : from);
        return true;
    }

    private static String reason(PlaybackException e) {
        int c = e.errorCode;
        if (c == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return "Der Anbieter lehnt den Abruf ab – vielleicht läuft der Zugang schon auf einem anderen Gerät.";
        if (c == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || c == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
            return "Keine Verbindung zum Server.";
        if (c >= 4000 && c < 5000) return "Dieses Format unterstützt das Gerät nicht.";
        if (c >= 3000 && c < 4000) return "Der Datenstrom ist fehlerhaft.";
        return "Bitte später noch einmal versuchen.";
    }

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            if (player == null || cur == null) return;
            open(triedAlt && !started ? cur.altUrl : cur.url, live ? 0 : Math.max(0, player.getCurrentPosition()));
        }
    };

    private void showMessage(String s) {
        message.setText(s);
        message.setVisibility(View.VISIBLE);
    }

    private void hideMessage() {
        message.setVisibility(View.GONE);
    }

    // ---- Sender ----------------------------------------------------------------

    private void tune(int to) {
        if (to < 0 || to >= queue.size() || player == null) return;
        if (to != index) lastIndex = index;
        index = to;
        PlayQueue.index = index;
        start(0);
    }

    private void zap(int delta) {
        int n = queue.size();
        if (n < 2) {
            showBanner();
            return;
        }
        tune(((index + delta) % n + n) % n);
    }

    private void showBanner() {
        Item it = cur.item;
        bName.setText((it != null && it.num > 0 ? it.num + "   " : "") + cur.title);
        Img.load(bLogo, cur.icon, Ui.dp(96), Ui.dp(62), false);
        bClock.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date()));
        bindEpg(it == null ? null : Epg.cached(it));
        Ui.fade(banner, true);
        Bg.cancel(hideBanner);
        Bg.uiDelayed(hideBanner, 6500);
    }

    private final Runnable hideBanner = new Runnable() {
        @Override
        public void run() {
            Ui.fade(banner, false);
        }
    };

    private void loadEpg() {
        final Item it = cur.item;
        if (it == null) return;
        Epg.load(source, it, new Epg.Callback() {
            @Override
            public void onEpg(Item item, List<Programme> l) {
                if (cur != null && cur.item == item && banner != null) bindEpg(l);
            }
        });
    }

    private void bindEpg(List<Programme> l) {
        Programme now = Epg.now(l);
        List<Programme> next = Epg.next(l);
        bNow.setText(now == null ? "" : now.title);
        bNow.setVisibility(now == null ? View.GONE : View.VISIBLE);
        bDesc.setText(now == null ? "" : now.desc);
        bDesc.setVisibility(now == null || Text.empty(now.desc) ? View.GONE : View.VISIBLE);
        bBar.setVisibility(now == null ? View.INVISIBLE : View.VISIBLE);
        if (now != null) {
            bBar.set((System.currentTimeMillis() - now.start) / (float) (now.end - now.start), 0);
            bTime.setText(EpgPanel.hm(now.start) + " – " + EpgPanel.hm(now.end));
        } else {
            bTime.setText("");
        }
        bNext.setText(next.isEmpty() ? "" : "Danach  " + EpgPanel.hm(next.get(0).start) + "  " + next.get(0).title);
    }

    private boolean listOpen() {
        return listPanel != null && listPanel.getVisibility() == View.VISIBLE;
    }

    private void openList() {
        if (listCards.getItemCount() == 0) {
            List<Cards.Entry> entries = new ArrayList<>(queue.size());
            for (Playable p : queue) {
                Cards.Entry e = new Cards.Entry();
                e.item = p.item;
                e.title = p.title;
                e.image = p.icon;
                entries.add(e);
            }
            listCards.set(entries);
        }
        Bg.cancel(hideBanner);
        banner.setVisibility(View.GONE);
        Ui.fade(listPanel, true);
        list.jumpTo(index, true);
    }

    private void closeList() {
        listPanel.setVisibility(View.GONE);
        root.requestFocus();
    }

    private void digit(int d) {
        if (digits.length() >= 5) digits = "";
        digits += d;
        number.setText(digits);
        number.setVisibility(View.VISIBLE);
        Bg.cancel(commitNumber);
        Bg.uiDelayed(commitNumber, 1700);
    }

    private final Runnable commitNumber = new Runnable() {
        @Override
        public void run() {
            number.setVisibility(View.GONE);
            int n;
            try {
                n = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                n = -1;
            }
            digits = "";
            for (int i = 0; i < queue.size(); i++) {
                Item it = queue.get(i).item;
                if (it != null && it.num == n) {
                    tune(i);
                    return;
                }
            }
            Ui.toast(PlayerActivity.this, "Sender " + n + " ist nicht in dieser Liste.");
        }
    };

    // ---- Filme -----------------------------------------------------------------

    private void showControls() {
        updateControls();
        Ui.fade(controls, true);
        scheduleHideControls();
    }

    private void scheduleHideControls() {
        Bg.cancel(hideControls);
        if (player != null && player.isPlaying() && scrub < 0) Bg.uiDelayed(hideControls, 4500);
    }

    private final Runnable hideControls = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying() && scrub < 0) Ui.fade(controls, false);
        }
    };

    private void updateControls() {
        if (player == null) return;
        long dur = player.getDuration();
        if (dur == C.TIME_UNSET || dur < 0) dur = 0;
        long pos = scrub >= 0 ? scrub : player.getCurrentPosition();
        cPos.setText(Text.clock((int) (pos / 1000)));
        cDur.setText(dur > 0 ? Text.clock((int) (dur / 1000)) : "");
        cBar.set(dur > 0 ? pos / (float) dur : 0, dur > 0 ? player.getBufferedPosition() / (float) dur : 0);
        boolean playing = player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED;
        cState.setImageDrawable(new Ui.PathDrawable(playing ? Ui.I_PLAY : Ui.I_PAUSE, playing ? Ui.ACCENT : Ui.TEXT));
    }

    private void togglePlay() {
        if (player == null) return;
        player.setPlayWhenReady(!player.getPlayWhenReady());
        if (live) showBanner();
        else showControls();
    }

    private void scrubBy(long deltaMs) {
        if (player == null) return;
        long dur = player.getDuration();
        if (dur == C.TIME_UNSET || dur <= 0) return;
        if (scrub < 0) scrub = player.getCurrentPosition();
        scrub = Math.max(0, Math.min(dur - 3000, scrub + deltaMs));
        Bg.cancel(hideControls);
        Ui.fade(controls, true);
        updateControls();
        Bg.cancel(commitScrub);
        Bg.uiDelayed(commitScrub, 500);
    }

    private final Runnable commitScrub = new Runnable() {
        @Override
        public void run() {
            if (player != null && scrub >= 0) player.seekTo(scrub);
            scrub = -1;
            scheduleHideControls();
        }
    };

    private static long step(int repeat) {
        return repeat < 6 ? 10000 : repeat < 18 ? 30000 : repeat < 40 ? 60000 : 180000;
    }

    private void jumpInQueue(int delta) {
        int to = index + delta;
        if (to < 0 || to >= queue.size()) return;
        save(false);
        index = to;
        PlayQueue.index = index;
        start(0);
    }

    // ---- Optionen --------------------------------------------------------------

    private static final class Track {
        Tracks.Group group;
        int index;
        String label;
        boolean selected;
    }

    private List<Track> tracks(int type) {
        List<Track> out = new ArrayList<>();
        if (player == null) return out;
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                Track t = new Track();
                t.group = g;
                t.index = i;
                t.selected = g.isTrackSelected(i);
                t.label = label(g.getTrackFormat(i), out.size() + 1, type);
                out.add(t);
            }
        }
        return out;
    }

    private static String label(Format f, int n, int type) {
        StringBuilder b = new StringBuilder();
        if (f.language != null && !f.language.isEmpty() && !"und".equals(f.language)) {
            String lang = new Locale(f.language).getDisplayLanguage(Locale.GERMAN);
            b.append(lang.isEmpty() ? f.language : Character.toUpperCase(lang.charAt(0)) + lang.substring(1));
        }
        if (f.label != null && !f.label.isEmpty() && b.indexOf(f.label) < 0) {
            if (b.length() > 0) b.append(" – ");
            b.append(f.label);
        }
        if (b.length() == 0) b.append(type == C.TRACK_TYPE_AUDIO ? "Tonspur " : "Untertitel ").append(n);
        if (type == C.TRACK_TYPE_AUDIO) {
            String mime = f.sampleMimeType == null ? "" : f.sampleMimeType;
            String codec = mime.contains("eac3") ? "Dolby Digital+" : mime.contains("ac3") ? "Dolby Digital" : mime.contains("dts") ? "DTS"
                    : mime.contains("true-hd") ? "TrueHD" : mime.contains("mp4a") ? "AAC" : mime.contains("mpeg") ? "MP3" : "";
            String ch = f.channelCount == 6 ? "5.1" : f.channelCount == 8 ? "7.1" : f.channelCount == 2 ? "Stereo" : f.channelCount == 1 ? "Mono" : "";
            if (!codec.isEmpty() || !ch.isEmpty()) b.append("  (").append((codec + " " + ch).trim()).append(")");
        }
        return b.toString();
    }

    private void options() {
        if (player == null) return;
        final List<Track> audio = tracks(C.TRACK_TYPE_AUDIO), subs = tracks(C.TRACK_TYPE_TEXT);
        Sheet s = new Sheet(this, live ? cur.title : "Wiedergabe");
        if (live && cur.item != null) {
            final boolean fav = db.isFav(cur.item);
            s.add(fav ? "Aus den Favoriten entfernen" : "Zu den Favoriten hinzufügen", null, new Runnable() {
                @Override
                public void run() {
                    db.setFav(cur.item, !fav);
                    Ui.toast(PlayerActivity.this, fav ? "Aus den Favoriten entfernt" : "Zu den Favoriten hinzugefügt");
                }
            });
        }
        if (audio.size() > 1) {
            s.add("Tonspur", selected(audio, "Standard"), new Runnable() {
                @Override
                public void run() {
                    pick("Tonspur", C.TRACK_TYPE_AUDIO, audio, false);
                }
            });
        }
        if (!subs.isEmpty()) {
            s.add("Untertitel", selected(subs, "Aus"), new Runnable() {
                @Override
                public void run() {
                    pick("Untertitel", C.TRACK_TYPE_TEXT, subs, true);
                }
            });
        }
        s.add("Bildformat", RESIZE[prefs.resize() % 3], new Runnable() {
            @Override
            public void run() {
                int r = (prefs.resize() + 1) % 3;
                prefs.resize(r);
                video.setResizeMode(resizeMode(r));
                options();
            }
        });
        if (!live && queue.size() > 1) {
            if (index < queue.size() - 1) {
                s.add("Nächste Folge", null, new Runnable() {
                    @Override
                    public void run() {
                        jumpInQueue(1);
                    }
                });
            }
            if (index > 0) {
                s.add("Vorherige Folge", null, new Runnable() {
                    @Override
                    public void run() {
                        jumpInQueue(-1);
                    }
                });
            }
        }
        if (!live) {
            s.add("Von vorn beginnen", null, new Runnable() {
                @Override
                public void run() {
                    if (player != null) player.seekTo(0);
                }
            });
        }
        s.show();
    }

    private static String selected(List<Track> l, String none) {
        for (Track t : l) if (t.selected) return t.label;
        return none;
    }

    private void pick(String title, final int type, List<Track> l, boolean allowOff) {
        Sheet s = new Sheet(this, title);
        boolean any = false;
        for (Track t : l) any |= t.selected;
        if (allowOff) {
            s.add("Aus", null, !any, new Runnable() {
                @Override
                public void run() {
                    if (player != null) player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type, true).build());
                }
            });
        }
        for (final Track t : l) {
            s.add(t.label, null, t.selected, new Runnable() {
                @Override
                public void run() {
                    if (player == null) return;
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(type, false)
                            .setOverrideForType(new TrackSelectionOverride(t.group.getMediaTrackGroup(), t.index)).build());
                }
            });
        }
        s.show();
    }

    // ---- Tasten ----------------------------------------------------------------

    private void ok() {
        if (live) {
            if (listOpen()) closeList();
            else openList();
        } else {
            togglePlay();
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (player == null) return super.onKeyDown(code, e);
        if (listOpen()) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                closeList();
                return true;
            }
            return super.onKeyDown(code, e);
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) {
                    okLong = false;
                    e.startTracking();
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                options();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                togglePlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                player.setPlayWhenReady(true);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                player.setPlayWhenReady(false);
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                finish();
                return true;
            default:
        }
        if (live) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    zap(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    zap(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_LAST_CHANNEL:
                    if (e.getRepeatCount() == 0) {
                        if (lastIndex >= 0 && lastIndex != index) tune(lastIndex);
                        else showBanner();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_INFO:
                case KeyEvent.KEYCODE_GUIDE:
                    if (banner.getVisibility() == View.VISIBLE && e.getRepeatCount() == 0) hideBanner.run();
                    else showBanner();
                    return true;
                default:
            }
            if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                digit(code - KeyEvent.KEYCODE_0);
                return true;
            }
            if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
                digit(code - KeyEvent.KEYCODE_NUMPAD_0);
                return true;
            }
        } else {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    scrubBy(-step(e.getRepeatCount()));
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    scrubBy(step(e.getRepeatCount()));
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    scrubBy(-30000);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    scrubBy(30000);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_INFO:
                    showControls();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    options();
                    return true;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    jumpInQueue(1);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    jumpInQueue(-1);
                    return true;
                default:
            }
        }
        return super.onKeyDown(code, e);
    }

    @Override
    public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            okLong = true;
            options();
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (!okLong && !e.isCanceled() && player != null && !listOpen() && e.isTracking()) ok();
            okLong = false;
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override
    public void onBackPressed() {
        if (listOpen()) {
            closeList();
            return;
        }
        if (!live && controls != null && controls.getVisibility() == View.VISIBLE && player != null && player.isPlaying()) {
            Bg.cancel(hideControls);
            Ui.fade(controls, false);
            return;
        }
        super.onBackPressed();
    }
}

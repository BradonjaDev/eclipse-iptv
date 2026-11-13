package de.eclipseiptv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Db;
import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.Mark;
import de.eclipseiptv.data.PlayQueue;
import de.eclipseiptv.data.Playable;
import de.eclipseiptv.data.Prefs;
import de.eclipseiptv.data.Source;
import de.eclipseiptv.data.Sync;
import de.eclipseiptv.ui.BrowseView;
import de.eclipseiptv.ui.Clouds;
import de.eclipseiptv.ui.EclipseView;
import de.eclipseiptv.ui.LivePlayer;
import de.eclipseiptv.ui.HomeView;
import de.eclipseiptv.ui.Host;
import de.eclipseiptv.ui.SearchView;
import de.eclipseiptv.ui.Section;
import de.eclipseiptv.ui.SettingsView;
import de.eclipseiptv.ui.Ui;

/** Hauptansicht: Leiste links, daneben der gewaehlte Bereich. */
public final class MainActivity extends Activity implements Host, Sync.Listener {
    private static final int SEARCH = 0, HOME = 1, LIVE = 2, VOD = 3, SERIES = 4, SETTINGS = 5;
    private static final String[] LABELS = {"Suche", "Start", "Sender", "Filme", "Serien", "Optionen"};
    private static final String[] ICONS = {Ui.I_SEARCH, Ui.I_HOME, Ui.I_LIVE, Ui.I_MOVIE, Ui.I_SERIES, Ui.I_SETTINGS};
    private static boolean autoRefreshChecked;

    private Db db;
    private Prefs prefs;
    private Source source;
    private Rail rail;
    private FrameLayout content;
    private final View[] buttons = new View[6];
    private final View[] sections = new View[6];
    private int current = -1, pending = HOME;
    private boolean setupOpened, wasRunning;
    private long lastBack;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        db = Db.get(this);
        prefs = new Prefs(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(backdrop());
        root.addView(Clouds.background(this), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        rail = new Rail(this);
        row.addView(rail, new LinearLayout.LayoutParams(Ui.dp(76), ViewGroup.LayoutParams.MATCH_PARENT));
        content = new FrameLayout(this);
        content.setClipChildren(false);
        row.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        if (state != null) pending = state.getInt("section", HOME);
    }

    /** Schwarzer Hintergrund mit rotem Mondlicht aus der oberen Ecke. Die Wolken liegen als eigene Ebene darueber. */
    public static Drawable backdrop() {
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{0x4DC20E26, 0x187E0A1B, 0x00000000});
        glow.setGradientCenter(0.92f, -0.1f);
        glow.setGradientRadius(Ui.px(540));
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0C070A, Ui.BG, 0xFF040304});
        return new LayerDrawable(new Drawable[]{base, glow});
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("section", current < 0 ? HOME : current);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Sync.add(this);
        LivePlayer.attach();
    }

    @Override
    protected void onStop() {
        Sync.remove(this);
        LivePlayer.detach();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        int before = source == null ? 0 : source.id;
        resolveSource();
        if (source == null) {
            if (setupOpened && !Sync.running()) {
                finish();
                return;
            }
            setupOpened = true;
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        setupOpened = false;
        // Die Vorschau gibt es nur bei den Sendern; anderswo soll kein Ton im Hintergrund weiterlaufen.
        if (current != LIVE) LivePlayer.stop();
        if (current < 0) {
            switchTo(pending);
            buttons[current].requestFocus();
        } else if (before != source.id) {
            invalidate();
        } else {
            ((Section) sections[current]).refresh();
        }
        if (!autoRefreshChecked) {
            autoRefreshChecked = true;
            if (prefs.autoRefresh() && System.currentTimeMillis() - source.updated > 24 * 3600 * 1000L) Sync.start(this, source, false);
        }
    }

    private void resolveSource() {
        source = db.source(prefs.source());
        if (source == null) {
            List<Source> all = db.sources();
            if (!all.isEmpty()) {
                source = all.get(0);
                prefs.source(source.id);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) Ui.immersive(this);
    }

    // ---- Bereiche --------------------------------------------------------------

    private void switchTo(int i) {
        if (i == current && sections[i] != null) return;
        if (sections[i] == null) {
            View v;
            if (i == SEARCH) v = new SearchView(this, this);
            else if (i == HOME) v = new HomeView(this, this);
            else if (i == SETTINGS) v = new SettingsView(this, this);
            else v = new BrowseView(this, this, i == LIVE ? Item.LIVE : i == VOD ? Item.VOD : Item.SERIES);
            sections[i] = v;
        }
        if (i != LIVE) LivePlayer.stop();
        current = i;
        content.removeAllViews();
        content.addView(sections[i], new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int k = 0; k < buttons.length; k++) buttons[k].setActivated(k == i);
        ((Section) sections[i]).refresh();
    }

    @Override
    public void invalidate() {
        resolveSource();
        if (source == null || current < 0) return;
        for (int i = 0; i < sections.length; i++) if (i != current) sections[i] = null;
        View v = sections[current];
        if (v instanceof BrowseView) ((BrowseView) v).reload();
        else ((Section) v).refresh();
    }

    @Override
    public void onSync() {
        boolean running = Sync.running();
        if (running == wasRunning) return;
        wasRunning = running;
        if (running) {
            if (current == HOME || current == SETTINGS) ((Section) sections[current]).refresh();
            return;
        }
        String err = Sync.error();
        if (err != null) {
            Ui.toast(this, "Aktualisierung fehlgeschlagen: " + err);
            Sync.clearError();
            if (current >= 0) ((Section) sections[current]).refresh();
        } else {
            Ui.toast(this, "Playlist aktualisiert");
            invalidate();
        }
    }

    // ---- Host ------------------------------------------------------------------

    @Override
    public Source source() {
        return source;
    }

    @Override
    public Prefs prefs() {
        return prefs;
    }

    @Override
    public Db db() {
        return db;
    }

    @Override
    public void playLive(List<Item> queue, int index) {
        if (source == null || queue.isEmpty()) return;
        boolean hls = prefs.hls();
        List<Playable> l = new ArrayList<>(queue.size());
        for (Item it : queue) l.add(Playable.live(source, it, hls));
        PlayQueue.set(source, l, index, "Sender");
        startActivity(new Intent(this, PlayerActivity.class));
    }

    @Override
    public void open(Item item) {
        startActivity(new Intent(this, DetailActivity.class).putExtra("source", item.source).putExtra("kind", item.kind).putExtra("ext", item.ext));
    }

    @Override
    public void open(Mark m) {
        boolean episode = m.kind == Item.EPISODE;
        startActivity(new Intent(this, DetailActivity.class).putExtra("source", m.source)
                .putExtra("kind", episode ? Item.SERIES : m.kind).putExtra("ext", episode ? m.parent : m.ext));
    }

    @Override
    public void openSetup() {
        startActivity(new Intent(this, SetupActivity.class));
    }

    @Override
    public void refreshNow() {
        if (Sync.running()) Ui.toast(this, "Die Playlist wird gerade aktualisiert.");
        else if (source != null) Sync.start(this, source, false);
    }

    // ---- Tasten ----------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_SEARCH && current >= 0) {
            switchTo(SEARCH);
            content.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (current < 0) {
            finish();
            return;
        }
        if (!rail.hasFocus()) {
            buttons[current].requestFocus();
            return;
        }
        if (current != HOME) {
            switchTo(HOME);
            buttons[HOME].requestFocus();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2500) {
            finish();
        } else {
            lastBack = now;
            Ui.toast(this, "Zum Beenden noch einmal Zurück drücken");
        }
    }

    // ---- Leiste ----------------------------------------------------------------

    private final class Rail extends LinearLayout {
        Rail(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(0, Ui.dp(18), 0, Ui.dp(16));
            GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF0080507, 0xC00A0608});
            GradientDrawable edge = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00DD1A34, 0x8CDD1A34, 0x00DD1A34});
            LayerDrawable bg = new LayerDrawable(new Drawable[]{shade, edge});
            bg.setLayerGravity(1, Gravity.END | Gravity.FILL_VERTICAL);
            bg.setLayerWidth(1, Math.max(1, Ui.dp(1)));
            setBackground(bg);
            EclipseView logo = new EclipseView(c);
            LayoutParams lp0 = new LayoutParams(Ui.dp(34), Ui.dp(34));
            lp0.bottomMargin = Ui.dp(14);
            addView(logo, lp0);
            for (int i = 0; i < LABELS.length; i++) {
                if (i == SETTINGS) addView(new View(c), new LayoutParams(1, 0, 1));
                buttons[i] = button(c, i);
                LayoutParams lp = new LayoutParams(Ui.dp(64), Ui.dp(52));
                lp.bottomMargin = Ui.dp(5);
                addView(buttons[i], lp);
            }
        }

        private View button(Context c, final int index) {
            LinearLayout b = new LinearLayout(c);
            b.setOrientation(VERTICAL);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.rowBg(13));
            b.setFocusable(true);
            b.setClickable(true);
            b.addView(Ui.icon(c, ICONS[index], Ui.states(Ui.WHITE, Ui.ACCENT_HOT, Ui.DIM)), new LayoutParams(Ui.dp(20), Ui.dp(20)));
            TextView t = Ui.line(c, 8.5f, Ui.DIM, true);
            t.setTextColor(Ui.states(Ui.WHITE, Ui.TEXT, Ui.DIM));
            t.setDuplicateParentStateEnabled(true);
            t.setText(LABELS[index]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(5), 0, 0);
            b.addView(t, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final Runnable go = new Runnable() {
                @Override
                public void run() {
                    if (buttons[index].isFocused()) switchTo(index);
                }
            };
            b.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    Bg.cancel(go);
                    if (has && index != current) Bg.uiDelayed(go, 160);
                }
            });
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bg.cancel(go);
                    switchTo(index);
                    content.requestFocus();
                }
            });
            b.setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int code, KeyEvent e) {
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (e.getAction() == KeyEvent.ACTION_DOWN) {
                            Bg.cancel(go);
                            switchTo(index);
                            content.requestFocus();
                        }
                        return true;
                    }
                    return false;
                }
            });
            return b;
        }

        /** Kommt der Fokus von rechts zurueck, landet er auf dem aktuellen Bereich. */
        @Override
        public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
            if (!hasFocus() && current >= 0) {
                views.add(buttons[current]);
                return;
            }
            super.addFocusables(views, direction, focusableMode);
        }
    }
}

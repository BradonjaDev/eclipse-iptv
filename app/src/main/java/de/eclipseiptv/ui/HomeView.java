package de.eclipseiptv.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Category;
import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.Mark;
import de.eclipseiptv.data.Source;
import de.eclipseiptv.data.Sync;

/** Startseite: Weiterschauen, Favoriten und Neues auf einen Blick. */
public final class HomeView extends FrameLayout implements Section {
    private final Host host;
    private final Rows rows;
    private final TextView clock, date, summary, hint;
    private int gen;
    private String shown = "";

    public HomeView(Context c, Host h) {
        super(c);
        host = h;
        rows = new Rows(c, h);
        addView(rows, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Kopf: grosse Finsternis mit Wolken davor, daneben der Name, rechts die Uhr
        FrameLayout hero = new FrameLayout(c);
        hero.setClipChildren(false);
        View back = Clouds.view(c, true, 0.9f, 22000, 6);
        hero.addView(back, at(Ui.dp(4), Ui.dp(20), Ui.dp(22 * Clouds.RATIO), Ui.dp(22)));
        hero.addView(new EclipseView(c), at(Ui.dp(16), Ui.dp(14), Ui.dp(104), Ui.dp(104)));
        View front = Clouds.view(c, false, 1f, 18000, 8);
        hero.addView(front, at(Ui.dp(60), Ui.dp(86), Ui.dp(30 * Clouds.RATIO), Ui.dp(30)));

        LinearLayout names = new LinearLayout(c);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout word = new LinearLayout(c);
        word.setGravity(Gravity.BOTTOM);
        TextView brand = Ui.line(c, 32f, Ui.TEXT, true);
        brand.setText("ECLIPSE");
        brand.setLetterSpacing(0.16f);
        word.addView(brand);
        TextView iptv = Ui.line(c, 13f, Ui.ACCENT_HOT, true);
        iptv.setText("IPTV");
        iptv.setLetterSpacing(0.4f);
        iptv.setPadding(Ui.dp(10), 0, 0, Ui.dp(5));
        word.addView(iptv);
        names.addView(word);
        View rule = new View(c);
        rule.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Ui.ACCENT, 0x00DD1A34}));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.dp(220), Math.max(1, Ui.dp(1.2f)));
        rp.topMargin = Ui.dp(7);
        names.addView(rule, rp);
        summary = Ui.line(c, 10.5f, Ui.DIM, false);
        summary.setPadding(0, Ui.dp(7), 0, 0);
        names.addView(summary);
        hero.addView(names, at(Ui.dp(140), Ui.dp(34), ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout time = new LinearLayout(c);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.END);
        clock = Ui.line(c, 26f, Ui.TEXT, false);
        time.addView(clock);
        date = Ui.line(c, 10f, Ui.DIM, false);
        time.addView(date);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        tp.topMargin = Ui.dp(30);
        tp.rightMargin = Ui.dp(30);
        hero.addView(time, tp);
        rows.box().addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(132)));

        hint = Ui.text(c, 12f, Ui.FAINT, false);
        hint.setPadding(Ui.dp(22), Ui.dp(30), Ui.dp(40), 0);
        hint.setLineSpacing(Ui.px(4), 1f);
        hint.setVisibility(GONE);
        rows.box().addView(hint);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(now));
            date.setText(new SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(now));
            Bg.uiDelayed(this, 20000);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        tick.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        Bg.cancel(tick);
        super.onDetachedFromWindow();
    }

    private static String n(int v) {
        return String.format("%,d", v).replace(',', '.');
    }

    @Override
    public void refresh() {
        final Source s = host.source();
        if (s == null) return;
        if (Sync.running()) summary.setText("Playlist wird aktualisiert …  " + Sync.status());
        else summary.setText(n(s.nLive) + " Sender   ·   " + n(s.nVod) + " Filme   ·   " + n(s.nSeries) + " Serien");
        final boolean adult = host.prefs().adult();
        final int g = ++gen;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final List<Cards.Entry> cont = new ArrayList<>();
                for (Mark m : host.db().continueWatching(s.id, 20)) cont.add(Cards.Entry.of(m));
                final List<Cards.Entry> favLive = Rows.entries(host.db().items(s.id, Item.LIVE, Category.FAV, adult));
                final List<Cards.Entry> recLive = Rows.entries(host.db().items(s.id, Item.LIVE, Category.RECENT, adult));
                final List<Cards.Entry> favVod = Rows.entries(host.db().items(s.id, Item.VOD, Category.FAV, adult));
                final List<Cards.Entry> favSer = Rows.entries(host.db().items(s.id, Item.SERIES, Category.FAV, adult));
                final List<Cards.Entry> newVod = Rows.entries(limit(host.db().items(s.id, Item.VOD, Category.NEW, adult), 40));
                final List<Cards.Entry> newSer = Rows.entries(limit(host.db().items(s.id, Item.SERIES, Category.NEW, adult), 40));
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (g != gen) return;
                        String sig = s.id + "|" + sign(cont) + sign(favLive) + sign(recLive) + sign(newVod) + sign(newSer) + sign(favVod) + sign(favSer);
                        if (sig.equals(shown)) return;
                        shown = sig;
                        boolean hadFocus = rows.hasFocus();
                        rows.clearRows(2);
                        rows.addRow("Weiterschauen", Cards.POSTER, 108, cont);
                        rows.addRow("Deine Sender", Cards.WIDE, 138, favLive);
                        rows.addRow("Zuletzt gesehene Sender", Cards.WIDE, 138, recLive);
                        rows.addRow(s.isXtream() ? "Neue Filme" : "Filme", Cards.POSTER, 108, newVod);
                        rows.addRow(s.isXtream() ? "Neue Serien" : "Serien", Cards.POSTER, 108, newSer);
                        rows.addRow("Deine Filme", Cards.POSTER, 108, favVod);
                        rows.addRow("Deine Serien", Cards.POSTER, 108, favSer);
                        hint.setVisibility(rows.isEmpty() ? VISIBLE : GONE);
                        hint.setText("Willkommen! Links findest du Sender, Filme und Serien.\n"
                                + "Mit einem langen Druck auf OK merkst du dir Favoriten – sie erscheinen dann hier.");
                        if (hadFocus) rows.focusFirst();
                    }
                });
            }
        });
    }

    /** Kurzfassung einer Reihe, um Aenderungen zu erkennen. */
    private static String sign(List<Cards.Entry> l) {
        StringBuilder b = new StringBuilder();
        for (Cards.Entry e : l) {
            b.append(e.item != null ? e.item.ext : e.mark != null ? e.mark.ext + ":" + e.progress : "").append(',');
        }
        return b.append('|').toString();
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    private static List<Item> limit(List<Item> l, int n) {
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }
}

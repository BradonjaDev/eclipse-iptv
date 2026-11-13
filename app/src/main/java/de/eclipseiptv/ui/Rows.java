package de.eclipseiptv.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.eclipseiptv.data.Item;

/** Senkrecht blaetterbare Seite aus waagerechten Reihen von Karten. */
public final class Rows extends ScrollView {
    private final LinearLayout box;
    private final Host host;
    private final List<TvRecycler> rows = new ArrayList<>();

    public Rows(Context c, Host host) {
        super(c);
        this.host = host;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
        setClipChildren(false);
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setPadding(0, 0, 0, Ui.dp(30));
        addView(box, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public LinearLayout box() {
        return box;
    }

    /** Entfernt alle Reihen; Ansichten davor (z. B. der Kopf) bleiben stehen. */
    public void clearRows(int keepViews) {
        while (box.getChildCount() > keepViews) box.removeViewAt(box.getChildCount() - 1);
        rows.clear();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public void focusFirst() {
        if (!rows.isEmpty()) rows.get(0).requestFocus();
    }

    public void addRow(String title, int style, int cell, final List<Cards.Entry> entries) {
        if (entries == null || entries.isEmpty()) return;
        Context c = getContext();
        final LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setClipChildren(false);
        TextView t = Ui.line(c, 13f, Ui.TEXT, true);
        t.setText(title);
        t.setPadding(Ui.dp(22), Ui.dp(14), 0, Ui.dp(4));
        wrap.addView(t);
        final TvRecycler r = new TvRecycler(c).horizontal();
        r.setPadding(Ui.dp(17), Ui.dp(4), Ui.dp(24), Ui.dp(4));
        final Cards adapter = new Cards(style, cell, new Cards.Listener() {
            @Override
            public void onClick(Cards.Entry e, int position) {
                if (e.mark != null) {
                    host.open(e.mark);
                } else if (e.item != null && e.item.kind == Item.LIVE) {
                    List<Item> q = new ArrayList<>();
                    for (Cards.Entry x : entries) if (x.item != null) q.add(x.item);
                    host.playLive(q, position);
                } else if (e.item != null) {
                    host.open(e.item);
                }
            }

            @Override
            public boolean onLong(Cards.Entry e, int position) {
                if (e.item == null) return false;
                e.fav = !e.fav;
                host.db().setFav(e.item, e.fav);
                Ui.toast(getContext(), e.fav ? "Zu den Favoriten hinzugefügt" : "Aus den Favoriten entfernt");
                if (r.getAdapter() != null) r.getAdapter().notifyItemChanged(position);
                return true;
            }
        });
        adapter.set(entries);
        r.setAdapter(adapter);
        int imgW = Ui.dp(cell) - Ui.dp(10);
        int h = (style == Cards.POSTER ? Math.round(imgW * 1.5f) : Math.round(imgW * 9f / 16f)) + Ui.dp(10 + 34 + 8);
        wrap.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        r.onSelect(new TvRecycler.OnSelect() {
            @Override
            public void onSelect(int position) {
                int target = wrap.getTop() + wrap.getHeight() / 2 - getHeight() / 2;
                smoothScrollTo(0, Math.max(0, target));
            }
        });
        box.addView(wrap);
        rows.add(r);
    }

    /** Die Seite blaettert selbst zur gewaehlten Reihe; das eingebaute Nachfuehren wird nicht gebraucht. */
    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
        return 0;
    }

    public static List<Cards.Entry> entries(List<Item> items) {
        List<Cards.Entry> out = new ArrayList<>(items.size());
        for (Item it : items) out.add(Cards.Entry.of(it));
        return out;
    }

    public void scrollTop() {
        scrollTo(0, 0);
    }

    public View header() {
        return box.getChildCount() > 0 ? box.getChildAt(0) : null;
    }
}

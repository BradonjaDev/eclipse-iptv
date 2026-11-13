package de.eclipseiptv.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.Mark;

/** Zeigt Eintraege als Senderzeile, Poster oder breite Karte. */
public final class Cards extends RecyclerView.Adapter<Cards.H> {
    public static final int ROW = 0, POSTER = 1, WIDE = 2;

    public static final class Entry {
        public Item item;
        public Mark mark;
        public String title = "", sub = "", image = "";
        public int progress;
        public boolean fav;

        public static Entry of(Item it) {
            Entry e = new Entry();
            e.item = it;
            e.title = it.name;
            e.image = it.icon;
            if (it.kind != Item.LIVE) e.sub = meta(it);
            return e;
        }

        public static Entry of(Mark m) {
            Entry e = new Entry();
            e.mark = m;
            e.title = m.name;
            e.image = m.icon;
            e.progress = m.percent();
            return e;
        }
    }

    public static String meta(Item it) {
        StringBuilder b = new StringBuilder();
        if (it.year > 0) b.append(it.year);
        if (it.rating > 0.05) {
            if (b.length() > 0) b.append("  ·  ");
            b.append("★ ").append(String.format(Locale.GERMANY, "%.1f", it.rating));
        }
        return b.toString();
    }

    public interface Listener {
        void onClick(Entry e, int position);

        boolean onLong(Entry e, int position);
    }

    private final int style, cell;
    private final Listener listener;
    private List<Entry> data = new ArrayList<>();

    /** cell: Breite einer Karte in Gestaltungs-Einheiten (bei Zeilen ohne Bedeutung). */
    public Cards(int style, int cell, Listener l) {
        this.style = style;
        this.cell = cell;
        this.listener = l;
    }

    public void set(List<Entry> list) {
        data = list == null ? new ArrayList<Entry>() : list;
        notifyDataSetChanged();
    }

    public List<Entry> data() {
        return data;
    }

    public Entry get(int pos) {
        return pos >= 0 && pos < data.size() ? data.get(pos) : null;
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static final class H extends RecyclerView.ViewHolder {
        TextView num, title, sub, fallback;
        ImageView image, star;
        View progress;

        H(View v) {
            super(v);
        }
    }

    @Override
    public H onCreateViewHolder(ViewGroup parent, int type) {
        Context c = parent.getContext();
        final H h = style == ROW ? row(c) : card(c);
        h.itemView.setFocusable(true);
        h.itemView.setClickable(true);
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int p = h.getAdapterPosition();
                Entry e = get(p);
                if (e != null && listener != null) listener.onClick(e, p);
            }
        });
        h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int p = h.getAdapterPosition();
                Entry e = get(p);
                return e != null && listener != null && listener.onLong(e, p);
            }
        });
        return h;
    }

    private H row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(Ui.rowBg(9));
        l.setPadding(Ui.dp(6), 0, Ui.dp(12), 0);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(42));
        lp.bottomMargin = Ui.dp(3);
        l.setLayoutParams(lp);
        H h = new H(l);
        h.num = Ui.line(c, 9.5f, Ui.FAINT, false);
        h.num.setTextColor(Ui.rowValue(Ui.FAINT));
        h.num.setDuplicateParentStateEnabled(true);
        h.num.setGravity(Gravity.END);
        l.addView(h.num, new LinearLayout.LayoutParams(Ui.dp(30), ViewGroup.LayoutParams.WRAP_CONTENT));
        h.image = new ImageView(c);
        h.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.image.setBackground(Ui.rect(0x1FFFFFFF, 5));
        h.image.setPadding(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(30));
        ip.leftMargin = Ui.dp(8);
        ip.rightMargin = Ui.dp(10);
        l.addView(h.image, ip);
        h.title = Ui.line(c, 12.5f, Ui.TEXT, false);
        h.title.setTextColor(Ui.rowText(Ui.TEXT));
        h.title.setDuplicateParentStateEnabled(true);
        l.addView(h.title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        h.sub = Ui.line(c, 7.5f, Ui.WHITE, true);
        h.sub.setText("LIVE");
        h.sub.setLetterSpacing(0.1f);
        h.sub.setBackground(Ui.rect(Ui.ACCENT, 3));
        h.sub.setPadding(Ui.dp(4), Ui.dp(1.5f), Ui.dp(4), Ui.dp(1.5f));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.rightMargin = Ui.dp(8);
        l.addView(h.sub, bp);
        h.star = Ui.icon(c, Ui.I_STAR, Ui.states(Ui.WHITE, Ui.ACCENT_HOT, Ui.ACCENT_HOT));
        l.addView(h.star, new LinearLayout.LayoutParams(Ui.dp(11), Ui.dp(11)));
        return h;
    }

    private H card(Context c) {
        boolean poster = style == POSTER;
        int w = Ui.dp(cell), pad = Ui.dp(5);
        int iw = w - 2 * pad, ih = poster ? Math.round(iw * 1.5f) : Math.round(iw * 9f / 16f);
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(pad, pad, pad, pad);
        l.setLayoutParams(new RecyclerView.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        H h = new H(l);

        FrameLayout box = new FrameLayout(c);
        box.setBackground(Ui.rect(Ui.SURFACE2, 7));
        Ui.round(box, 7);
        h.fallback = Ui.text(c, poster ? 10f : 10.5f, Ui.DIM, true);
        h.fallback.setGravity(Gravity.CENTER);
        Ui.lines(h.fallback, poster ? 4 : 2);
        h.fallback.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        box.addView(h.fallback, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        h.image = new ImageView(c);
        h.image.setScaleType(poster ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        if (!poster) h.image.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        box.addView(h.image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        h.progress = new View(c);
        h.progress.setBackgroundColor(Ui.ACCENT);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(0, Ui.dp(3), Gravity.BOTTOM | Gravity.START);
        box.addView(h.progress, pp);
        View frame = new View(c);
        frame.setBackground(Ui.frame(7));
        frame.setDuplicateParentStateEnabled(true);
        box.addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        l.addView(box, new LinearLayout.LayoutParams(iw, ih));

        h.title = Ui.line(c, 10.5f, Ui.TEXT, false);
        h.title.setTextColor(Ui.states(Ui.ACCENT_HOT, Ui.TEXT, Ui.TEXT));
        h.title.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(iw, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(6);
        l.addView(h.title, tp);
        h.sub = Ui.line(c, 8.5f, Ui.FAINT, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(iw, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(2);
        l.addView(h.sub, sp);
        Ui.lift(l, 1.07f);
        return h;
    }

    @Override
    public void onBindViewHolder(H h, int pos) {
        Entry e = data.get(pos);
        h.title.setText(e.title);
        if (style == ROW) {
            h.num.setText(e.item != null && e.item.num > 0 ? String.valueOf(e.item.num) : "");
            h.star.setVisibility(e.fav ? View.VISIBLE : View.INVISIBLE);
            de.eclipseiptv.data.Playable on = LivePlayer.current();
            boolean playing = on != null && e.item != null && e.item.kind == Item.LIVE && e.item.ext.equals(on.markExt);
            h.itemView.setActivated(playing);
            h.sub.setVisibility(playing ? View.VISIBLE : View.GONE);
            Img.load(h.image, e.image, Ui.dp(48), Ui.dp(30), false);
            return;
        }
        boolean poster = style == POSTER;
        int iw = Ui.dp(cell) - 2 * Ui.dp(5), ih = poster ? Math.round(iw * 1.5f) : Math.round(iw * 9f / 16f);
        h.fallback.setText(e.title);
        h.sub.setText(e.fav ? (e.sub.isEmpty() ? "★ Favorit" : "★  " + e.sub) : e.sub);
        h.sub.setTextColor(e.fav ? Ui.ACCENT_HOT : Ui.FAINT);
        ViewGroup.LayoutParams pp = h.progress.getLayoutParams();
        pp.width = e.progress > 0 ? Math.max(Ui.dp(3), iw * Math.min(100, e.progress) / 100) : 0;
        h.progress.setLayoutParams(pp);
        boolean has = e.image != null && e.image.startsWith("http");
        h.fallback.setVisibility(has && poster ? View.INVISIBLE : View.VISIBLE);
        if (!poster && has) h.fallback.setVisibility(View.INVISIBLE);
        Img.load(h.image, e.image, iw, ih, poster);
    }

    @Override
    public void onViewRecycled(H h) {
        Img.clear(h.image);
    }
}

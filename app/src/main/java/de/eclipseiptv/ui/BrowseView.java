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
import java.util.Set;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Category;
import de.eclipseiptv.data.Epg;
import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.Programme;
import de.eclipseiptv.data.Source;

/** Bereich mit Kategorien links und den Eintraegen rechts (Sender, Filme oder Serien). */
public final class BrowseView extends LinearLayout implements Section {
    private static final int COLUMNS = 6;
    private final Host host;
    private final int kind;
    private final TvRecycler cats, list;
    private final CatAdapter catAdapter = new CatAdapter();
    private final Cards cards;
    private final TextView title, info, empty;
    private EpgPanel epg;

    private List<Category> categories = new ArrayList<>();
    private Category current;
    private int loadGen;
    private Item epgItem;

    public BrowseView(Context c, Host h, int kind) {
        super(c);
        this.host = h;
        this.kind = kind;
        setOrientation(HORIZONTAL);
        boolean live = kind == Item.LIVE;

        cats = new TvRecycler(c).vertical().lockAxis(true);
        cats.setPadding(Ui.dp(8), Ui.dp(20), Ui.dp(8), Ui.dp(20));
        cats.setAdapter(catAdapter);
        cats.setBackgroundColor(0x66121019);
        addView(cats, new LayoutParams(Ui.dp(214), ViewGroup.LayoutParams.MATCH_PARENT));
        cats.onSelect(new TvRecycler.OnSelect() {
            @Override
            public void onSelect(int position) {
                if (current != null) title.setText(current.name);
                Bg.cancel(pick);
                Bg.uiDelayed(pick, 260);
            }
        });

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(VERTICAL);
        right.setPadding(Ui.dp(14), Ui.dp(20), Ui.dp(16), 0);
        addView(right, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.BOTTOM);
        title = Ui.line(c, 17f, Ui.TEXT, true);
        head.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        info = Ui.line(c, 10f, Ui.FAINT, false);
        info.setPadding(Ui.dp(12), 0, Ui.dp(6), Ui.dp(2));
        head.addView(info);
        head.setPadding(Ui.dp(6), 0, 0, Ui.dp(10));
        right.addView(head, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(HORIZONTAL);
        right.addView(body, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        FrameLayout listBox = new FrameLayout(c);
        body.addView(listBox, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // Breite der Spalte: 960 - Leiste 76 - Kategorien 214 - Raender 30 = 640
        int cell = 640 / COLUMNS;
        cards = new Cards(live ? Cards.ROW : Cards.POSTER, cell, new Cards.Listener() {
            @Override
            public void onClick(Cards.Entry e, int position) {
                if (e.item == null) return;
                if (e.item.kind == Item.LIVE) {
                    List<Item> q = new ArrayList<>();
                    for (Cards.Entry x : cards.data()) q.add(x.item);
                    host.playLive(q, position);
                } else {
                    host.open(e.item);
                }
            }

            @Override
            public boolean onLong(Cards.Entry e, int position) {
                if (e.item == null) return false;
                e.fav = !e.fav;
                host.db().setFav(e.item, e.fav);
                cards.notifyItemChanged(position);
                Ui.toast(getContext(), e.fav ? "Zu den Favoriten hinzugefügt" : "Aus den Favoriten entfernt");
                // Die Liste "Favoriten" links erscheint bzw. aktualisiert sich kurz danach.
                Bg.cancel(reloadSoon);
                Bg.uiDelayed(reloadSoon, 600);
                return true;
            }
        });
        list = new TvRecycler(c);
        if (live) list.vertical();
        else list.grid(COLUMNS);
        list.lockAxis(true);
        list.setPadding(0, Ui.dp(4), 0, Ui.dp(24));
        list.setAdapter(cards);
        listBox.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.onSelect(new TvRecycler.OnSelect() {
            @Override
            public void onSelect(int position) {
                focused(cards.get(position));
            }
        });

        empty = Ui.text(c, 12f, Ui.FAINT, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(GONE);
        listBox.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (live) {
            epg = new EpgPanel(c);
            LayoutParams ep = new LayoutParams(Ui.dp(262), ViewGroup.LayoutParams.MATCH_PARENT);
            ep.leftMargin = Ui.dp(12);
            ep.bottomMargin = Ui.dp(20);
            body.addView(epg, ep);
            epg.setVisibility(INVISIBLE);
        }
    }

    private final Runnable reloadSoon = new Runnable() {
        @Override
        public void run() {
            if (isAttachedToWindow()) loadCategories();
        }
    };

    private final Runnable pick = new Runnable() {
        @Override
        public void run() {
            Category c = catAdapter.get(cats.selected());
            if (c != null && cats.hasFocus()) show(c, false);
        }
    };

    @Override
    public void refresh() {
        loadCategories();
    }

    /** Baut die Kategorien neu auf, z. B. nachdem eine ausgeblendet wurde. */
    public void reload() {
        loadCategories();
    }

    private void loadCategories() {
        final Source s = host.source();
        if (s == null) return;
        final boolean adult = host.prefs().adult();
        final int gen = ++loadGen;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final List<Category> out = new ArrayList<>();
                int favs = host.db().markCount(s.id, kind, true), recent = host.db().markCount(s.id, kind, false);
                if (favs > 0) out.add(special(Category.FAV, "Favoriten", favs));
                if (recent > 0) out.add(special(Category.RECENT, "Zuletzt gesehen", Math.min(recent, 60)));
                if (kind != Item.LIVE && s.isXtream()) out.add(special(Category.NEW, "Neu hinzugefügt", 0));
                int total = 0;
                List<Category> real = host.db().categories(s.id, kind, adult);
                for (Category c : real) total += c.count;
                out.add(special(Category.ALL, kind == Item.LIVE ? "Alle Sender" : kind == Item.VOD ? "Alle Filme" : "Alle Serien", total));
                out.addAll(real);
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != loadGen) return;
                        boolean had = cats.hasFocus();
                        categories = out;
                        catAdapter.notifyDataSetChanged();
                        String want = current != null ? current.ext : host.prefs().lastCategory(kind);
                        int pos = -1, firstReal = -1;
                        for (int i = 0; i < out.size(); i++) {
                            if (out.get(i).ext.equals(want)) pos = i;
                            if (firstReal < 0 && !out.get(i).isSpecial()) firstReal = i;
                        }
                        if (pos < 0) pos = out.get(0).ext.equals(Category.FAV) ? 0 : Math.max(0, firstReal);
                        cats.jumpTo(pos, had);
                        show(out.get(pos), true);
                    }
                });
            }
        });
    }

    private Category special(String ext, String name, int count) {
        Category c = new Category(kind, ext, name);
        c.count = count;
        return c;
    }

    /** Laedt die Eintraege einer Kategorie. keep: Position in der Liste moeglichst behalten. */
    private void show(final Category c, final boolean keep) {
        final Source s = host.source();
        if (s == null) return;
        final boolean same = current != null && current.ext.equals(c.ext);
        if (same && !keep) return;
        current = c;
        host.prefs().lastCategory(kind, c.ext);
        catAdapter.mark();
        title.setText(c.name);
        final boolean adult = host.prefs().adult();
        final int gen = ++loadGen;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                List<Item> items = host.db().items(s.id, kind, c.ext, adult);
                Set<String> favs = host.db().favorites(s.id, kind);
                final List<Cards.Entry> out = new ArrayList<>(items.size());
                for (Item it : items) {
                    Cards.Entry e = Cards.Entry.of(it);
                    e.fav = favs.contains(it.ext);
                    out.add(e);
                }
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != loadGen) return;
                        int keepPos = same ? list.selected() : 0;
                        cards.set(out);
                        if (keepPos >= out.size()) keepPos = Math.max(0, out.size() - 1);
                        list.jumpTo(keepPos, list.hasFocus());
                        empty.setVisibility(out.isEmpty() ? VISIBLE : GONE);
                        empty.setText(Category.FAV.equals(c.ext) ? "Noch keine Favoriten.\nOK lange drücken, um einen Eintrag zu merken."
                                : "Hier ist nichts zu sehen.");
                        info.setText(count(out.size()) + "   ·   OK lang drücken: Favorit");
                        if (epg != null) {
                            epg.setVisibility(out.isEmpty() ? INVISIBLE : VISIBLE);
                            if (!out.isEmpty() && !list.hasFocus()) focused(out.get(keepPos));
                        }
                        if (out.isEmpty() && list.hasFocus()) cats.requestFocus();
                    }
                });
            }
        });
    }

    private String count(int n) {
        String num = String.format("%,d", n).replace(',', '.');
        return num + (kind == Item.LIVE ? " Sender" : kind == Item.VOD ? (n == 1 ? " Film" : " Filme") : (n == 1 ? " Serie" : " Serien"));
    }

    private final Runnable epgLoad = new Runnable() {
        @Override
        public void run() {
            final Item it = epgItem;
            if (it == null) return;
            Epg.load(host.source(), it, new Epg.Callback() {
                @Override
                public void onEpg(Item item, List<Programme> l) {
                    if (item == epgItem && epg != null) epg.programme(l);
                }
            });
        }
    };

    private void focused(Cards.Entry e) {
        if (e == null || e.item == null) return;
        if (epg != null) {
            epgItem = e.item;
            epg.channel(e.item);
            Bg.cancel(epgLoad);
            Bg.uiDelayed(epgLoad, 350);
        } else {
            title.setText(e.item.name);
        }
    }

    // ---- Kategorien ------------------------------------------------------------

    private final class CatAdapter extends RecyclerView.Adapter<CatH> {
        Category get(int pos) {
            return pos >= 0 && pos < categories.size() ? categories.get(pos) : null;
        }

        /** Hebt die Kategorie hervor, deren Inhalt gerade rechts zu sehen ist. */
        void mark() {
            for (int i = 0; i < cats.getChildCount(); i++) {
                View v = cats.getChildAt(i);
                Category c = get(cats.getChildAdapterPosition(v));
                v.setActivated(c != null && current != null && c.ext.equals(current.ext));
            }
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        @Override
        public CatH onCreateViewHolder(ViewGroup parent, int type) {
            Context c = parent.getContext();
            LinearLayout l = new LinearLayout(c);
            l.setGravity(Gravity.CENTER_VERTICAL);
            l.setBackground(Ui.rowBg(8));
            l.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
            l.setFocusable(true);
            l.setClickable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(33));
            lp.bottomMargin = Ui.dp(2);
            l.setLayoutParams(lp);
            final CatH h = new CatH(l);
            h.icon = new ImageView(c);
            LayoutParams ip = new LayoutParams(Ui.dp(13), Ui.dp(13));
            ip.rightMargin = Ui.dp(8);
            l.addView(h.icon, ip);
            h.name = Ui.line(c, 11.5f, Ui.TEXT, false);
            h.name.setTextColor(Ui.states(Ui.TEXT, Ui.ACCENT_HOT, Ui.DIM));
            h.name.setDuplicateParentStateEnabled(true);
            l.addView(h.name, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            h.count = Ui.line(c, 8.5f, Ui.FAINT, false);
            h.count.setPadding(Ui.dp(6), 0, 0, 0);
            l.addView(h.count);
            l.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Category cat = get(h.getAdapterPosition());
                    if (cat == null) return;
                    Bg.cancel(pick);
                    show(cat, false);
                    if (cards.getItemCount() > 0) list.requestFocus();
                }
            });
            l.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    final Category cat = get(h.getAdapterPosition());
                    if (cat == null || cat.isSpecial()) return false;
                    Sheet.confirm(getContext(), "Kategorie ausblenden?", "„" + cat.name + "“ wird nicht mehr angezeigt. "
                            + "In den Einstellungen lässt sich das rückgängig machen.", "Ausblenden", new Runnable() {
                        @Override
                        public void run() {
                            host.db().hideCategory(host.source().id, kind, cat.ext, true);
                            if (current != null && current.ext.equals(cat.ext)) current = null;
                            host.invalidate();
                        }
                    });
                    return true;
                }
            });
            return h;
        }

        @Override
        public void onBindViewHolder(CatH h, int pos) {
            Category c = categories.get(pos);
            h.name.setText(c.name);
            h.count.setText(c.count > 0 ? String.format("%,d", c.count).replace(',', '.') : "");
            String ic = Category.FAV.equals(c.ext) ? Ui.I_STAR : Category.RECENT.equals(c.ext) ? Ui.I_CLOCK
                    : Category.NEW.equals(c.ext) ? Ui.I_NEW : Category.ALL.equals(c.ext) ? Ui.I_LIST : null;
            h.icon.setVisibility(ic == null ? GONE : VISIBLE);
            if (ic != null) h.icon.setImageDrawable(new Ui.PathDrawable(ic, Ui.ACCENT));
            h.itemView.setActivated(current != null && c.ext.equals(current.ext));
        }
    }

    private static final class CatH extends RecyclerView.ViewHolder {
        TextView name, count;
        ImageView icon;

        CatH(View v) {
            super(v);
        }
    }
}

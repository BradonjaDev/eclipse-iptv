package de.umbratv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.umbratv.data.Bg;
import de.umbratv.data.Db;
import de.umbratv.data.Details;
import de.umbratv.data.Episode;
import de.umbratv.data.Item;
import de.umbratv.data.Mark;
import de.umbratv.data.PlayQueue;
import de.umbratv.data.Playable;
import de.umbratv.data.Source;
import de.umbratv.data.Text;
import de.umbratv.data.Xtream;
import de.umbratv.ui.Img;
import de.umbratv.ui.TvRecycler;
import de.umbratv.ui.Ui;

/** Detailseite eines Films oder einer Serie: Beschreibung, Trailer, Staffeln und Folgen. */
public final class DetailActivity extends Activity {
    private Db db;
    private Source source;
    private Item item;
    private boolean series, fav, loaded;
    private Mark movieMark;
    private Map<String, Mark> episodeMarks = new HashMap<>();
    private List<Episode> episodes = new ArrayList<>();
    private final List<Integer> seasons = new ArrayList<>();
    private int season = -1;
    private String trailer = "";
    private int durationSecs;

    private ImageView backdrop, poster;
    private TextView title, meta, plot, people, status;
    private LinearLayout buttons;
    private TvRecycler seasonList, episodeList;
    private final SeasonAdapter seasonAdapter = new SeasonAdapter();
    private final EpisodeAdapter episodeAdapter = new EpisodeAdapter();
    private List<Episode> shown = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        db = Db.get(this);
        final int sid = getIntent().getIntExtra("source", 0);
        final int kind = getIntent().getIntExtra("kind", Item.VOD);
        final String ext = getIntent().getStringExtra("ext");
        series = kind == Item.SERIES;
        build();
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final Source s = db.source(sid);
                final Item it = s == null || ext == null ? null : db.item(sid, kind, ext);
                final boolean f = it != null && db.isFav(it);
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (it == null) {
                            Ui.toast(DetailActivity.this, "Dieser Eintrag ist nicht mehr in der Playlist.");
                            finish();
                            return;
                        }
                        source = s;
                        item = it;
                        fav = f;
                        trailer = it.trailer;
                        bind();
                        loadMarks();
                        loadDetails();
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        if (item != null) loadMarks();
    }

    // ---- Aufbau ----------------------------------------------------------------

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0.5f);
        root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View scrim = new View(this);
        scrim.setBackground(new LayerDrawable(new Drawable[]{
                new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF507060B, 0xC007060B, 0x5907060B}),
                new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x0007060B, 0x6607060B, 0xF707060B})}));
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setPadding(Ui.dp(44), Ui.dp(series ? 28 : 46), Ui.dp(60), 0);
        head.setClipChildren(false);
        FrameLayout posterBox = new FrameLayout(this);
        posterBox.setBackground(Ui.rect(Ui.SURFACE2, 10));
        Ui.round(posterBox, 10);
        poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterBox.addView(poster, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int pw = series ? 112 : 168;
        head.addView(posterBox, new LinearLayout.LayoutParams(Ui.dp(pw), Ui.dp(pw * 1.5f)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setClipChildren(false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        ip.leftMargin = Ui.dp(28);
        head.addView(info, ip);
        title = Ui.text(this, series ? 22f : 26f, Ui.TEXT, true);
        Ui.lines(title, 2);
        info.addView(title);
        meta = Ui.line(this, 11.5f, Ui.GOLD_HOT, false);
        meta.setPadding(0, Ui.dp(7), 0, 0);
        info.addView(meta);
        plot = Ui.text(this, 11.5f, 0xFFD8D3E3, false);
        plot.setLineSpacing(Ui.px(3.5f), 1f);
        Ui.lines(plot, series ? 3 : 7);
        plot.setPadding(0, Ui.dp(10), 0, 0);
        info.addView(plot, new LinearLayout.LayoutParams(Ui.dp(560), ViewGroup.LayoutParams.WRAP_CONTENT));
        people = Ui.text(this, 10f, Ui.FAINT, false);
        Ui.lines(people, series ? 1 : 2);
        people.setPadding(0, Ui.dp(8), 0, 0);
        info.addView(people, new LinearLayout.LayoutParams(Ui.dp(560), ViewGroup.LayoutParams.WRAP_CONTENT));
        buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setClipChildren(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(36));
        bp.topMargin = Ui.dp(series ? 12 : 18);
        info.addView(buttons, bp);
        status = Ui.text(this, 10.5f, Ui.FAINT, false);
        status.setPadding(0, Ui.dp(10), 0, 0);
        info.addView(status);
        page.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (series) {
            seasonList = new TvRecycler(this).horizontal();
            seasonList.setPadding(Ui.dp(40), Ui.dp(4), Ui.dp(40), Ui.dp(4));
            seasonList.setAdapter(seasonAdapter);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(40));
            sp.topMargin = Ui.dp(12);
            page.addView(seasonList, sp);
            seasonList.onSelect(new TvRecycler.OnSelect() {
                @Override
                public void onSelect(int position) {
                    if (position >= 0 && position < seasons.size() && seasons.get(position) != season) showSeason(seasons.get(position), false);
                }
            });
            episodeList = new TvRecycler(this).vertical();
            episodeList.setPadding(Ui.dp(40), Ui.dp(4), Ui.dp(40), Ui.dp(24));
            episodeList.setAdapter(episodeAdapter);
            page.addView(episodeList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        setContentView(root);
    }

    private View button(String icon, String label, boolean primary, final Runnable action) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.buttonBg(18));
        b.setActivated(primary);
        b.setPadding(Ui.dp(14), 0, Ui.dp(16), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.addView(Ui.icon(this, icon, Ui.states(Ui.ON_GOLD, Ui.GOLD, Ui.TEXT)), new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14)));
        TextView t = Ui.line(this, 12f, Ui.TEXT, true);
        t.setTextColor(Ui.states(Ui.ON_GOLD, Ui.TEXT, Ui.TEXT));
        t.setDuplicateParentStateEnabled(true);
        t.setText(label);
        t.setPadding(Ui.dp(7), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.rightMargin = Ui.dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    // ---- Inhalt ----------------------------------------------------------------

    private void bind() {
        title.setText(item.name);
        Img.load(poster, item.icon, Ui.dp(168), Ui.dp(252), true);
        if (!Text.empty(item.backdrop)) Img.load(backdrop, item.backdrop, Ui.screenW / 2, Ui.screenH / 2, true);
        else Img.load(backdrop, item.icon, Ui.screenW / 6, Ui.screenH / 4, true);
        StringBuilder m = new StringBuilder();
        if (item.year > 0) m.append(item.year);
        if (durationSecs > 0) sep(m).append(Text.minutes(durationSecs));
        if (item.rating > 0.05) sep(m).append("★ ").append(String.format(Locale.GERMANY, "%.1f", item.rating));
        if (!Text.empty(item.genre)) sep(m).append(item.genre.replace(" / ", ", "));
        meta.setText(m);
        meta.setVisibility(m.length() > 0 ? View.VISIBLE : View.GONE);
        plot.setText(Text.empty(item.plot) ? "" : item.plot);
        plot.setVisibility(Text.empty(item.plot) ? View.GONE : View.VISIBLE);
        StringBuilder p = new StringBuilder();
        if (!Text.empty(item.director)) p.append("Regie: ").append(item.director);
        if (!Text.empty(item.cast)) {
            if (p.length() > 0) p.append("\n");
            p.append("Mit ").append(item.cast);
        }
        people.setText(p);
        people.setVisibility(p.length() > 0 ? View.VISIBLE : View.GONE);
        buildButtons();
    }

    private static StringBuilder sep(StringBuilder b) {
        if (b.length() > 0) b.append("   ·   ");
        return b;
    }

    private void buildButtons() {
        int focused = buttons.indexOfChild(buttons.getFocusedChild());
        boolean firstBuild = buttons.getChildCount() == 0;
        buttons.removeAllViews();
        if (series) {
            final Episode next = nextEpisode();
            if (next != null) {
                Mark m = episodeMarks.get(next.id);
                boolean resume = m != null && m.resumable();
                boolean started = !episodeMarks.isEmpty();
                String label = (resume ? "Fortsetzen" : started ? "Weiter" : "Abspielen") + "  ·  S" + next.season + " F" + next.num;
                buttons.addView(button(Ui.I_PLAY, label, true, new Runnable() {
                    @Override
                    public void run() {
                        play(next, true);
                    }
                }));
            }
        } else {
            final boolean resume = movieMark != null && movieMark.resumable();
            buttons.addView(button(Ui.I_PLAY, resume ? "Fortsetzen ab " + Text.clock((int) (movieMark.pos / 1000)) : "Abspielen", true, new Runnable() {
                @Override
                public void run() {
                    playMovie(resume ? movieMark.pos : 0);
                }
            }));
            if (resume) {
                buttons.addView(button(Ui.I_REPLAY, "Von vorn", false, new Runnable() {
                    @Override
                    public void run() {
                        playMovie(0);
                    }
                }));
            }
        }
        if (!Text.empty(trailer)) {
            buttons.addView(button(Ui.I_TRAILER, "Trailer", false, new Runnable() {
                @Override
                public void run() {
                    openTrailer();
                }
            }));
        }
        buttons.addView(button(fav ? Ui.I_STAR : Ui.I_STAR_OFF, fav ? "Gemerkt" : "Merken", false, new Runnable() {
            @Override
            public void run() {
                fav = !fav;
                db.setFav(item, fav);
                buildButtons();
            }
        }));
        if (buttons.getChildCount() > 0) {
            boolean elsewhere = getCurrentFocus() != null && focused < 0 && !firstBuild;
            if (!elsewhere) buttons.getChildAt(focused >= 0 && focused < buttons.getChildCount() ? focused : 0).requestFocus();
        }
    }

    private void loadMarks() {
        final Item it = item;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final Mark mm = series ? null : db.mark(it.source, Item.VOD, it.ext);
                final Map<String, Mark> em = series ? db.episodeMarks(it.source, it.ext) : new HashMap<String, Mark>();
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        movieMark = mm;
                        episodeMarks = em;
                        buildButtons();
                        if (series) episodeAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private void loadDetails() {
        if (!source.isXtream() && !series) {
            loaded = true;
            return;
        }
        status.setText(series ? "Folgen werden geladen …" : "");
        final Item it = item;
        final Source s = source;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                Details d = null;
                String error = null;
                try {
                    if (s.isXtream()) {
                        d = series ? new Xtream(s).seriesInfo(it.ext) : new Xtream(s).vodInfo(it.ext);
                    } else {
                        d = new Details();
                        d.episodes.addAll(db.episodes(s.id, it.ext));
                    }
                } catch (Exception e) {
                    error = "Die Details konnten nicht geladen werden.";
                }
                final Details details = d;
                final String err = error;
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        loaded = true;
                        status.setText(err == null ? "" : err);
                        if (details == null) return;
                        if (!Text.empty(details.plot)) item.plot = details.plot;
                        if (!Text.empty(details.genre)) item.genre = details.genre;
                        if (!Text.empty(details.cast)) item.cast = details.cast;
                        if (!Text.empty(details.director)) item.director = details.director;
                        if (!Text.empty(details.backdrop)) item.backdrop = details.backdrop;
                        if (details.rating > 0.05 && item.rating < 0.05) item.rating = details.rating;
                        if (item.year == 0) item.year = Text.year(details.released, null);
                        if (!Text.empty(details.trailer)) trailer = details.trailer;
                        if (details.durationSecs > 0) durationSecs = details.durationSecs;
                        episodes = details.episodes;
                        bind();
                        if (series) bindEpisodes();
                    }
                });
            }
        });
    }

    private void bindEpisodes() {
        seasons.clear();
        for (Episode e : episodes) if (!seasons.contains(e.season)) seasons.add(e.season);
        seasonAdapter.notifyDataSetChanged();
        seasonList.setVisibility(seasons.size() > 1 ? View.VISIBLE : View.GONE);
        if (episodes.isEmpty()) {
            status.setText("Zu dieser Serie wurden keine Folgen gefunden.");
            return;
        }
        Episode next = nextEpisode();
        int s = next != null ? next.season : seasons.get(0);
        seasonList.jumpTo(seasons.indexOf(s), false);
        showSeason(s, true);
        buildButtons();
    }

    private void showSeason(int s, boolean jumpToNext) {
        season = s;
        shown = new ArrayList<>();
        for (Episode e : episodes) if (e.season == s) shown.add(e);
        episodeAdapter.notifyDataSetChanged();
        for (int i = 0; i < seasonList.getChildCount(); i++) {
            View v = seasonList.getChildAt(i);
            int pos = seasonList.getChildAdapterPosition(v);
            v.setActivated(pos >= 0 && pos < seasons.size() && seasons.get(pos) == s);
        }
        int pos = 0;
        if (jumpToNext) {
            Episode next = nextEpisode();
            if (next != null) pos = Math.max(0, shown.indexOf(next));
        }
        episodeList.jumpTo(pos, false);
    }

    /** Die Folge, mit der es weitergeht: die angefangene, sonst die nach der zuletzt gesehenen, sonst die erste. */
    private Episode nextEpisode() {
        if (episodes.isEmpty()) return null;
        int lastIndex = -1;
        long lastTime = 0;
        for (int i = 0; i < episodes.size(); i++) {
            Mark m = episodeMarks.get(episodes.get(i).id);
            if (m != null && m.last > lastTime) {
                lastTime = m.last;
                lastIndex = i;
            }
        }
        if (lastIndex < 0) return episodes.get(0);
        Mark m = episodeMarks.get(episodes.get(lastIndex).id);
        if (m.resumable() || m.pos <= 30000) return episodes.get(lastIndex);
        return episodes.get(Math.min(lastIndex + 1, episodes.size() - 1));
    }

    // ---- Abspielen -------------------------------------------------------------

    private void playMovie(long from) {
        Playable p = Playable.movie(source, item);
        if (Text.empty(p.url)) {
            Ui.toast(this, "Für diesen Film gibt es keinen Link.");
            return;
        }
        p.startPos = from;
        List<Playable> q = new ArrayList<>();
        q.add(p);
        PlayQueue.set(source, q, 0, "");
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void play(Episode target, boolean resume) {
        List<Playable> q = new ArrayList<>(episodes.size());
        int index = 0;
        for (int i = 0; i < episodes.size(); i++) {
            Episode e = episodes.get(i);
            Playable p = Playable.episode(source, item, e);
            if (e == target) {
                index = i;
                Mark m = episodeMarks.get(e.id);
                if (resume && m != null && m.resumable()) p.startPos = m.pos;
            }
            q.add(p);
        }
        PlayQueue.set(source, q, index, item.name);
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void openTrailer() {
        String id = Text.youtubeId(trailer);
        if (id.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + id)));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id)));
            } catch (Exception e2) {
                Ui.toast(this, "Für den Trailer wird die YouTube-App benötigt.");
            }
        }
    }

    // ---- Staffeln und Folgen ---------------------------------------------------

    private static final class H extends RecyclerView.ViewHolder {
        TextView a, b, c;
        ImageView image, check;
        View progress;

        H(View v) {
            super(v);
        }
    }

    private final class SeasonAdapter extends RecyclerView.Adapter<H> {
        @Override
        public int getItemCount() {
            return seasons.size();
        }

        @Override
        public H onCreateViewHolder(ViewGroup parent, int type) {
            Context c = parent.getContext();
            TextView t = Ui.line(c, 11.5f, Ui.TEXT, true);
            t.setTextColor(Ui.states(Ui.ON_GOLD, Ui.GOLD_HOT, Ui.DIM));
            t.setGravity(Gravity.CENTER);
            t.setBackground(Ui.buttonBg(16));
            t.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
            t.setFocusable(true);
            t.setClickable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(32));
            lp.rightMargin = Ui.dp(6);
            lp.leftMargin = Ui.dp(4);
            t.setLayoutParams(lp);
            final H h = new H(t);
            h.a = t;
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (episodeAdapter.getItemCount() > 0) episodeList.requestFocus();
                }
            });
            return h;
        }

        @Override
        public void onBindViewHolder(H h, int pos) {
            int s = seasons.get(pos);
            h.a.setText(s == 0 ? "Specials" : "Staffel " + s);
            h.itemView.setActivated(s == season);
        }
    }

    private final class EpisodeAdapter extends RecyclerView.Adapter<H> {
        @Override
        public int getItemCount() {
            return shown.size();
        }

        @Override
        public H onCreateViewHolder(ViewGroup parent, int type) {
            Context c = parent.getContext();
            LinearLayout l = new LinearLayout(c);
            l.setGravity(Gravity.CENTER_VERTICAL);
            l.setBackground(Ui.rowBg(10));
            l.setPadding(Ui.dp(8), 0, Ui.dp(14), 0);
            l.setFocusable(true);
            l.setClickable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.dp(700), Ui.dp(62));
            lp.bottomMargin = Ui.dp(3);
            lp.leftMargin = Ui.dp(4);
            l.setLayoutParams(lp);
            final H h = new H(l);
            FrameLayout box = new FrameLayout(c);
            box.setBackground(Ui.rect(Ui.SURFACE2, 6));
            Ui.round(box, 6);
            h.image = new ImageView(c);
            h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            box.addView(h.image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            h.progress = new View(c);
            h.progress.setBackgroundColor(Ui.GOLD);
            box.addView(h.progress, new FrameLayout.LayoutParams(0, Ui.dp(3), Gravity.BOTTOM | Gravity.START));
            l.addView(box, new LinearLayout.LayoutParams(Ui.dp(84), Ui.dp(47)));
            h.c = Ui.line(c, 15f, Ui.FAINT, true);
            h.c.setGravity(Gravity.CENTER);
            l.addView(h.c, new LinearLayout.LayoutParams(Ui.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            h.a = Ui.line(c, 12.5f, Ui.TEXT, false);
            texts.addView(h.a);
            h.b = Ui.line(c, 9.5f, Ui.FAINT, false);
            h.b.setPadding(0, Ui.dp(4), 0, 0);
            texts.addView(h.b);
            l.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            h.check = Ui.icon(c, Ui.I_CHECK, Ui.GOLD);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14));
            cp.leftMargin = Ui.dp(10);
            l.addView(h.check, cp);
            l.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int p = h.getAdapterPosition();
                    if (p >= 0 && p < shown.size()) play(shown.get(p), true);
                }
            });
            return h;
        }

        @Override
        public void onBindViewHolder(H h, int pos) {
            Episode e = shown.get(pos);
            Mark m = episodeMarks.get(e.id);
            h.c.setText(String.valueOf(e.num));
            h.a.setText(e.title);
            StringBuilder b = new StringBuilder();
            if (e.durationSecs > 0) b.append(Text.minutes(e.durationSecs));
            if (!Text.empty(e.plot)) {
                if (b.length() > 0) b.append("   ·   ");
                b.append(e.plot);
            }
            h.b.setText(b);
            h.b.setVisibility(b.length() > 0 ? View.VISIBLE : View.GONE);
            boolean seen = m != null && m.pos > 30000 && !m.resumable();
            h.check.setVisibility(seen ? View.VISIBLE : View.INVISIBLE);
            ViewGroup.LayoutParams pp = h.progress.getLayoutParams();
            pp.width = m != null && m.resumable() ? Math.max(Ui.dp(3), Ui.dp(84) * m.percent() / 100) : 0;
            h.progress.setLayoutParams(pp);
            Img.load(h.image, Text.empty(e.icon) ? item.icon : e.icon, Ui.dp(84), Ui.dp(47), true);
        }

        @Override
        public void onViewRecycled(H h) {
            Img.clear(h.image);
        }
    }
}

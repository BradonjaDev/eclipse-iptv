package de.eclipseiptv.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.eclipseiptv.data.Epg;
import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.Programme;

/** Zeigt zu einem Sender, was gerade laeuft und was danach kommt. */
public final class EpgPanel extends LinearLayout {
    private static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.GERMANY);
    private final ImageView logo;
    private final TextView name, nowLabel, title, time, desc, nextLabel, next;
    private final View barFill;
    private final FrameLayout bar;

    /** Platzsparend fuer die Spalte unter der Vorschau. */
    public void compact() {
        setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(12));
        ViewGroup.LayoutParams lp = logo.getLayoutParams();
        lp.width = Ui.dp(56);
        lp.height = Ui.dp(36);
        logo.setLayoutParams(lp);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(12.5f));
        Ui.lines(desc, 3);
        Ui.lines(next, 3);
        ((LayoutParams) nowLabel.getLayoutParams()).topMargin = Ui.dp(10);
        ((LayoutParams) nextLabel.getLayoutParams()).topMargin = Ui.dp(10);
    }

    public static String hm(long t) {
        return HM.format(new Date(t));
    }

    public EpgPanel(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setBackground(Ui.outline(0xE6120C0F, 12, Ui.LINE, 0.6f));
        setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(14));

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(Ui.rect(0x14FFFFFF, 8));
        logo.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        head.addView(logo, new LayoutParams(Ui.dp(72), Ui.dp(46)));
        name = Ui.text(c, 14f, Ui.TEXT, true);
        Ui.lines(name, 2);
        LayoutParams np = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        np.leftMargin = Ui.dp(12);
        head.addView(name, np);
        addView(head);

        nowLabel = Ui.label(c, "Jetzt");
        nowLabel.setTextColor(Ui.ACCENT_HOT);
        addView(nowLabel, top(16));
        title = Ui.text(c, 13f, Ui.TEXT, true);
        Ui.lines(title, 2);
        addView(title, top(5));
        time = Ui.line(c, 10f, Ui.DIM, false);
        addView(time, top(4));
        bar = new FrameLayout(c);
        bar.setBackground(Ui.rect(0x22FFFFFF, 2));
        barFill = new View(c);
        barFill.setBackground(Ui.rect(Ui.ACCENT, 2));
        bar.addView(barFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LayoutParams bp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(3));
        bp.topMargin = Ui.dp(8);
        addView(bar, bp);
        desc = Ui.text(c, 10.5f, Ui.DIM, false);
        desc.setLineSpacing(Ui.px(2.5f), 1f);
        Ui.lines(desc, 8);
        addView(desc, top(10));
        nextLabel = Ui.label(c, "Danach");
        addView(nextLabel, top(16));
        next = Ui.text(c, 10.5f, Ui.DIM, false);
        next.setLineSpacing(Ui.px(4f), 1f);
        Ui.lines(next, 4);
        addView(next, top(6));
    }

    private LayoutParams top(float margin) {
        LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = Ui.dp(margin);
        return p;
    }

    /** Zeigt den Sender sofort; das Programm folgt, sobald es geladen ist. */
    public void channel(Item it) {
        name.setText(it.name);
        Img.load(logo, it.icon, Ui.dp(72), Ui.dp(46), false);
        List<Programme> c = Epg.cached(it);
        if (c != null) programme(c);
        else loading();
    }

    private void loading() {
        nowLabel.setVisibility(INVISIBLE);
        title.setText("");
        time.setText("");
        desc.setText("");
        bar.setVisibility(INVISIBLE);
        nextLabel.setVisibility(INVISIBLE);
        next.setText("");
    }

    public void programme(List<Programme> list) {
        Programme now = Epg.now(list);
        List<Programme> after = Epg.next(list);
        if (now == null && after.isEmpty()) {
            loading();
            desc.setText("Für diesen Sender liegt kein Programm vor.");
            return;
        }
        nowLabel.setVisibility(now != null ? VISIBLE : INVISIBLE);
        bar.setVisibility(now != null ? VISIBLE : INVISIBLE);
        if (now != null) {
            title.setText(now.title);
            time.setText(hm(now.start) + " – " + hm(now.end) + " Uhr");
            desc.setText(now.desc);
            final float f = Math.max(0f, Math.min(1f, (System.currentTimeMillis() - now.start) / (float) (now.end - now.start)));
            bar.post(new Runnable() {
                @Override
                public void run() {
                    ViewGroup.LayoutParams p = barFill.getLayoutParams();
                    p.width = Math.round(bar.getWidth() * f);
                    barFill.setLayoutParams(p);
                }
            });
        } else {
            title.setText("");
            time.setText("");
            desc.setText("");
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < after.size() && i < 4; i++) {
            if (i > 0) b.append('\n');
            b.append(hm(after.get(i).start)).append("   ").append(after.get(i).title);
        }
        nextLabel.setVisibility(b.length() > 0 ? VISIBLE : INVISIBLE);
        next.setText(b);
    }
}

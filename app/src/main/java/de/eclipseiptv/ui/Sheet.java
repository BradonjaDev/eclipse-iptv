package de.eclipseiptv.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Auswahlfenster im Stil der App: Titel, optionaler Hinweis und eine Liste von Aktionen. */
public final class Sheet extends Dialog {
    private final LinearLayout rows;
    private final TextView message;
    private View first;

    public Sheet(Context c, String title) {
        super(c, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0xB3000000);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.outline(Ui.SURFACE, 16, Ui.LINE, 0.7f));
        panel.setPadding(Ui.dp(18), Ui.dp(18), Ui.dp(18), Ui.dp(14));
        panel.setClickable(true);
        TextView t = Ui.text(c, 15f, Ui.TEXT, true);
        t.setText(title);
        Ui.lines(t, 2);
        t.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
        panel.addView(t);
        message = Ui.text(c, 11f, Ui.DIM, false);
        message.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), 0);
        message.setLineSpacing(Ui.px(2.5f), 1f);
        message.setVisibility(View.GONE);
        panel.addView(message);
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(12);
        panel.addView(scroll, sp);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Ui.dp(340), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        pp.topMargin = pp.bottomMargin = Ui.dp(30);
        root.addView(panel, pp);
        setContentView(root);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public Sheet message(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        return this;
    }

    /** Fuegt eine Zeile hinzu. value: kleiner Text rechts (z. B. der aktuelle Wert), darf null sein. */
    public Sheet add(String label, String value, final Runnable action) {
        return add(label, value, false, action);
    }

    public Sheet add(String label, String value, boolean current, final Runnable action) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.rowBg(9));
        row.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setActivated(current);
        TextView l = Ui.line(c, 12.5f, Ui.TEXT, false);
        l.setTextColor(Ui.rowText(Ui.TEXT));
        l.setDuplicateParentStateEnabled(true);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (value != null && !value.isEmpty()) {
            TextView v = Ui.line(c, 10.5f, Ui.ACCENT_HOT, false);
            v.setTextColor(Ui.rowValue(Ui.ACCENT_HOT));
            v.setDuplicateParentStateEnabled(true);
            v.setText(value);
            v.setPadding(Ui.dp(10), 0, 0, 0);
            row.addView(v);
        } else if (current) {
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14));
            row.addView(Ui.icon(c, Ui.I_CHECK, Ui.states(Ui.WHITE, Ui.ACCENT_HOT, Ui.ACCENT_HOT)), ip);
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (action != null) action.run();
            }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(38));
        rp.bottomMargin = Ui.dp(2);
        rows.addView(row, rp);
        if (first == null || current) first = row;
        return this;
    }

    @Override
    public void show() {
        super.show();
        if (getWindow() != null) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        if (first != null) {
            first.requestFocus();
            // Manche Geraete vergeben den Fokus erst nach dem Aufbau des Fensters
            first.post(new Runnable() {
                @Override
                public void run() {
                    if (getCurrentFocus() == null || !getCurrentFocus().isFocused()) first.requestFocus();
                }
            });
        }
    }

    /** Einfache Rueckfrage mit einer bestaetigenden Aktion und "Abbrechen". */
    public static void confirm(Context c, String title, String text, String yes, Runnable action) {
        new Sheet(c, title).message(text).add(yes, null, action).add("Abbrechen", null, null).show();
    }
}

package de.eclipseiptv.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.graphics.drawable.StateListDrawable;

import java.util.List;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Item;
import de.eclipseiptv.data.Source;

/** Suche ueber Sender, Filme und Serien. */
public final class SearchView extends FrameLayout implements Section {
    private final Host host;
    private final Rows rows;
    private final EditText input;
    private final TextView hint;
    private int gen;
    private String shown = null;

    public SearchView(Context c, Host h) {
        super(c);
        host = h;
        rows = new Rows(c, h);
        addView(rows, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Ui.dp(22), Ui.dp(24), Ui.dp(28), Ui.dp(4));
        TextView t = Ui.line(c, 17f, Ui.TEXT, true);
        t.setText("Suche");
        head.addView(t);
        input = new EditText(c);
        input.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(14f));
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.FAINT);
        input.setHint("Sender, Filme und Serien durchsuchen");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, Ui.outline(Ui.SURFACE3, 10, Ui.ACCENT, 1.5f));
        bg.addState(new int[]{}, Ui.outline(Ui.SURFACE2, 10, Ui.LINE, 0.7f));
        input.setBackground(bg);
        input.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        input.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(420), Ui.dp(40));
        ip.topMargin = Ui.dp(12);
        head.addView(input, ip);
        hint = Ui.text(c, 11f, Ui.FAINT, false);
        hint.setPadding(Ui.dp(2), Ui.dp(12), 0, 0);
        hint.setText("OK drücken, um die Tastatur zu öffnen.");
        head.addView(hint);
        rows.box().addView(head);

        input.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(android.view.View v, boolean has) {
                if (has) rows.smoothScrollTo(0, 0);
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Bg.cancel(run);
                Bg.uiDelayed(run, 350);
            }
        });
    }

    private final Runnable run = new Runnable() {
        @Override
        public void run() {
            search(input.getText().toString().trim());
        }
    };

    @Override
    public void refresh() {
        if (shown != null) {
            String q = shown;
            shown = null;
            search(q);
        }
    }

    private void search(final String q) {
        final Source s = host.source();
        if (s == null || q.equals(shown)) return;
        shown = q;
        final int g = ++gen;
        if (q.length() < 2) {
            rows.clearRows(1);
            hint.setVisibility(VISIBLE);
            hint.setText(q.isEmpty() ? "OK drücken, um die Tastatur zu öffnen." : "Mindestens zwei Zeichen eingeben.");
            return;
        }
        final boolean adult = host.prefs().adult();
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final List<Item> live = host.db().search(s.id, Item.LIVE, q, adult, 60);
                final List<Item> vod = host.db().search(s.id, Item.VOD, q, adult, 60);
                final List<Item> ser = host.db().search(s.id, Item.SERIES, q, adult, 60);
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (g != gen) return;
                        rows.clearRows(1);
                        rows.addRow("Sender (" + live.size() + (live.size() == 60 ? "+" : "") + ")", Cards.WIDE, 138, Rows.entries(live));
                        rows.addRow("Filme (" + vod.size() + (vod.size() == 60 ? "+" : "") + ")", Cards.POSTER, 108, Rows.entries(vod));
                        rows.addRow("Serien (" + ser.size() + (ser.size() == 60 ? "+" : "") + ")", Cards.POSTER, 108, Rows.entries(ser));
                        hint.setVisibility(rows.isEmpty() ? VISIBLE : GONE);
                        hint.setText("Nichts gefunden für „" + q + "“.");
                    }
                });
            }
        });
    }
}

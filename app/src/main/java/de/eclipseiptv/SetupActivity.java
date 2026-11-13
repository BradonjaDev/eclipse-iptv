package de.eclipseiptv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Db;
import de.eclipseiptv.data.Prefs;
import de.eclipseiptv.data.Source;
import de.eclipseiptv.data.Sync;
import de.eclipseiptv.ui.EclipseView;
import de.eclipseiptv.ui.Sheet;
import de.eclipseiptv.ui.Ui;

/** Playlist verbinden: per Handy (QR-Code) oder direkt am Geraet; vorhandene Playlists verwalten. */
public final class SetupActivity extends Activity implements Sync.Listener, SetupServer.Handler {
    private Db db;
    private Prefs prefs;
    private SetupServer server;
    private TextView address, status;
    private ImageView qr;
    private ProgressBar spinner;
    private LinearLayout sourceBox;
    private View firstButton;
    private int startGeneration;
    private boolean waiting, wasRunning;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        db = Db.get(this);
        prefs = new Prefs(this);
        startGeneration = Sync.generation();
        waiting = Sync.running();
        build();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setBackground(MainActivity.backdrop());
        root.setPadding(Ui.dp(56), Ui.dp(40), Ui.dp(48), Ui.dp(30));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        root.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));
        left.addView(new EclipseView(this), new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64)));
        TextView t = Ui.text(this, 27f, Ui.TEXT, true);
        t.setText("Playlist verbinden");
        t.setPadding(0, Ui.dp(14), 0, 0);
        left.addView(t);
        TextView p = Ui.text(this, 12.5f, Ui.DIM, false);
        p.setLineSpacing(Ui.px(4), 1f);
        p.setText("Am bequemsten geht es mit dem Handy: QR-Code scannen oder die Adresse im Browser öffnen "
                + "und dort den Link eintragen. Handy und Fernseher müssen im selben WLAN sein.");
        p.setPadding(0, Ui.dp(8), Ui.dp(30), 0);
        left.addView(p);

        LinearLayout connect = new LinearLayout(this);
        connect.setGravity(Gravity.CENTER_VERTICAL);
        connect.setPadding(0, Ui.dp(22), 0, 0);
        qr = new ImageView(this);
        qr.setBackground(Ui.rect(0xFFFFFFFF, 10));
        qr.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        connect.addView(qr, new LinearLayout.LayoutParams(Ui.dp(132), Ui.dp(132)));
        LinearLayout addr = new LinearLayout(this);
        addr.setOrientation(LinearLayout.VERTICAL);
        addr.setPadding(Ui.dp(22), 0, 0, 0);
        addr.addView(Ui.label(this, "Adresse"));
        address = Ui.text(this, 19f, Ui.ACCENT_HOT, true);
        address.setPadding(0, Ui.dp(6), 0, Ui.dp(18));
        addr.addView(address);
        firstButton = button("Am Gerät eingeben", new Runnable() {
            @Override
            public void run() {
                chooseInput();
            }
        });
        addr.addView(firstButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(36)));
        connect.addView(addr);
        left.addView(connect);

        LinearLayout st = new LinearLayout(this);
        st.setGravity(Gravity.CENTER_VERTICAL);
        st.setPadding(0, Ui.dp(20), Ui.dp(20), 0);
        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(20), Ui.dp(20));
        sp.rightMargin = Ui.dp(10);
        st.addView(spinner, sp);
        status = Ui.text(this, 12.5f, Ui.TEXT, false);
        Ui.lines(status, 3);
        st.addView(status);
        left.addView(st);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setBackground(Ui.outline(0xB3121019, 16, Ui.LINE, 0.6f));
        right.setPadding(Ui.dp(14), Ui.dp(18), Ui.dp(14), Ui.dp(14));
        TextView h = Ui.line(this, 14f, Ui.TEXT, true);
        h.setText("Deine Playlists");
        h.setPadding(Ui.dp(10), 0, 0, Ui.dp(10));
        right.addView(h);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        sourceBox = new LinearLayout(this);
        sourceBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(sourceBox);
        right.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.85f));
        setContentView(root);
        firstButton.requestFocus();
    }

    private View button(String label, final Runnable action) {
        TextView b = Ui.line(this, 12.5f, Ui.TEXT, true);
        b.setTextColor(Ui.states(Ui.ON_ACCENT, Ui.TEXT, Ui.TEXT));
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.buttonBg(18));
        b.setActivated(true);
        b.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return b;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Sync.add(this);
        server = new SetupServer(this);
        String a = server.start() ? server.address() : null;
        if (a == null) {
            address.setText("Kein Netzwerk gefunden");
            qr.setVisibility(View.INVISIBLE);
        } else {
            address.setText(a.replace("http://", ""));
            qr.setVisibility(View.VISIBLE);
            qr.setImageBitmap(qrCode(a, Ui.dp(116)));
        }
        loadSources();
        onSync();
    }

    @Override
    protected void onStop() {
        Sync.remove(this);
        if (server != null) server.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
    }

    private static Bitmap qrCode(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int w = m.getWidth(), h = m.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = m.get(x, y) ? 0xFF07060B : 0xFFFFFFFF;
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.RGB_565);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Einlesen --------------------------------------------------------------

    /** Wird vom Webserver (Handy) und von der Eingabe am Geraet aufgerufen. */
    @Override
    public String add(Map<String, String> f) {
        final Source s;
        try {
            if ("xtream".equals(f.get("mode"))) s = Source.xtream(f.get("server"), f.get("user"), f.get("pass"));
            else s = Source.fromUrl(f.get("url"));
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        String name = f.get("name");
        if (name != null && !name.trim().isEmpty()) s.name = name.trim();
        for (Source old : db.sources()) {
            boolean same = s.isXtream() ? old.isXtream() && old.base.equalsIgnoreCase(s.base) && old.user.equals(s.user)
                    : !old.isXtream() && old.url.equals(s.url);
            if (same) {
                s.id = old.id;
                if (name == null || name.trim().isEmpty()) s.name = old.name;
            }
        }
        if (!Sync.start(this, s, true)) return "Es wird gerade schon eine Playlist geladen. Bitte kurz warten.";
        waiting = true;
        return null;
    }

    @Override
    public void onSync() {
        boolean running = Sync.running();
        spinner.setVisibility(running ? View.VISIBLE : View.GONE);
        String err = Sync.error();
        if (running) {
            status.setTextColor(Ui.TEXT);
            status.setText(Sync.status());
        } else if (err != null) {
            status.setTextColor(Ui.WARN);
            status.setText(err);
            waiting = false;
        } else if (waiting && Sync.generation() != startGeneration) {
            finish();
        } else {
            status.setText("");
        }
        if (!running && wasRunning) loadSources();
        wasRunning = running;
    }

    // ---- Vorhandene Playlists --------------------------------------------------

    private void loadSources() {
        Bg.run(new Runnable() {
            @Override
            public void run() {
                final List<Source> all = db.sources();
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        sourceBox.removeAllViews();
                        if (all.isEmpty()) {
                            TextView e = Ui.text(SetupActivity.this, 11.5f, Ui.FAINT, false);
                            e.setText("Noch keine Playlist eingerichtet.");
                            e.setPadding(Ui.dp(10), Ui.dp(4), 0, 0);
                            sourceBox.addView(e);
                        }
                        for (Source s : all) sourceBox.addView(sourceRow(s));
                    }
                });
            }
        });
    }

    private View sourceRow(final Source s) {
        boolean active = s.id == prefs.source();
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.rowBg(10));
        r.setActivated(active);
        r.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        r.setFocusable(true);
        r.setClickable(true);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.line(this, 13f, Ui.TEXT, false);
        n.setText(s.name);
        texts.addView(n);
        TextView d = Ui.line(this, 9.5f, Ui.FAINT, false);
        d.setText((s.isXtream() ? "Xtream" : "M3U") + "  ·  " + s.nLive + " Sender  ·  " + s.nVod + " Filme  ·  " + s.nSeries + " Serien");
        d.setPadding(0, Ui.dp(3), 0, 0);
        texts.addView(d);
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (active) {
            TextView a = Ui.line(this, 9.5f, Ui.ACCENT_HOT, true);
            a.setText("AKTIV");
            a.setLetterSpacing(0.1f);
            r.addView(a);
        }
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sourceMenu(s);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(3);
        r.setLayoutParams(lp);
        return r;
    }

    private void sourceMenu(final Source s) {
        Sheet sheet = new Sheet(this, s.name);
        if (s.id != prefs.source()) {
            sheet.add("Diese Playlist verwenden", null, new Runnable() {
                @Override
                public void run() {
                    prefs.source(s.id);
                    finish();
                }
            });
        }
        sheet.add("Jetzt aktualisieren", null, new Runnable() {
            @Override
            public void run() {
                if (!Sync.start(SetupActivity.this, s, false)) Ui.toast(SetupActivity.this, "Es wird gerade schon geladen.");
            }
        });
        sheet.add("Entfernen", null, new Runnable() {
            @Override
            public void run() {
                Sheet.confirm(SetupActivity.this, "Playlist entfernen?", "„" + s.name + "“ wird samt Favoriten und Merkern von diesem Gerät gelöscht.",
                        "Entfernen", new Runnable() {
                            @Override
                            public void run() {
                                if (Sync.running()) {
                                    Ui.toast(SetupActivity.this, "Bitte warten, bis das Laden fertig ist.");
                                    return;
                                }
                                db.deleteSource(s.id);
                                if (prefs.source() == s.id) prefs.source(0);
                                loadSources();
                                firstButton.requestFocus();
                            }
                        });
            }
        });
        sheet.show();
    }

    // ---- Eingabe am Geraet -----------------------------------------------------

    private void chooseInput() {
        new Sheet(this, "Was möchtest du eingeben?")
                .add("M3U-Link", null, new Runnable() {
                    @Override
                    public void run() {
                        input(false);
                    }
                })
                .add("Xtream-Zugang (Server, Benutzer, Passwort)", null, new Runnable() {
                    @Override
                    public void run() {
                        input(true);
                    }
                }).show();
    }

    private void input(final boolean xtream) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xCC000000);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.outline(Ui.SURFACE, 16, Ui.LINE, 0.7f));
        panel.setPadding(Ui.dp(22), Ui.dp(20), Ui.dp(22), Ui.dp(18));
        TextView t = Ui.text(this, 15f, Ui.TEXT, true);
        t.setText(xtream ? "Xtream-Zugang" : "M3U-Link");
        panel.addView(t);
        final String[] hints = xtream ? new String[]{"Server, z. B. http://beispiel.de:8080", "Benutzername", "Passwort"} : new String[]{"http://…"};
        final EditText[] fields = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            EditText e = new EditText(this);
            e.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.px(13f));
            e.setTextColor(Ui.TEXT);
            e.setHintTextColor(Ui.FAINT);
            e.setHint(hints[i]);
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, Ui.outline(Ui.SURFACE3, 10, Ui.ACCENT, 1.5f));
            bg.addState(new int[]{}, Ui.outline(Ui.SURFACE2, 10, Ui.LINE, 0.7f));
            e.setBackground(bg);
            e.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(38));
            lp.topMargin = Ui.dp(10);
            panel.addView(e, lp);
            fields[i] = e;
        }
        View go = button("Playlist laden", new Runnable() {
            @Override
            public void run() {
                Map<String, String> f = new java.util.HashMap<>();
                if (xtream) {
                    f.put("mode", "xtream");
                    f.put("server", fields[0].getText().toString());
                    f.put("user", fields[1].getText().toString());
                    f.put("pass", fields[2].getText().toString());
                } else {
                    f.put("mode", "m3u");
                    f.put("url", fields[0].getText().toString());
                }
                String err = add(f);
                if (err != null) {
                    Ui.toast(SetupActivity.this, err);
                    return;
                }
                d.dismiss();
                onSync();
            }
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(36));
        gp.topMargin = Ui.dp(16);
        panel.addView(go, gp);
        root.addView(panel, new FrameLayout.LayoutParams(Ui.dp(460), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        d.setContentView(root);
        if (d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
        fields[0].requestFocus();
    }
}

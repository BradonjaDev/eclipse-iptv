package de.eclipseiptv.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import de.eclipseiptv.data.Bg;
import de.eclipseiptv.data.Source;
import de.eclipseiptv.data.Sync;

/** Einstellungen. */
public final class SettingsView extends ScrollView implements Section {
    private final Host host;
    private final LinearLayout box;

    public SettingsView(Context c, Host h) {
        super(c);
        host = h;
        setVerticalScrollBarEnabled(false);
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(22), Ui.dp(24), Ui.dp(22), Ui.dp(30));
        addView(box, new LayoutParams(Ui.dp(620), ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static String ago(long t) {
        if (t <= 0) return "noch nie";
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 2) return "gerade eben";
        if (m < 60) return "vor " + m + " Minuten";
        if (m < 48 * 60) return "vor " + (m / 60) + " Stunden";
        return "vor " + (m / 1440) + " Tagen";
    }

    private static String n(int v) {
        return String.format("%,d", v).replace(',', '.');
    }

    @Override
    public void refresh() {
        final Source s = host.source();
        if (s == null) return;
        int focusIndex = -1;
        View f = box.getFocusedChild();
        if (f != null) focusIndex = box.indexOfChild(f);
        box.removeAllViews();
        Context c = getContext();
        TextView t = Ui.line(c, 17f, Ui.TEXT, true);
        t.setText("Einstellungen");
        box.addView(t);

        head("Playlist");
        row(s.name, Sync.running() ? Sync.status()
                        : n(s.nLive) + " Sender · " + n(s.nVod) + " Filme · " + n(s.nSeries) + " Serien · aktualisiert " + ago(s.updated),
                Sync.running() ? "läuft …" : "Aktualisieren", new Runnable() {
                    @Override
                    public void run() {
                        host.refreshNow();
                    }
                });
        row("Playlists verwalten", "Weitere Playlist hinzufügen, wechseln oder entfernen", null, new Runnable() {
            @Override
            public void run() {
                host.openSetup();
            }
        });
        row("Automatisch aktualisieren", "Einmal am Tag beim Start der App", onOff(host.prefs().autoRefresh()), new Runnable() {
            @Override
            public void run() {
                host.prefs().autoRefresh(!host.prefs().autoRefresh());
                refresh();
            }
        });

        head("Inhalte");
        row("Inhalte für Erwachsene", "Kategorien und Titel ab 18 anzeigen", onOff(host.prefs().adult()), new Runnable() {
            @Override
            public void run() {
                if (host.prefs().adult()) {
                    host.prefs().adult(false);
                    host.invalidate();
                } else {
                    Sheet.confirm(getContext(), "Inhalte für Erwachsene anzeigen?", "Diese Inhalte erscheinen dann in den Listen und in der Suche.",
                            "Anzeigen", new Runnable() {
                                @Override
                                public void run() {
                                    host.prefs().adult(true);
                                    host.invalidate();
                                }
                            });
                }
            }
        });
        final int[] hidden = new int[1];
        final TextView hiddenValue = row("Ausgeblendete Kategorien", "Kategorien blendest du aus, indem du sie mit OK lange drückst", "…", new Runnable() {
            @Override
            public void run() {
                if (hidden[0] == 0) {
                    Ui.toast(getContext(), "Es ist nichts ausgeblendet.");
                    return;
                }
                Sheet.confirm(getContext(), "Alle Kategorien wieder anzeigen?", hidden[0] + " ausgeblendete Kategorien werden wieder eingeblendet.",
                        "Wieder anzeigen", new Runnable() {
                            @Override
                            public void run() {
                                host.db().unhideAll(s.id);
                                host.invalidate();
                            }
                        });
            }
        });
        Bg.run(new Runnable() {
            @Override
            public void run() {
                hidden[0] = host.db().hiddenCount(s.id);
                Bg.ui(new Runnable() {
                    @Override
                    public void run() {
                        hiddenValue.setText(hidden[0] == 0 ? "keine" : String.valueOf(hidden[0]));
                    }
                });
            }
        });

        head("Wiedergabe");
        row("Format für Sender", "Wenn Sender nicht starten oder ruckeln, das andere Format probieren",
                host.prefs().hls() ? "HLS" : "MPEG-TS", new Runnable() {
                    @Override
                    public void run() {
                        host.prefs().hls(!host.prefs().hls());
                        refresh();
                    }
                });

        head("Über");
        String version = "";
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        row("Eclipse IPTV " + version, "IPTV-Player für große Playlists. Die App bringt keine Inhalte mit.", null, null);

        if (focusIndex >= 0 && focusIndex < box.getChildCount()) box.getChildAt(focusIndex).requestFocus();
    }

    private static String onOff(boolean b) {
        return b ? "An" : "Aus";
    }

    private void head(String s) {
        TextView l = Ui.label(getContext(), s);
        l.setPadding(Ui.dp(10), Ui.dp(20), 0, Ui.dp(6));
        box.addView(l);
    }

    private TextView row(String title, String desc, String value, final Runnable action) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(Ui.rowBg(10));
        r.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(14), Ui.dp(9));
        r.setFocusable(true);
        r.setClickable(action != null);
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.line(c, 13f, Ui.TEXT, false);
        t.setTextColor(Ui.rowText(Ui.TEXT));
        t.setDuplicateParentStateEnabled(true);
        t.setText(title);
        texts.addView(t);
        TextView d = Ui.text(c, 10f, Ui.FAINT, false);
        d.setTextColor(Ui.states(0xFFFFE3E7, Ui.FAINT, Ui.FAINT));
        d.setDuplicateParentStateEnabled(true);
        Ui.lines(d, 2);
        d.setText(desc);
        d.setPadding(0, Ui.dp(3), 0, 0);
        texts.addView(d);
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = Ui.line(c, 12f, Ui.ACCENT_HOT, true);
        v.setTextColor(Ui.rowValue(Ui.ACCENT_HOT));
        v.setDuplicateParentStateEnabled(true);
        v.setText(value == null ? "" : value);
        v.setPadding(Ui.dp(16), 0, 0, 0);
        r.addView(v);
        if (action != null) {
            r.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    action.run();
                }
            });
        }
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = Ui.dp(3);
        box.addView(r, rp);
        return v;
    }
}

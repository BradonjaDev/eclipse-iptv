package de.eclipseiptv;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import de.eclipseiptv.data.Sync;

/**
 * Kleiner Webserver fuer die Einrichtung vom Handy. Laeuft nur, solange der
 * Einrichtungsbildschirm offen ist.
 */
final class SetupServer {
    interface Handler {
        /** Nimmt die Formulardaten entgegen. Liefert eine Fehlermeldung oder null, wenn das Einlesen gestartet wurde. */
        String add(Map<String, String> form);
    }

    private final Handler handler;
    private ServerSocket socket;
    private volatile boolean running;
    private int port;

    SetupServer(Handler h) {
        handler = h;
    }

    boolean start() {
        for (int p = 8080; p < 8090; p++) {
            try {
                socket = new ServerSocket(p);
                port = p;
                break;
            } catch (Exception ignored) {
            }
        }
        if (socket == null) return false;
        running = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        final Socket s = socket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                serve(s);
                            }
                        }).start();
                    } catch (Exception e) {
                        if (!running) return;
                    }
                }
            }
        }, "setup-server");
        t.setDaemon(true);
        t.start();
        return true;
    }

    void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
    }

    /** Adresse, unter der das Handy die Einrichtungsseite erreicht, oder null ohne Netz. */
    String address() {
        String ip = localIp();
        return ip == null || socket == null ? null : "http://" + ip + ":" + port;
    }

    static String localIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    if (a.isSiteLocalAddress()) return a.getHostAddress();
                    fallback = a.getHostAddress();
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }

    private void serve(Socket s) {
        try {
            s.setSoTimeout(10000);
            InputStream in = s.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int state = 0, b;
            while (state < 4 && (b = in.read()) >= 0 && head.size() < 16384) {
                head.write(b);
                if (b == '\r' && (state == 0 || state == 2)) state++;
                else if (b == '\n' && (state == 1 || state == 3)) state++;
                else state = 0;
            }
            String[] lines = head.toString("UTF-8").split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 2) return;
            int length = 0;
            for (String l : lines) {
                if (l.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(l.substring(15).trim());
            }
            byte[] body = new byte[Math.min(Math.max(length, 0), 65536)];
            int got = 0;
            while (got < body.length) {
                int r = in.read(body, got, body.length - got);
                if (r < 0) break;
                got += r;
            }
            String path = req[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            OutputStream out = s.getOutputStream();
            if ("POST".equals(req[0]) && "/add".equals(path)) {
                String err = handler.add(form(new String(body, 0, got, StandardCharsets.UTF_8)));
                send(out, "application/json", err == null ? "{\"ok\":true}" : "{\"error\":" + quote(err) + "}");
            } else if ("/status".equals(path)) {
                String err = Sync.error();
                send(out, "application/json", "{\"running\":" + Sync.running() + ",\"status\":" + quote(Sync.status())
                        + ",\"error\":" + (err == null ? "null" : quote(err)) + ",\"generation\":" + Sync.generation() + "}");
            } else if ("/".equals(path)) {
                send(out, "text/html; charset=utf-8", PAGE);
            } else {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
        } catch (Exception ignored) {
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void send(OutputStream out, String type, String body) throws Exception {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + data.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(data);
    }

    private static Map<String, String> form(String body) {
        Map<String, String> m = new HashMap<>();
        for (String part : body.split("&")) {
            int i = part.indexOf('=');
            if (i <= 0) continue;
            try {
                m.put(URLDecoder.decode(part.substring(0, i), "UTF-8"), URLDecoder.decode(part.substring(i + 1), "UTF-8").trim());
            } catch (Exception ignored) {
            }
        }
        return m;
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c < 0x20) b.append(' ');
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private static final String PAGE = "<!doctype html><html lang=de><head><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Eclipse IPTV einrichten</title><style>"
            + "*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Roboto,sans-serif;background:#070507;color:#f7f1ee;"
            + "background-image:radial-gradient(circle at 85% -10%,rgba(194,14,38,.42),transparent 55%);min-height:100vh}"
            + "main{max-width:520px;margin:0 auto;padding:28px 18px 60px}"
            + ".sun{width:56px;height:56px;border-radius:50%;background:radial-gradient(circle,#050304 0 49%,#fff 50% 52%,#ff2b42 55%,rgba(176,10,32,.6) 66%,transparent 74%)}"
            + "h1{font-size:26px;margin:14px 0 6px}p{color:#bba9ae;line-height:1.5;margin:0 0 18px}"
            + ".tabs{display:flex;gap:8px;margin:18px 0 14px}.tabs button{flex:1;padding:11px;border-radius:12px;border:1px solid #3a222b;"
            + "background:#120c0f;color:#bba9ae;font-size:15px}.tabs button.on{background:rgba(221,26,52,.22);border-color:#dd1a34;color:#ff8f9a}"
            + "label{display:block;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:#806a70;margin:14px 0 6px}"
            + "input,textarea{width:100%;padding:13px 14px;border-radius:12px;border:1px solid #3a222b;background:#120c0f;color:#f7f1ee;font-size:16px;font-family:inherit}"
            + "textarea{min-height:96px;resize:vertical;word-break:break-all}input:focus,textarea:focus{outline:none;border-color:#dd1a34}"
            + ".go{width:100%;margin-top:22px;padding:15px;border:1.5px solid #fff;border-radius:14px;background:#dd1a34;color:#fff;font-size:17px;font-weight:600}"
            + ".go:disabled{opacity:.5}#msg{margin-top:18px;padding:14px;border-radius:12px;background:#120c0f;border:1px solid #3a222b;display:none;line-height:1.5}"
            + "#msg.err{border-color:#ffb02e;color:#ffd28a}#msg.ok{border-color:#dd1a34;color:#ff8f9a}.hide{display:none}.note{font-size:13px;color:#806a70;line-height:1.45;margin-top:8px}"
            + "</style></head><body><main><div class=sun></div><h1>Eclipse IPTV einrichten</h1>"
            + "<p>Trage hier deine Playlist ein. Der Fernseher lädt sie danach von selbst.</p>"
            + "<div class=tabs><button id=t1 class=on type=button>M3U-Link</button><button id=t2 type=button>Xtream-Zugang</button></div>"
            + "<form id=f><div id=p1><label>Link zur Playlist</label><textarea name=url placeholder='http://…' autocapitalize=off autocorrect=off spellcheck=false></textarea>"
            + "<div class=note>Hat dein Anbieter mehrere Server (Xtream-Link mit get.php)? Dann die weiteren Server einfach in neue Zeilen unter den Link schreiben.</div></div>"
            + "<div id=p2 class=hide><label>Server</label><input name=server placeholder='http://beispiel.de:8080' autocapitalize=off autocorrect=off>"
            + "<label>Benutzername</label><input name=user autocapitalize=off autocorrect=off><label>Passwort</label><input name=pass autocapitalize=off autocorrect=off>"
            + "<label>Ausweich-Server (freiwillig)</label><textarea name=alts placeholder='http://ersatz.beispiel.de:8080' autocapitalize=off autocorrect=off spellcheck=false></textarea>"
            + "<div class=note>Eine Adresse pro Zeile. Antwortet der Haupt-Server nicht, wechselt die App von selbst zum nächsten.</div></div>"
            + "<label>Name (freiwillig)</label><input name=name placeholder='z. B. Wohnzimmer'>"
            + "<button class=go id=go>Playlist laden</button></form><div id=msg></div></main><script>"
            + "var x=false,t1=document.getElementById('t1'),t2=document.getElementById('t2'),msg=document.getElementById('msg'),go=document.getElementById('go');"
            + "function tab(v){x=v;t1.className=v?'':'on';t2.className=v?'on':'';document.getElementById('p1').className=v?'hide':'';document.getElementById('p2').className=v?'':'hide'}"
            + "t1.onclick=function(){tab(false)};t2.onclick=function(){tab(true)};"
            + "function show(c,s){msg.className=c;msg.style.display='block';msg.textContent=s}"
            + "function poll(){fetch('/status').then(function(r){return r.json()}).then(function(s){"
            + "if(s.running){show('',s.status);setTimeout(poll,800)}else if(s.error){show('err',s.error);go.disabled=false}"
            + "else{show('ok','Fertig! Die Playlist ist auf dem Fernseher eingerichtet.');go.disabled=false}}).catch(function(){setTimeout(poll,1500)})}"
            + "document.getElementById('f').onsubmit=function(e){e.preventDefault();var d=new FormData(e.target),b=[];"
            + "d.forEach(function(v,k){b.push(encodeURIComponent(k)+'='+encodeURIComponent(v))});b.push('mode='+(x?'xtream':'m3u'));"
            + "go.disabled=true;show('','Wird gestartet …');fetch('/add',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:b.join('&')})"
            + ".then(function(r){return r.json()}).then(function(r){if(r.error){show('err',r.error);go.disabled=false}else setTimeout(poll,500)})"
            + ".catch(function(){show('err','Der Fernseher ist nicht erreichbar. Ist die App noch auf diesem Bildschirm?');go.disabled=false})};"
            + "</script></body></html>";
}

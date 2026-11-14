"""Logo-Varianten: Icon (192) und TV-Banner (640x360). Aufruf: python3 logos.py <Zielordner>

Das App-Logo ist Variante 4 ("Aufgang"). In die App uebernehmen:
  python3 logos.py /tmp/logos
  rsvg-convert -w 192 -h 192 /tmp/logos/logo-4-aufgang-icon.svg -o ../../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
  rsvg-convert -w 320 -h 180 /tmp/logos/logo-4-aufgang-banner.svg -o ../../app/src/main/res/drawable-xhdpi/banner.png
"""
import math, sys, os

FONT = "Noto Sans, Liberation Sans, sans-serif"
BG = '''<radialGradient id="bg{i}" cx="{cx}" cy="{cy}" r="95%"><stop offset="0" stop-color="#2a0810"/><stop offset="0.5" stop-color="#110709"/><stop offset="1" stop-color="#070507"/></radialGradient>'''

def glow(i, cx, cy, r):
    return (f'<radialGradient id="g{i}" cx="{cx}" cy="{cy}" r="{r}" gradientUnits="userSpaceOnUse">'
            '<stop offset="0" stop-color="#ffd6da"/><stop offset="0.52" stop-color="#ff2b42"/><stop offset="0.62" stop-color="#b00a20" stop-opacity="0.7"/>'
            '<stop offset="0.82" stop-color="#7e0a1b" stop-opacity="0.19"/><stop offset="1" stop-color="#7e0a1b" stop-opacity="0"/></radialGradient>')

def eclipse(i, cx, cy, r, rim="#ffffff"):
    return (f'<defs>{glow(i, cx, cy, r)}</defs><circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#g{i})"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r*0.54:.1f}" fill="#050304" stroke="{rim}" stroke-width="{max(1.5, r*0.03):.1f}"/>')

def c_eclipse(i, cx, cy, r):
    """Finsternis als offenes C: voller Lichtkranz, der weisse Rand ist rechts offen."""
    rr = r * 0.54
    a0, a1 = math.radians(42), math.radians(318)
    x0, y0 = cx + rr * math.cos(a0), cy + rr * math.sin(a0)
    x1, y1 = cx + rr * math.cos(a1), cy + rr * math.sin(a1)
    return (f'<defs>{glow(i, cx, cy, r)}</defs>'
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#g{i})"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{rr:.1f}" fill="#050304"/>'
            f'<path d="M{x0:.1f} {y0:.1f} A{rr:.1f} {rr:.1f} 0 1 1 {x1:.1f} {y1:.1f}" fill="none" stroke="#fff" stroke-width="{max(1.5, r*0.04):.1f}" stroke-linecap="round"/>')

def word(x, y, size, anchor="start", spacing=None, iptv=True, line=True, width=None):
    sp = spacing if spacing is not None else size * 0.13
    out = f'<text x="{x}" y="{y}" font-family="{FONT}" font-weight="700" font-size="{size}" fill="#f7f1ee" letter-spacing="{sp:.1f}" text-anchor="{anchor}">ECLIPSE</text>'
    if line:
        w = width or size * 4.1
        lx = x - w / 2 if anchor == "middle" else x
        out += f'<rect x="{lx:.1f}" y="{y + size*0.28:.1f}" width="{w:.1f}" height="{max(1.2, size*0.04):.1f}" fill="#dd1a34"/>'
    if iptv:
        out += f'<text x="{x}" y="{y + size*0.85:.1f}" font-family="{FONT}" font-weight="600" font-size="{size*0.4:.1f}" fill="#ff5c6c" letter-spacing="{size*0.24:.1f}" text-anchor="{anchor}">IPTV</text>'
    return out

def icon(i, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="192" height="192" viewBox="0 0 192 192"><defs>{BG.format(i=i, cx="50%", cy="30%")}'
            f'<clipPath id="c{i}"><rect width="192" height="192" rx="40"/></clipPath></defs><g clip-path="url(#c{i})"><rect width="192" height="192" fill="url(#bg{i})"/>{body}</g></svg>')

def banner(i, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360"><defs>{BG.format(i=i, cx="28%", cy="46%")}</defs>'
            f'<rect width="640" height="360" fill="url(#bg{i})"/>{body}</svg>')

V = {}
# 1 Schlicht: Finsternis ueber dem Schriftzug
V["1-schlicht"] = (
    icon(1, eclipse("1a", 96, 74, 60) + word(96, 150, 24, "middle", spacing=3.5, line=False) .replace('y="170.4"', 'y="170"')),
    banner(1, eclipse("1b", 170, 180, 132) + word(330, 186, 58, spacing=8, width=232)))
# 2 C als Finsternis: im Schriftzug ersetzt die Finsternis das C
def c_word(x, y, size):
    sp = size * 0.13
    rr = size * 0.37
    e = f'<text x="{x}" y="{y}" font-family="{FONT}" font-weight="700" font-size="{size}" fill="#f7f1ee" letter-spacing="{sp:.1f}">E</text>'
    cx = x + size * 0.60 + sp + rr
    ecl = c_eclipse("c%d" % size, cx, y - size * 0.36, rr / 0.54)
    rest = f'<text x="{cx + rr + sp*1.4:.1f}" y="{y}" font-family="{FONT}" font-weight="700" font-size="{size}" fill="#f7f1ee" letter-spacing="{sp:.1f}">LIPSE</text>'
    return e + ecl + rest
V["2-c-finsternis"] = (
    icon(2, c_eclipse("2a", 92, 84, 78) + f'<text x="96" y="168" font-family="{FONT}" font-weight="600" font-size="15" fill="#ff5c6c" letter-spacing="7" text-anchor="middle">IPTV</text>'),
    banner(2, c_word(118, 196, 78) + f'<rect x="120" y="222" width="400" height="2.5" fill="#dd1a34"/>'
           f'<text x="320" y="262" font-family="{FONT}" font-weight="600" font-size="24" fill="#ff5c6c" letter-spacing="16" text-anchor="middle">IPTV</text>'))
# 3 Plakette: Schrift im Kreis um die Finsternis
def ring_text(cx, cy, rr, text, center_deg, size, color, weight, step_deg):
    """Setzt Buchstaben einzeln auf einen Kreis (oben: Lesrichtung im Uhrzeigersinn, unten: gegen den Uhrzeigersinn)."""
    out = []
    n = len(text)
    bottom = 0 < (center_deg % 360) < 180
    for k, ch in enumerate(text):
        off = (k - (n - 1) / 2) * step_deg
        deg = center_deg - off if bottom else center_deg + off
        a = math.radians(deg)
        x, y = cx + rr * math.cos(a), cy + rr * math.sin(a)
        rot = deg - 90 if bottom else deg + 90
        out.append(f'<text x="{x:.1f}" y="{y:.1f}" transform="rotate({rot:.1f} {x:.1f} {y:.1f})" font-family="{FONT}" font-weight="{weight}" '
                   f'font-size="{size}" fill="{color}" text-anchor="middle" dominant-baseline="central">{ch}</text>')
    return "".join(out)

def badge(i, cx, cy, r, size):
    rr = r * 0.80
    return (eclipse(i, cx, cy, r * 0.66)
            + f'<circle cx="{cx}" cy="{cy}" r="{r*0.97:.1f}" fill="none" stroke="#dd1a34" stroke-opacity="0.6" stroke-width="{max(1, r*0.014):.1f}"/>'
            + ring_text(cx, cy, rr, "ECLIPSE", 270, size, "#f7f1ee", 700, 15.5)
            + ring_text(cx, cy, rr, "IPTV", 90, size * 0.8, "#ff5c6c", 600, 15))

V["3-plakette"] = (
    icon(3, badge("3a", 96, 96, 86, 15)),
    banner(3, badge("3b", 180, 180, 150, 22) + word(370, 186, 50, spacing=7, width=200)))
# 4 Aufgang: Finsternis geht ueber einem Horizont auf
def rise(i, cx, horizon, r, w):
    return (f'<defs><linearGradient id="h{i}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#dd1a34" stop-opacity="0"/>'
            f'<stop offset="0.5" stop-color="#ff5c6c"/><stop offset="1" stop-color="#dd1a34" stop-opacity="0"/></linearGradient>'
            f'<clipPath id="k{i}"><rect x="0" y="0" width="{w}" height="{horizon}"/></clipPath></defs>'
            f'<g clip-path="url(#k{i})">{eclipse(i, cx, horizon + r*0.12, r)}</g>'
            f'<rect x="0" y="{horizon - 1}" width="{w}" height="2.2" fill="url(#h{i})"/>')
V["4-aufgang"] = (
    icon(4, rise("4a", 96, 101, 82, 192) + word(96, 146, 22, "middle", spacing=3.2, line=False)),
    banner(4, rise("4b", 320, 200, 150, 640) + word(320, 270, 44, "middle", spacing=7, line=False)))

out = sys.argv[1] if len(sys.argv) > 1 else "."
for k, (ic, ba) in V.items():
    open(os.path.join(out, f"logo-{k}-icon.svg"), "w").write(ic)
    open(os.path.join(out, f"logo-{k}-banner.svg"), "w").write(ba)
print("ok", list(V))

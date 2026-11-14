"""Wolkenform fuer Logo, Banner und App. Dieselben Zahlen stehen in ui/Clouds.java.

Aufbau: Kreise ("Baeusche") auf einem abgerundeten Sockel, alles in Einheiten der
Wolkenhoehe. Die Form passt in 2.2 x 1.0; innen deuten kurze Boegen die Baeusche an.
Die Boegen sind so gewaehlt, dass sie sich nicht beruehren.
"""
import math

W = 2.2          # Breite im Verhaeltnis zur Hoehe
PAD = 0.06       # Rand fuer die Kontur, damit nichts abgeschnitten wird
LOBES = [(0.40, 0.66, 0.30), (0.80, 0.42, 0.36), (1.24, 0.34, 0.30), (1.62, 0.52, 0.30), (1.92, 0.72, 0.22), (1.06, 0.62, 0.30)]
BASE = (0.12, 0.60, 2.10, 0.96, 0.18)
# Innenlinien: (cx, cy, r, start, sweep) - Kreisboegen, Winkel im Uhrzeigersinn ab 3 Uhr
ARCS = [(0.74, 0.80, 0.22, 200, 120), (1.22, 0.74, 0.24, 205, 115), (1.68, 0.82, 0.18, 205, 115)]
# Wirbel unten links: (cx, cy, r)
CURL = (0.36, 0.79, 0.11)

def spiral(cx, cy, r0, turns=0.85, steps=40):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        a = -t * turns * 2 * math.pi + math.pi * 0.5
        r = r0 * (1 - 0.7 * t)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts

def arc_pts(cx, cy, r, a0, sweep, steps=24):
    return [(cx + r * math.cos(math.radians(a0 + sweep * k / steps)), cy + r * math.sin(math.radians(a0 + sweep * k / steps))) for k in range(steps + 1)]

def cloud(x, y, h, fill="#d0142f", deep="#86091c", stroke="#ffffff", ident="c", sw=None, line=True):
    sw = sw or max(1.2, h * 0.035)
    S = lambda v: v * h
    out = [f'<linearGradient id="{ident}g" gradientUnits="userSpaceOnUse" x1="0" y1="{y:.1f}" x2="0" y2="{y+S(1):.1f}">'
           f'<stop offset="0" stop-color="{fill}"/><stop offset="1" stop-color="{deep}"/></linearGradient>']
    shapes = [f'<circle cx="{x+S(cx):.1f}" cy="{y+S(cy):.1f}" r="{S(r):.1f}"/>' for cx, cy, r in LOBES]
    x0, y0, x1, y1, rr = BASE
    shapes.append(f'<rect x="{x+S(x0):.1f}" y="{y+S(y0):.1f}" width="{S(x1-x0):.1f}" height="{S(y1-y0):.1f}" rx="{S(rr):.1f}"/>')
    out.append(f'<g fill="none" stroke="{stroke}" stroke-width="{2*sw:.1f}" stroke-linejoin="round">' + "".join(shapes) + '</g>')
    out.append(f'<g fill="url(#{ident}g)">' + "".join(shapes) + '</g>')
    if line:
        paths = [arc_pts(*a) for a in ARCS] + [spiral(*CURL)]
        for pts in paths:
            d = "M" + " L".join(f"{x+S(px):.1f} {y+S(py):.1f}" for px, py in pts)
            out.append(f'<path d="{d}" fill="none" stroke="{stroke}" stroke-width="{sw*0.75:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.9"/>')
    return "\n".join(out)

if __name__ == "__main__":
    S = "/tmp/claude-1000/-home-bradonja/bd7d9dfb-ea02-4b44-8028-5691b9e43778/scratchpad/"
    # Rahmen zeigt die Flaeche 2.2 x 1.0 (plus Rand): nichts darf darueber hinausragen
    def box(x, y, h):
        return f'<rect x="{x:.1f}" y="{y:.1f}" width="{W*h:.1f}" height="{h:.1f}" fill="none" stroke="#3a6" stroke-width="1" stroke-dasharray="4 4"/>'
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="900" height="420"><rect width="900" height="420" fill="#070507"/>'
           f'{box(60,60,170)}{cloud(60,60,170)}{box(520,90,110)}{cloud(520,90,110,ident="b")}{cloud(540,280,56,ident="s")}</svg>')
    open(S + "cloud.svg", "w").write(svg)

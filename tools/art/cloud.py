"""Wolkenform fuer Logo, Banner und App. Dieselben Zahlen stehen in ui/Clouds.java."""
import math
# Kreise (x, y, r) in Einheiten der Wolkenhoehe, dazu ein abgerundeter Sockel
LOBES = [(0.34, 0.64, 0.30), (0.72, 0.42, 0.38), (1.16, 0.32, 0.32), (1.55, 0.50, 0.32), (1.86, 0.70, 0.24), (1.0, 0.62, 0.30)]
BASE = (0.06, 0.58, 2.06, 0.98, 0.20)
W = 2.12

def spiral(cx, cy, r0, turns, start, direction=1, steps=40):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        a = math.radians(start) + direction * t * turns * 2 * math.pi
        r = r0 * (1 - 0.72 * t)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts

# Innenlinien: untere Kanten der oberen Wolkenbaeusche (Kreis, Startwinkel, Winkel), Winkel im Uhrzeigersinn ab 3 Uhr
ARCS = [((0.72, 0.42, 0.38), 40, 105), ((1.16, 0.32, 0.32), 50, 95), ((1.55, 0.50, 0.32), 40, 100)]
CURL = (0.40, 0.76, 0.11)

def inner_paths():
    out = []
    for (cx, cy, r), a0, sweep in ARCS:
        pts = [(cx + r * math.cos(math.radians(a0 + sweep * k / 24)), cy + r * math.sin(math.radians(a0 + sweep * k / 24))) for k in range(25)]
        out.append(pts)
    cx, cy, r = CURL
    out.append(spiral(cx, cy, r, 0.8, 0, -1))
    return out

def cloud(x, y, h, fill="#d0142f", deep="#86091c", stroke="#ffffff", ident="c", sw=None, line=True):
    sw = sw or max(1.2, h * 0.04)
    S = lambda v: v * h
    out = [f'<linearGradient id="{ident}g" gradientUnits="userSpaceOnUse" x1="0" y1="{y:.1f}" x2="0" y2="{y+S(1):.1f}">'
           f'<stop offset="0" stop-color="{fill}"/><stop offset="1" stop-color="{deep}"/></linearGradient>']
    shapes = [f'<circle cx="{x+S(cx):.1f}" cy="{y+S(cy):.1f}" r="{S(r):.1f}"/>' for cx, cy, r in LOBES]
    x0, y0, x1, y1, rr = BASE
    shapes.append(f'<rect x="{x+S(x0):.1f}" y="{y+S(y0):.1f}" width="{S(x1-x0):.1f}" height="{S(y1-y0):.1f}" rx="{S(rr):.1f}"/>')
    out.append(f'<g fill="none" stroke="{stroke}" stroke-width="{2*sw:.1f}">' + "".join(shapes) + '</g>')
    out.append(f'<g fill="url(#{ident}g)">' + "".join(shapes) + '</g>')
    if line:
        for pts in inner_paths():
            d = "M" + " L".join(f"{x+S(px):.1f} {y+S(py):.1f}" for px, py in pts)
            out.append(f'<path d="{d}" fill="none" stroke="{stroke}" stroke-width="{sw*0.8:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.92"/>')
    return "\n".join(out)

if __name__ == "__main__":
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="900" height="420"><rect width="900" height="420" fill="#070507"/>{cloud(60,60,150)}{cloud(480,120,110,ident="b")}{cloud(520,300,60,ident="s")}</svg>'
    open("/tmp/claude-1000/-home-bradonja/bd7d9dfb-ea02-4b44-8028-5691b9e43778/scratchpad/cloud.svg", "w").write(svg)

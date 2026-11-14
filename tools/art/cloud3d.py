"""Plastische Wolke (SVG) fuer Logo und Banner. Gleiche Form und Beleuchtung wie ui/Clouds.java.

Aufbau von hinten nach vorn: weicher Schatten, weisse Kontur, Sockel, Baeusche als
beleuchtete Kugeln (Licht von oben links), feine dunkle Trennlinien zwischen den Baeuschen.
"""
W = 2.2
# Baeusche (x, y, r), von hinten nach vorn gezeichnet
LOBES = [(1.18, 0.30, 0.25), (0.86, 0.36, 0.26), (1.52, 0.44, 0.24), (0.55, 0.52, 0.24), (1.20, 0.56, 0.26), (1.84, 0.62, 0.20), (0.30, 0.70, 0.20), (0.88, 0.68, 0.24), (1.56, 0.70, 0.22)]
BASE = (0.12, 0.64, 2.06, 0.96, 0.16)
SQUASH = 0.86   # Baeusche etwas flacher als Kreise

def cloud(x, y, h, ident="c", shadow=True, outline=True, opacity=1.0):
    S = lambda v: v * h
    sw = max(1.2, h * 0.032)
    x0, y0, x1, y1, rr = BASE
    circles = [f'<ellipse cx="{x+S(cx):.1f}" cy="{y+S(cy):.1f}" rx="{S(r*1.18):.1f}" ry="{S(r*SQUASH*1.18):.1f}"/>' for cx, cy, r in LOBES]
    base = f'<rect x="{x+S(x0):.1f}" y="{y+S(y0):.1f}" width="{S(x1-x0):.1f}" height="{S(y1-y0):.1f}" rx="{S(rr):.1f}"/>'
    union = "".join(circles) + base
    o = [f'<g opacity="{opacity}">', '<defs>',
         f'<clipPath id="{ident}clip">{union}</clipPath>',
         f'<filter id="{ident}blur" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="{S(0.07):.1f}"/></filter>',
         f'<filter id="{ident}soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="{S(0.045):.1f}"/></filter>',
         f'<linearGradient id="{ident}base" gradientUnits="userSpaceOnUse" x1="0" y1="{y+S(0.5):.1f}" x2="0" y2="{y+S(1):.1f}">'
         '<stop offset="0" stop-color="#a50f26"/><stop offset="1" stop-color="#4f0511"/></linearGradient>',
         f'<radialGradient id="{ident}lobe" cx="0.40" cy="0.32" r="0.80">'
         '<stop offset="0" stop-color="#ee3049"/><stop offset="0.55" stop-color="#cc1730"/><stop offset="0.9" stop-color="#a00f25"/>'
         '<stop offset="1" stop-color="#8c0c20"/></radialGradient>',
         f'<linearGradient id="{ident}ao" gradientUnits="userSpaceOnUse" x1="0" y1="{y+S(0.5):.1f}" x2="0" y2="{y+S(1):.1f}">'
         '<stop offset="0" stop-color="#140004" stop-opacity="0"/><stop offset="1" stop-color="#140004" stop-opacity="0.6"/></linearGradient>',
         f'<linearGradient id="{ident}sheen" gradientUnits="userSpaceOnUse" x1="0" y1="{y:.1f}" x2="0" y2="{y+S(0.55):.1f}">'
         '<stop offset="0" stop-color="#ffffff" stop-opacity="0.22"/><stop offset="1" stop-color="#ffffff" stop-opacity="0"/></linearGradient>',
         '</defs>']
    if shadow:
        o.append(f'<g fill="#000" opacity="0.6" filter="url(#{ident}blur)" transform="translate(0 {S(0.10):.1f})">{union}</g>')
    if outline:
        o.append(f'<g fill="none" stroke="#fff" stroke-width="{2*sw:.1f}">{union}</g>')
    o.append(f'<g clip-path="url(#{ident}clip)">')
    o.append(f'<g fill="url(#{ident}base)">{base}</g>')
    for i, (cx, cy, r) in enumerate(LOBES):
        if i > 0:
            # weicher Schatten, den dieser Bausch auf die dahinterliegenden wirft
            o.append(f'<ellipse cx="{x+S(cx+0.02):.1f}" cy="{y+S(cy+0.05):.1f}" rx="{S(r*1.2):.1f}" ry="{S(r*SQUASH*1.2):.1f}" fill="#2a0008" opacity="0.45" filter="url(#{ident}soft)"/>')
        o.append(f'<ellipse cx="{x+S(cx):.1f}" cy="{y+S(cy):.1f}" rx="{S(r*1.18):.1f}" ry="{S(r*SQUASH*1.18):.1f}" fill="url(#{ident}lobe)"/>')
    o.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{S(W):.1f}" height="{S(1):.1f}" fill="url(#{ident}ao)"/>')
    o.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{S(W):.1f}" height="{S(1):.1f}" fill="url(#{ident}sheen)"/>')
    o.append('</g></g>')
    return "\n".join(o)

if __name__ == "__main__":
    S = "/tmp/claude-1000/-home-bradonja/bd7d9dfb-ea02-4b44-8028-5691b9e43778/scratchpad/c3d/"
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="900" height="420"><rect width="900" height="420" fill="#0a0507"/>'
           f'{cloud(40,60,180,"a")}{cloud(520,110,110,"b")}{cloud(560,300,56,"s")}</svg>')
    open(S + "c.svg", "w").write(svg)

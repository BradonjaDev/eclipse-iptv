"""Erzeugt icon.svg und banner.svg. Aufruf: python3 gen.py (danach mit rsvg-convert in PNG wandeln, siehe README)."""
import base64, os
from cloud3d import cloud, W

# Rauch-Hintergrund aus der App (data/Nebula.java), erzeugt mit tools/NebulaPreview
_neb = os.path.join(os.path.dirname(os.path.abspath(__file__)), "nebula.png")
NEB = "data:image/png;base64," + base64.b64encode(open(_neb, "rb").read()).decode() if os.path.exists(_neb) else None

def eclipse(cx, cy, r, ident):
    return f'''<radialGradient id="{ident}c" cx="{cx}" cy="{cy}" r="{r}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffd6da"/><stop offset="0.52" stop-color="#ff2b42"/><stop offset="0.62" stop-color="#b00a20" stop-opacity="0.7"/>
      <stop offset="0.82" stop-color="#7e0a1b" stop-opacity="0.19"/><stop offset="1" stop-color="#7e0a1b" stop-opacity="0"/></radialGradient>
    <circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#{ident}c)"/>
    <circle cx="{cx}" cy="{cy}" r="{r*0.54}" fill="#050304" stroke="#ffffff" stroke-width="{max(1.5, r*0.03)}"/>'''

BG = '''<radialGradient id="bg" cx="{cx}" cy="{cy}" r="95%"><stop offset="0" stop-color="#3a0812"/><stop offset="0.42" stop-color="#12080b"/><stop offset="1" stop-color="#070507"/></radialGradient>'''

icon = f'''<svg xmlns="http://www.w3.org/2000/svg" width="192" height="192" viewBox="0 0 192 192">
  <defs>{BG.format(cx="70%", cy="25%")}<clipPath id="clip"><rect width="192" height="192" rx="40"/></clipPath></defs>
  <g clip-path="url(#clip)">
    <rect width="192" height="192" fill="url(#bg)"/>
    {f'<image href="{NEB}" x="-150" y="-10" width="384" height="216" preserveAspectRatio="none"/>' if NEB else ''}
    {cloud(112, 22, 28, ident="ia", opacity=0.9)}
    {eclipse(96, 86, 80, "ie")}
    {cloud(12, 116, 46, ident="ib")}
  </g>
</svg>'''
open("icon.svg", "w").write(icon)

banner = f'''<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">
  <defs>{BG.format(cx="27%", cy="46%")}</defs>
  <rect width="640" height="360" fill="url(#bg)"/>
  {f'<image href="{NEB}" x="0" y="0" width="640" height="360" preserveAspectRatio="none"/>' if NEB else ''}
  {cloud(462, 22, 40, ident="ba", opacity=0.85)}
  {cloud(530, 284, 36, ident="bb", opacity=0.75)}
  {eclipse(172, 166, 136, "be")}
  {cloud(44, 204, 78, ident="bc")}
  <text x="330" y="186" font-family="Noto Sans, Liberation Sans, sans-serif" font-weight="700" font-size="56" fill="#f7f1ee" letter-spacing="8">ECLIPSE</text>
  <rect x="332" y="204" width="232" height="2.5" fill="#dd1a34"/>
  <text x="333" y="242" font-family="Noto Sans, Liberation Sans, sans-serif" font-weight="600" font-size="24" fill="#ff5c6c" letter-spacing="14">IPTV</text>
</svg>'''
open("banner.svg", "w").write(banner)

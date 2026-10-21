import sys, os
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

FONT, OUT = sys.argv[1], sys.argv[2]
f = instantiateVariableFont(TTFont(FONT), {"wght": 500})
gs = f.getGlyphSet(); cmap = f.getBestCmap(); upm = f["head"].unitsPerEm

def text_path(s, size, x, baseline, tracking=0.0):
    """Return SVG path d for string s, left at x, baseline y (y down)."""
    sc = size / upm
    pen = SVGPathPen(gs, ntos=lambda v: ("%.2f" % v).rstrip("0").rstrip("."))
    bp = BoundsPen(gs)
    cx = 0
    for ch in s:
        g = cmap[ord(ch)]
        t = (sc, 0, 0, -sc, x + cx * sc, baseline)
        gs[g].draw(TransformPen(pen, t)); gs[g].draw(TransformPen(bp, t))
        cx += gs[g].width + tracking * upm
    return pen.getCommands(), bp.bounds

# icon: 240 space; wordmark size 19 (0.4.3: más grande), centrado, baseline 200; el dibujo sube 10.
WS, WB, ART_DY = 19, 200, -10
d0, b0 = text_path("airmix", WS, 0, WB, tracking=1.5/13)
w = b0[2] - b0[0]
word_icon, _ = text_path("airmix", WS, 120 - w/2 - b0[0], WB, tracking=1.5/13)

CURVE = "M36 116 C 64 116, 72 66, 100 66 C 128 66, 132 146, 156 146 C 176 146, 184 116, 204 116"
ORANGE, DARK, WHITE, LINE, DIM = "#FF7A45", "#12161B", "#E8EDF2", "#2E3743", "#8A96A3"

def art(tx="", word=True):
    s = f'<g{tx}><g transform="translate(0,{ART_DY})">'
    s += f'<path d="{CURVE} Z" fill="{ORANGE}" fill-opacity="0.28"/>'
    s += f'<path d="M40 116 L200 116" stroke="{LINE}" stroke-width="3"/>'
    s += f'<path d="{CURVE}" fill="none" stroke="{ORANGE}" stroke-width="7" stroke-linecap="round"/>'
    s += f'<circle cx="100" cy="66" r="11" fill="{WHITE}"/><circle cx="156" cy="146" r="11" fill="{WHITE}"/></g>'
    if word: s += f'<path d="{word_icon}" fill="{DIM}"/>'
    return s + '</g>'

# iOS 1024 opaque square (iOS rounds corners itself)
open(f"{OUT}/icon-1024.svg","w").write(
  f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="1024" height="1024"><rect width="240" height="240" fill="{DARK}"/>{art()}</svg>')

# preview with rounded tile (for the user)
open(f"{OUT}/icon-preview.svg","w").write(
  f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="512" height="512"><rect width="240" height="240" rx="54" fill="{DARK}"/>{art()}</svg>')

# Android adaptive foreground: 240-space mapped to the 72dp visible area of a 108dp canvas
def circle_path(cx, cy, r):
    return f"M{cx-r},{cy} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0 Z"
def avd_paths(word=True, mono=False):
    o = ORANGE if not mono else "#000000"
    w_ = WHITE if not mono else "#000000"
    p = [f'<group android:translateY="{ART_DY}">']
    p.append(f'<path android:fillColor="{o}" android:fillAlpha="{0.28 if not mono else 0.4}" android:pathData="{CURVE} Z"/>')
    if not mono: p.append(f'<path android:strokeColor="{LINE}" android:strokeWidth="3" android:pathData="M40,116 L200,116"/>')
    p.append(f'<path android:strokeColor="{o}" android:strokeWidth="7" android:strokeLineCap="round" android:pathData="{CURVE}"/>')
    p.append(f'<path android:fillColor="{w_}" android:pathData="{circle_path(100,66,11)} {circle_path(156,146,11)}"/>')
    p.append('</group>')
    if word and not mono: p.append(f'<path android:fillColor="{DIM}" android:pathData="{word_icon}"/>')
    return "\n        ".join(p)

def fg(mono=False):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/logo/gen.py: logo 8C (curva EQ con banda llena). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <group android:translateX="18" android:translateY="18" android:scaleX="0.3" android:scaleY="0.3">
        {avd_paths(word=not mono, mono=mono)}
    </group>
</vector>
'''
open(f"{OUT}/ic_launcher_foreground.xml","w").write(fg())
open(f"{OUT}/ic_launcher_monochrome.xml","w").write(fg(mono=True))

# In-app lockup: mark (no baseline, no word) + "airmix" wordmark
# mark bounds in 240-space: x 29..211? curve ends round-capped at 36-3.5 .. 204+3.5; y 55..157
mx0, my0, mx1, my1 = 32.5, 55, 207.5, 157
size = 58
_, bb = text_path("airmix", size, 0, 0, tracking=0.04)
tw, th = bb[2]-bb[0], bb[3]-bb[1]
gap = 26
tx = mx1 + gap - bb[0]
base = (my0+my1)/2 - (bb[1]+bb[3])/2
word_lock, wb = text_path("airmix", size, tx, base, tracking=0.04)
vx0, vy0, vx1, vy1 = mx0, my0, wb[2], my1
VW, VH = vx1-vx0, vy1-vy0
mark = (f'<path d="{CURVE} Z" fill="{ORANGE}" fill-opacity="0.28"/>'
        f'<path d="{CURVE}" fill="none" stroke="{ORANGE}" stroke-width="7" stroke-linecap="round"/>'
        f'<circle cx="100" cy="66" r="11" fill="{WHITE}"/><circle cx="156" cy="146" r="11" fill="{WHITE}"/>')
open(f"{OUT}/logo.svg","w").write(
  f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vx0:.2f} {vy0:.2f} {VW:.2f} {VH:.2f}" width="{VW:.0f}" height="{VH:.0f}">{mark}<path d="{word_lock}" fill="{WHITE}"/></svg>')
H = 26.0; W = H * VW / VH
open(f"{OUT}/logo_lockup.xml","w").write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por tools/logo/gen.py: logo de la barra superior (marca + "airmix"). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{W:.1f}dp" android:height="{H:.0f}dp"
    android:viewportWidth="{VW:.2f}" android:viewportHeight="{VH:.2f}">
    <group android:translateX="{-vx0:.2f}" android:translateY="{-vy0:.2f}">
        <path android:fillColor="{ORANGE}" android:fillAlpha="0.28" android:pathData="{CURVE} Z"/>
        <path android:strokeColor="{ORANGE}" android:strokeWidth="7" android:strokeLineCap="round" android:pathData="{CURVE}"/>
        <path android:fillColor="{WHITE}" android:pathData="{circle_path(100,66,11)} {circle_path(156,146,11)}"/>
        <path android:fillColor="{WHITE}" android:pathData="{word_lock}"/>
    </group>
</vector>
''')
print("lockup", VW, VH, W)

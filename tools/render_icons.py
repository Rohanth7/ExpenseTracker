"""Renders the launcher PNGs (legacy and themed) from the generated ic_launcher_foreground.xml.
Needs skia-python (pip install skia-python); it is a design tool, not part of the build.
usage, from the repo root: python3 tools/render_icons.py app/src/main/res"""
import sys, xml.etree.ElementTree as ET
import skia

RES = sys.argv[1]
A = "{http://schemas.android.com/apk/res/android}"
BG = "#1A1F52"
CLAY, RIM, COIN, COIN_EDGE, FACE_INK = "#CF6E44", "#9E472A", "#F4BC42", "#C98A1B", "#4A1E0E"

def svg_of(keep=lambda p: True, recolor=None):
    root = ET.parse(f"{RES}/drawable/ic_launcher_foreground.xml").getroot()
    def walk(node):
        out = []
        for ch in node:
            if ch.tag == "group":
                tx, ty = ch.get(A + "translateX", "0"), ch.get(A + "translateY", "0")
                sx, sy = ch.get(A + "scaleX", "1"), ch.get(A + "scaleY", "1")
                out.append(f'<g transform="translate({tx},{ty}) scale({sx},{sy})">{walk(ch)}</g>')
            elif ch.tag == "path" and keep(ch):
                attrs = [f'd="{ch.get(A + "pathData")}"']
                fill, stroke = ch.get(A + "fillColor"), ch.get(A + "strokeColor")
                def col(c):
                    if recolor: return recolor, "1"
                    if len(c) == 9: return "#" + c[3:], str(int(c[1:3], 16) / 255)
                    return c, "1"
                if fill:
                    c, o = col(fill)
                    o = float(o) * float(ch.get(A + "fillAlpha", "1"))
                    attrs.append(f'fill="{c}" fill-opacity="{o}"')
                else:
                    attrs.append('fill="none"')
                if stroke:
                    c, _ = col(stroke)
                    attrs.append(f'stroke="{c}" stroke-width="{ch.get(A + "strokeWidth")}" stroke-linecap="round" stroke-linejoin="round"')
                out.append(f"<path {' '.join(attrs)}/>")
        return "".join(out)
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">{walk(root)}</svg>'

def render(svg, px, canvas=None, surface=None):
    surface = surface or skia.Surface(px, px)
    dom = skia.SVGDOM.MakeFromStream(skia.MemoryStream(svg.encode(), True))
    dom.setContainerSize(skia.Size(px, px))
    with surface as c:
        c.save(); c.scale(px / 108, px / 108); dom.render(c); c.restore()
    return surface

def color_of(path):
    c = path.get(A + "fillColor") or path.get(A + "strokeColor")
    return "#" + c[-6:].upper()

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
full = svg_of()
# Body, rim and coin; the sparkles share the coin's colour but are strokes, so they're left out.
silhouette = svg_of(lambda p: (p.get(A + "fillColor") and color_of(p) in (CLAY, RIM, COIN)) or color_of(p) == COIN_EDGE, recolor="#FFFFFF")
face = svg_of(lambda p: color_of(p) == FACE_INK, recolor="#FFFFFF")

for name, k in DENSITIES.items():
    # Themed icon layer: one colour, her face cut out of it, no sparkles.
    px = round(108 * k)
    mono = render(silhouette, px)
    face_img = render(face, px).makeImageSnapshot()
    with mono as c:
        c.drawImage(face_img, 0, 0, skia.SamplingOptions(), skia.Paint(BlendMode=skia.BlendMode.kDstOut))
    mono.makeImageSnapshot().save(f"{RES}/mipmap-{name}/ic_launcher_monochrome.png", skia.kPNG)

    # Android 7 has no adaptive icons: the same layers, cropped to the 72dp a launcher shows.
    size = round(48 * k)
    for legacy, round_shape in (("ic_launcher", False), ("ic_launcher_round", True)):
        layer = skia.Surface(round(108 * size / 72), round(108 * size / 72))
        with layer as c: c.clear(skia.Color4f.FromColor(skia.ColorSetRGB(0x1A, 0x1F, 0x52)))
        render(full, layer.width(), surface=layer)
        out = skia.Surface(size, size)
        with out as c:
            c.clear(skia.ColorTRANSPARENT)
            rect = skia.Rect.MakeWH(size, size)
            if round_shape: c.clipRRect(skia.RRect.MakeOval(rect), doAntiAlias=True)
            else: c.clipRRect(skia.RRect.MakeRectXY(rect, size * .215, size * .215), doAntiAlias=True)
            off = (layer.width() - size) / 2
            c.drawImage(layer.makeImageSnapshot(), -off, -off, skia.SamplingOptions(skia.FilterMode.kLinear))
        out.makeImageSnapshot().save(f"{RES}/mipmap-{name}/{legacy}.png", skia.kPNG)
print("ok")

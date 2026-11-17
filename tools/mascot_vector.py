"""Writes her Happy pot as an Android vector drawable from the path data in Mascot.kt.
usage, from the repo root:
  python3 tools/mascot_vector.py app/src/main/java/com/example/expensetracker/ui/mascot/Mascot.kt app/src/main/res/drawable/mascot_happy.xml
  python3 tools/mascot_vector.py app/src/main/java/com/example/expensetracker/ui/mascot/Mascot.kt app/src/main/res/drawable/ic_launcher_foreground.xml icon
"icon" makes a 108dp adaptive-icon foreground with her inside the 66dp safe circle."""
import re, sys
src = open(sys.argv[1]).read()

def svg(name):
    return re.search(rf'private val {name} = svg\("([^"]+)"\)', src).group(1)

happy_face = re.search(r'MascotMood\.Happy to svg\("([^"]+)"\)', src).group(1)
def color(name):
    return "#" + re.search(rf'private val {name} = Color\(0x([0-9A-F]{{8}})\)', src).group(1)

def n(v):
    return f"{round(v, 4):g}"

def ellipse(cx, cy, rx, ry):
    return f"M{n(cx - rx)},{n(cy)} a{n(rx)},{n(ry)} 0 1,0 {n(2 * rx)},0 a{n(rx)},{n(ry)} 0 1,0 {n(-2 * rx)},0 z"

def round_rect(x, y, w, h, r):
    return (f"M{n(x + r)},{n(y)} h{n(w - 2 * r)} a{n(r)},{n(r)} 0 0,1 {n(r)},{n(r)} v{n(h - 2 * r)} a{n(r)},{n(r)} 0 0,1 {n(-r)},{n(r)} "
            f"h{n(-(w - 2 * r))} a{n(r)},{n(r)} 0 0,1 {n(-r)},{n(-r)} v{n(-(h - 2 * r))} a{n(r)},{n(r)} 0 0,1 {n(r)},{n(-r)} z")

def fill(d, c, alpha=None):
    a = f'\n        android:fillAlpha="{alpha}"' if alpha else ""
    return f'    <path\n        android:fillColor="{c}"{a}\n        android:pathData="{d}" />'

def stroke(d, c, w):
    return (f'    <path\n        android:pathData="{d}"\n        android:strokeColor="{c}"\n        android:strokeWidth="{w}"\n'
            f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round" />')

# The shapes and numbers drawMascot() and face() in Mascot.kt use for Happy, in their grid units.
dots = re.search(r'for \(\(x, y\) in listOf\((.*?)\)\) drawCircle\(ClayDots', src).group(1)
dot_pts = [tuple(map(float, m)) for m in re.findall(r'([\d.]+)f to ([\d.]+)f', dots)]
parts = [
    fill(ellipse(60, 113, 30, 4), "#000000", ".2"),
    fill(svg("Body"), color("Clay")),
    fill(ellipse(60, 26, 12, 4.5), color("ClayRim")),
    fill(round_rect(50, 44, 20, 4.5, 2.2), color("ClaySlot")),
    fill(" ".join(ellipse(x, y, 2, 2) for x, y in dot_pts), color("ClayDots")),
    fill(ellipse(60, 9, 7, 7), color("Coin")),
    stroke(ellipse(60, 9, 7, 7), color("CoinEdge"), 2),
    stroke(svg("Sparkles"), color("Coin"), 2.5),
    '    <group android:translateY="12">',
    "    " + stroke(happy_face, color("FaceInk"), 3).replace("\n", "\n    "),
    "    " + fill(ellipse(42, 71, 4, 4) + " " + ellipse(78, 71, 4, 4), color("Cheek"), ".55").replace("\n", "\n    "),
    "    </group>",
]
# Grid point (60, 60.7) is the centre of the smallest circle around coin, sparkles, body and
# shadow (radius 60.7). Scaled by .49 that circle is 29.7dp, inside the 33dp safe radius, and she
# takes the share of the icon the mockup gives her (78 of 96).
ICON_SCALE, ICON_CENTRE_Y = .49, 60.7
if len(sys.argv) > 3 and sys.argv[3] == "icon":
    size, viewport = "108dp", ("108", "108")
    group = (f'<group android:scaleX="{ICON_SCALE}" android:scaleY="{ICON_SCALE}" '
             f'android:translateX="{n(54 - 60 * ICON_SCALE)}" android:translateY="{n(54 - ICON_CENTRE_Y * ICON_SCALE)}">')
    width = height = size
else:
    viewport, width, height = ("120", "124"), "120dp", "124dp"
    group = '<group android:translateY="4">'
header = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from the path data in ui/mascot/Mascot.kt (Happy) by tools/mascot_vector.py; regenerate rather than edit. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{width}"
    android:height="{height}"
    android:viewportWidth="{viewport[0]}"
    android:viewportHeight="{viewport[1]}">
{group}
"""
open(sys.argv[2], "w").write(header + "\n".join(parts) + "\n</group>\n</vector>\n")

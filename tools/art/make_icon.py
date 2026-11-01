"""Draws the CrazyTower app icon: a crooked tower with lit windows and a star on top.

python3 tools/art/make_icon.py  ->  game/packaging/crazytower.png (256 px) and crazytower.ico
"""
from pathlib import Path
from PIL import Image, ImageDraw
import math

OUT = Path(__file__).resolve().parents[2] / "game" / "packaging"
S = 1024  # drawn large, scaled down for a smooth result

SKY, INK, PANEL = (43, 58, 103), (18, 21, 42), (30, 34, 54)
TEAL, GOLD, PINK, GROUND = (79, 227, 193), (255, 206, 84), (255, 107, 157), (62, 142, 110)


def star(d, cx, cy, r, color):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=color, outline=INK, width=int(r * 0.12))


img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle((24, 24, S - 24, S - 24), radius=190, fill=SKY)
d.rounded_rectangle((24, S - 230, S - 24, S - 24), radius=190, fill=GROUND)
d.rectangle((24, S - 230, S - 24, S - 160), fill=GROUND)

# floors that lean a little more the higher they go
floors, fh, base_w = 7, 90, 470
for i in range(floors):
    y1 = S - 200 - i * fh
    y0 = y1 - fh + 8
    shift = int(math.sin(i * 1.1) * 16 * i / floors * 2)
    w = base_w - i * 18
    x0 = (S - w) // 2 + shift
    d.rectangle((x0, y0, x0 + w, y1), fill=PANEL, outline=INK, width=10)
    for k in range(4):
        wx = x0 + 34 + k * (w - 68) // 4
        color = [GOLD, TEAL, PINK, GOLD][(i + k) % 4] if (i * 3 + k) % 5 else (60, 66, 98)
        d.rectangle((wx, y0 + 20, wx + (w - 68) // 4 - 22, y1 - 22), fill=color)

star(d, S // 2 + 30, S - 200 - floors * fh - 70, 92, GOLD)

OUT.mkdir(parents=True, exist_ok=True)
small = img.resize((256, 256), Image.LANCZOS)
small.save(OUT / "crazytower.png")
small.save(OUT / "crazytower.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("written", OUT)
